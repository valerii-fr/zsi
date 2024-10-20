package dev.nordix.zsi.scanner.statemachine.model

import ru.nsk.kstatemachine.state.DefaultState

internal sealed class ReaderState : DefaultState() {

    data object Initializing : ReaderState()
    data object AwaitingSurface : ReaderState()
    data object Ready : ReaderState()
    data object Idle : ReaderState()
    data object Stopping : ReaderState()
    data object Stopped : ReaderState()
    data object Failure : ReaderState()

    sealed class Reading : ReaderState() {
        data object Video : Reading()
        data object Image : Reading()
        data object Scanning : Reading()
    }

}
