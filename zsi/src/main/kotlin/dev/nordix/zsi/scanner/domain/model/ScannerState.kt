package dev.nordix.zsi.scanner.domain.model

data class ScannerState(
    val id: Int,
    val status: Status,
    val mode: Mode,
    val properties: ScannerProperties,
) {

    enum class Mode {
        Manual,
        HandsFree
    }

    enum class Status {
        Off,
        Initializing,
        Ready,
        Scanning,
        Capturing,
        Failed,
        Unavailable,
        ;
    }

    companion object {
        val INITIAL = ScannerState(
            id = -1,
            status = Status.Off,
            properties = ScannerProperties.INITIAL,
            mode = Mode.Manual,
        )
    }
}
