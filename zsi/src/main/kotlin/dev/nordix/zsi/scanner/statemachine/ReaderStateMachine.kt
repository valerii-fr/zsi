@file:Suppress("INLINE_FROM_HIGHER_PLATFORM")
package dev.nordix.zsi.scanner.statemachine

import android.content.Context
import dev.nordix.hardware.domain.AudioService
import dev.nordix.hardware.domain.HardwareProvider
import dev.nordix.zsi.scanner.domain.model.ScanResult
import dev.nordix.zsi.scanner.domain.model.ScannerState
import dev.nordix.zsi.scanner.statemachine.model.ReaderEvent
import dev.nordix.zsi.scanner.statemachine.model.ReaderState
import dev.nordix.zsi.scanner.domain.model.SurfaceState
import dev.nordix.zsi.settings.domain.model.ScannerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import ru.nsk.kstatemachine.state.TransitionStateApi
import ru.nsk.kstatemachine.state.addInitialState
import ru.nsk.kstatemachine.state.onEntry
import ru.nsk.kstatemachine.state.transition
import ru.nsk.kstatemachine.state.transitionOn
import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.statemachine.createStateMachine
import ru.nsk.kstatemachine.statemachine.onTransitionComplete
import timber.log.Timber
import java.time.Instant

class ReaderStateMachine(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: StateFlow<ScannerSettings>,
    private val hardwareProvider: HardwareProvider,
) {

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val machineScope = CoroutineScope(scope.coroutineContext + newSingleThreadContext(TAG))
    private val readerCallbackAggregator = ReaderCallbackAggregator(this)
    private val barcodeReaderHandler = BarcodeReaderHandler(
        context = context,
        settings = settings,
    )
    private lateinit var stateMachine: StateMachine
    private var stateMachineJob: Job? = null
    private val eventFlow = MutableSharedFlow<ReaderEvent>(replay = 1)
    private var mode: ScannerState.Mode = ScannerState.Mode.Manual

    val readerState: StateFlow<ScannerState>
        field = MutableStateFlow(ScannerState.INITIAL)

    val surfaceState: StateFlow<SurfaceState>
        field = MutableStateFlow<SurfaceState>(SurfaceState.Ignored)

    private val barcodeReaderState = barcodeReaderHandler.readerState

    val barcodeReaderOutput: StateFlow<ScanResult?>
        field = MutableStateFlow(null)

    init {
        stateMachineJob = machineScope.initialize()
        stateMachineJob?.invokeOnCompletion { e ->
            Timber.tag(TAG).d(e, "StateMachine job completed")
        }

        barcodeReaderState.onEach { rs ->
            readerState.update { ss ->
                ss.copy(
                    id = rs.id,
                )
            }
        }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    private fun CoroutineScope.initialize() = launch {
        stateMachine = createStateMachine(
            start = true,
            scope = this,
            name = this@ReaderStateMachine::class.simpleName
        ) {
            val sm = this@createStateMachine

            listenerExceptionHandler = StateMachine.ListenerExceptionHandler { exception ->
                Timber.tag(TAG).e(exception, "Exception in state machine")
            }
            logger = StateMachine.Logger { Timber.tag(TAG).d(it()) }

            transition<ReaderEvent.User.Release> {
                targetState = ReaderState.Stopping
            }

            onTransitionComplete { activeStates, transitionParams ->
                Timber.tag(TAG).d("Transition complete: $activeStates, $transitionParams")
                val newStatus = when (transitionParams.direction.targetState) {
                    is ReaderState.Reading.Scanning -> ScannerState.Status.Scanning
                    is ReaderState.Reading.Image,
                    is ReaderState.Reading.Video -> ScannerState.Status.Capturing
                    is ReaderState.Initializing -> ScannerState.Status.Initializing
                    is ReaderState.Ready -> ScannerState.Status.Ready
                    is ReaderState.Failure -> ScannerState.Status.Failed
                    is ReaderState.Idle,
                    is ReaderState.Stopped,
                    is ReaderState.Stopping -> ScannerState.Status.Off
                    else -> readerState.value.status
                }
                readerState.update { state ->
                    state.copy(
                        status = newStatus
                    )
                }
            }

            addInitialState(ReaderState.Idle) {
                onEntry {
                    eventFlow.onEach {
                        stateMachine.processEvent(it)
                    }
                        .flowOn(Dispatchers.IO)
                        .launchIn(this@launch)
                }
                transitionOn<ReaderEvent.User.Launch> {
                    targetState = {
                        ReaderState.Initializing
                    }
                }
            }

            addState(ReaderState.Initializing) {
                onEntry {
                    val initialized = barcodeReaderHandler.openReader(
                        aggregator = readerCallbackAggregator,
                        codesSettings = settings.value.codeSettings
                    )
                    if (initialized) {
                        sm.processEvent(ReaderEvent.System.ScannerInit.AwaitingSurface)
                    } else {
                        sm.processEvent(ReaderEvent.System.ScannerInit.Failed)
                    }
                }

                transition<ReaderEvent.System.ScannerInit.Ready> {
                    targetState = ReaderState.Ready
                }

                transition<ReaderEvent.System.ScannerInit.AwaitingSurface> {
                    targetState = ReaderState.AwaitingSurface
                }

                transition<ReaderEvent.System.ScannerInit.Failed> {
                    targetState = ReaderState.Failure
                }

            }

            addState(ReaderState.AwaitingSurface) {
                onEntry {
                    surfaceState.update { SurfaceState.Requested }
                }

                transitionOn<ReaderEvent.System.SetSurface> {
                    targetState = {
                        barcodeReaderHandler.setSurface(event.surface)
                        surfaceState.update { SurfaceState.Available }
                        ReaderState.Ready
                    }
                }
            }

            addState(ReaderState.Ready) {

                transitionOn<ReaderEvent.User.Start> {
                    guard = { mode == ScannerState.Mode.Manual }
                    targetState = {
                        hwOnScanStart()
                        barcodeReaderHandler.decode()
                        ReaderState.Reading.Scanning
                    }
                }

                transitionOn<ReaderEvent.User.Stop> {
                    guard = { mode == ScannerState.Mode.Manual }
                    targetState = {
                        barcodeReaderHandler.stop()
                        ReaderState.Ready
                    }
                }

                transitionOn<ReaderEvent.System.StopScanning> {
                    barcodeReaderOutput.value = null
                    targetState = {
                        barcodeReaderHandler.stop()
                        ReaderState.Ready
                    }
                }

                addSetModeTransition()

            }

            addState(ReaderState.Reading.Scanning) {

                transitionOn<ReaderEvent.System.OnDecodeComplete> {
                    guard = { event.data != null }
                    targetState = {
                        hwOnScanEnd()
                        barcodeReaderOutput.value = ScanResult(
                            timestamp = Instant.now(),
                            payload = listOf(
                                ScanResult.Payload.StringValue(
                                    timestamp = Instant.now(),
                                    size = event.length,
                                    value = event.data?.decodeToString()
                                )
                            )
                        )

                        when (mode) {
                            ScannerState.Mode.Manual -> ReaderState.Ready
                            ScannerState.Mode.HandsFree -> ReaderState.Reading.Scanning
                        }
                    }
                }

                transition<ReaderEvent.System.OnError> {
                    barcodeReaderOutput.value = null
                    targetState = ReaderState.Failure
                }

                transitionOn<ReaderEvent.System.MotionDetected> {
                    barcodeReaderOutput.value = null
                    targetState = {
                        barcodeReaderHandler.decode()
                        ReaderState.Reading.Scanning
                    }
                }

                transitionOn<ReaderEvent.System.StopScanning> {
                    barcodeReaderOutput.value = null
                    targetState = {
                        barcodeReaderHandler.stop()
                        ReaderState.Ready
                    }
                }

                transition<ReaderEvent.System.OnFrameAvailable> {
                    targetState = ReaderState.Reading.Scanning
                }

                transitionOn<ReaderEvent.System.OnEvent> {
                    targetState = {
                        Timber.tag(TAG).d("Event: $event")
                        ReaderState.Reading.Scanning
                    }
                }

                transitionOn<ReaderEvent.User.Stop> {
                    guard = { mode == ScannerState.Mode.Manual }
                    targetState = {
                        barcodeReaderHandler.stop()
                        ReaderState.Ready
                    }
                }

                addSetModeTransition()
            }

            addState(ReaderState.Failure) {
                onEntry {
                    barcodeReaderOutput.value = null
                }
            }

            addState(ReaderState.Stopping) {
                onEntry {
                    surfaceState.update { SurfaceState.Ignored }
                    barcodeReaderHandler.closeReader()
                    sm.processEvent(ReaderEvent.System.ScannerInit.Closed)
                }

                transition<ReaderEvent.System.ScannerInit.Closed> {
                    targetState = ReaderState.Stopped
                }
            }

            addState(ReaderState.Stopped) {
                onEntry {
                    barcodeReaderOutput.value = null
                }

                transitionOn<ReaderEvent.User.Launch> {
                    targetState = {
                        ReaderState.Initializing
                    }
                }
            }

        }
    }

    fun processEvent(event: ReaderEvent) {
        machineScope.launch {
            while (!::stateMachine.isInitialized) {
                Timber.tag(TAG).d("Waiting for state machine to be initialized")
                kotlinx.coroutines.delay(500)
            }
            Timber.tag(TAG).d("Processing event: $event")
            eventFlow.tryEmit(event)
        }
    }

    private fun TransitionStateApi.addSetModeTransition() {
        transitionOn<ReaderEvent.User.SetMode> {
            guard = { mode != event.mode }
            targetState = {
                mode = event.mode
                readerState.update { it.copy(mode = event.mode) }
                when (event.mode) {

                    ScannerState.Mode.Manual -> {
                        barcodeReaderHandler.apply {
                            stopHandsFree()
                        }
                        ReaderState.Ready
                    }

                    ScannerState.Mode.HandsFree -> {
                        val r = barcodeReaderHandler.startHandsFree()
                        if (r) {
                            ReaderState.Reading.Scanning
                        } else {
                            barcodeReaderHandler.stopHandsFree()
                            ReaderState.Failure
                        }
                    }

                }
            }
        }
    }

    private fun hwOnScanStart() {
        val settings = settings.value
        when (settings.beepMode) {
            ScannerSettings.BeepMode.OFF,
            ScannerSettings.BeepMode.ON_END -> {}
            ScannerSettings.BeepMode.ON_START_ON_END -> {
                hardwareProvider.audio.beep(AudioService.BeepTone.HZ_1760)
            }
        }
        when(settings.vibrationMode) {
            ScannerSettings.VibrationMode.OFF,
            ScannerSettings.VibrationMode.ON_END -> {}
            ScannerSettings.VibrationMode.ON_START_ON_END -> {
                hardwareProvider.vibration.vibrate()
            }
        }
    }

    private fun hwOnScanEnd() {
        val settings = settings.value
        when (settings.beepMode) {
            ScannerSettings.BeepMode.OFF -> {}
            ScannerSettings.BeepMode.ON_END,
            ScannerSettings.BeepMode.ON_START_ON_END -> {
                hardwareProvider.audio.beep(AudioService.BeepTone.HZ_1760)
            }
        }
        when(settings.vibrationMode) {
            ScannerSettings.VibrationMode.OFF -> {}
            ScannerSettings.VibrationMode.ON_END,
            ScannerSettings.VibrationMode.ON_START_ON_END -> {
                hardwareProvider.vibration.vibrate()
            }
        }
    }

    companion object {
        private const val TAG = "ReaderStateMachine"
    }

}
