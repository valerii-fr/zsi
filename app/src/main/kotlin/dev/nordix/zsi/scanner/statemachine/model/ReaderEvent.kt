package dev.nordix.zsi.scanner.statemachine.model

import android.graphics.SurfaceTexture
import android.view.SurfaceView
import dev.nordix.zsi.scanner.domain.model.ScannerProperties
import dev.nordix.zsi.scanner.domain.model.ScannerState
import ru.nsk.kstatemachine.event.Event

internal interface ReaderEvent : Event {

    sealed interface User : ReaderEvent {

        data class SetProperties(val properties: ScannerProperties) : User
        object Start : User
        object Stop : User
        object Launch : User
        object Release : User
        data class SetMode(val mode: ScannerState.Mode) : User

    }

    sealed interface System : ReaderEvent {
        sealed interface ScannerInit : System {
            data object Ready : ScannerInit
            data object AwaitingSurface : ScannerInit
            data object Closed : ScannerInit
            data object Failed : ScannerInit
        }

        data class OnDecodeComplete(
            val symbology: Int,
            val length: Int,
            val data: ByteArray?,
        ) : System

        data class OnEvent(
            val event: Int,
            val info: Int,
            val data: ByteArray?,
        ) : System

        data class OnError(
            val error: Int,
        ) : System

        data class OnFrameAvailable(
            val surfaceTexture: SurfaceTexture?,
        ) : System

        data class SetSurface(
            val surface: SurfaceView,
        ) : System

        object MotionDetected : System
        object StopScanning : System

    }

}
