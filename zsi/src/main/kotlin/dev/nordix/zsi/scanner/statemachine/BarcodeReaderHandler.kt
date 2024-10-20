package dev.nordix.zsi.scanner.statemachine

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraManager
import android.view.SurfaceView
import com.zebra.adc.decoder.BarCodeReader
import com.zebra.adc.decoder.BarCodeReader.ParamNum
import com.zebra.adc.decoder.BarCodeReader.ParamVal
import dev.nordix.zsi.scanner.helpers.ScannerDefaults.getDefaultCodesParams
import dev.nordix.zsi.scanner.helpers.ScannerDefaults.scannerSdkParams
import dev.nordix.zsi.scanner.domain.model.BarcodeReaderState
import dev.nordix.zsi.scanner.domain.model.BarcodeReaderState.ReaderStateType
import dev.nordix.zsi.settings.domain.model.ScannerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

internal class BarcodeReaderHandler(
    private val context: Context,
    private val settings: StateFlow<ScannerSettings>,
) {

    private var surfaceTexture: SurfaceTexture? = null
    val readerState: StateFlow<BarcodeReaderState>
        field = MutableStateFlow<BarcodeReaderState>(
            BarcodeReaderState(id = -1)
        )

    private var barCodeReader: BarCodeReader? = null

    suspend fun openReader(
        aggregator: ReaderCallbackAggregator,
        codesSettings: Map<Int, Int>
    ) : Boolean {
        try {
            Timber.tag(TAG).d("Initializing native SDK")
            System.loadLibrary("IAL.hht")
            System.loadLibrary("SDL.hht")
            System.loadLibrary("barcodereader90.hht") // Android 9.0
            readerState.update { it.copy(currentState = ReaderStateType.INITIALIZING) }
        } catch (e: Exception) {
            when (e) {
                is UnsatisfiedLinkError -> {
                    Timber.tag(TAG).e(e, "Native SDK init failed")
                    readerState.value = BarcodeReaderState(
                        currentState = ReaderStateType.UNAVAILABLE
                    )
                }

                else -> {
                    Timber.tag(TAG).e(e, "Reader init failed")
                    readerState.value = BarcodeReaderState(
                        currentState = ReaderStateType.EXCEPTION
                    )
                }
            }
            return false
        } finally {
            Timber.tag(TAG).d(readerState.value.toString())
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camerasCount = cameraManager.cameraIdList.size

        return try {
            val settings = settings.value
            barCodeReader = if (camerasCount == 0) {
                BarCodeReader.open(0, context.applicationContext)
            } else {
                BarCodeReader.open(1, context.applicationContext)
            }
            require(barCodeReader != null)
            readerState.value = BarcodeReaderState(id = 1)
            surfaceTexture = SurfaceTexture(5)
            surfaceTexture?.setOnFrameAvailableListener(aggregator)
            barCodeReader!!.apply {
                setDefaultParameters()
                setDecodeCallback(aggregator)
                setErrorCallback(aggregator)
                setPreviewTexture(surfaceTexture)
                scannerSdkParams.forEach {
                    setParameter(it.key, it.value)
                }
            }
            resetParameters()
            setParams(codesSettings)
            settings.setParams()

            readerState.update {
                it.copy(currentState = ReaderStateType.READY)
            }
            true
        } catch (e: Exception) {
            readerState.value = BarcodeReaderState(id = -1)
            Timber.tag(TAG).e(e, "Failed to open reader")
            false
        } finally {
            Timber.tag(TAG).d(readerState.value.toString())
        }

    }

    suspend fun closeReader() {
        barCodeReader?.let { reader ->
            reader.stopDecode()
            reader.setDecodeCallback(null)
            reader.setErrorCallback(null)
            reader.release()
            Timber.tag(TAG).v("reader is released")
        } ?: run {
            Timber.tag(TAG).v("reader is null")
        }
    }

    private fun setParams(params: Map<Int, Int>) {
        try {
            barCodeReader?.let { reader ->
                Timber.tag(TAG).v("set params: $params")
                params.map {
                    it.key to reader.setParameter(it.key, it.value)
                }.let {
                    Timber.tag(TAG).v("set params result: $it")
                }
            }
            readerState.update {
                it.copy(currentState = ReaderStateType.READY)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set params")
            readerState.update {
                it.copy(currentState = ReaderStateType.EXCEPTION)
            }
        }
    }

    fun stop() {
        runCatching {
            Timber.tag(TAG).v("stop decode")
            barCodeReader?.stopDecode()
            readerState.update {
                it.copy(currentState = ReaderStateType.READY)
            }
        }
    }

    fun decode() {
        runCatching {
            Timber.tag(TAG).v("start decode")
            barCodeReader?.startDecode()
            readerState.update {
                it.copy(currentState = ReaderStateType.READING)
            }
        }
    }

    internal fun setSurface(surface: SurfaceView) {
        runCatching {
            barCodeReader?.apply {
                setPreviewDisplay(surface.holder)
            }
        }
    }

    internal fun startHandsFree(): Boolean {
        runCatching {
            barCodeReader?.setParameter(
                ParamNum.AIMMODEHANDSFREE.toInt(),
                ParamVal.AIM_ON_ALWAYS.toInt()
            )
            barCodeReader?.setParameter(
                ParamNum.PRIM_TRIG_MODE.toInt(),
                ParamVal.HANDSFREE.toInt()
            )
        }
        return barCodeReader
            ?.startHandsFreeDecode(ParamVal.HANDSFREE.toInt()) == BarCodeReader.BCR_SUCCESS
    }

    internal fun stopHandsFree() {
        barCodeReader?.apply {
            setParameter(
                ParamNum.AIMMODEHANDSFREE.toInt(),
                ParamVal.AIM_OFF.toInt()
            )
            setParameter(
                ParamNum.PRIM_TRIG_MODE.toInt(),
                ParamVal.LEVEL.toInt()
            )
            stopDecode()
        }
    }

    private fun resetParameters() {
        val cs = getDefaultCodesParams(context)
        barCodeReader?.apply {
            setDefaultParameters()
            setParams(cs)
        }
    }

    private fun runCatching(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Exception in runCatching block")
            readerState.update {
                it.copy(currentState = ReaderStateType.EXCEPTION)
            }
        }
    }

    private fun ScannerSettings.setParams() {
        barCodeReader?.apply {
            setParameter(
                ParamNum.IMG_ILLUM.toInt(),
                flashMode.ordinal
            )
            setParameter(
                ParamNum.IMG_AIM_MODE.toInt(),
                aimMode.ordinal
            )
            setParameter(
                ParamNum.AIMMODEHANDSFREE.toInt(),
                aimMode.ordinal
            )
            setParameter(
                ParamNum.IMG_AIM_SNAPSHOT.toInt(),
                aimMode.ordinal
            )
            setParameter(
                ParamNum.LASER_ON_PRIM.toInt(),
                stopTimeout / 100
            )
            setParameter(
                ParamNum.IMG_SNAPTIMEOUT.toInt(),
                stopTimeout / 100
            )
        }
    }

    companion object {
        private const val TAG = "BarcodeReaderHandler"
    }

}
