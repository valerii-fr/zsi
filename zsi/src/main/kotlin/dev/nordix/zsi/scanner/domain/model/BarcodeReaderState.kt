package dev.nordix.zsi.scanner.domain.model

data class BarcodeReaderState(
    val id: Int = -1,
    val currentState: ReaderStateType = ReaderStateType.UNINITIALIZED,
) {

    enum class ReaderStateType {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        READING,
        EXCEPTION,
        UNAVAILABLE,
    }

}
