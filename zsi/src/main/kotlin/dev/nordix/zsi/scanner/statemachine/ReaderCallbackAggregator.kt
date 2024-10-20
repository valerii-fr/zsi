package dev.nordix.zsi.scanner.statemachine

import android.graphics.SurfaceTexture
import com.zebra.adc.decoder.BarCodeReader
import dev.nordix.zsi.scanner.statemachine.model.ReaderEvent
import timber.log.Timber

internal class ReaderCallbackAggregator(
    private val readerStateMachine: ReaderStateMachine
) :
    BarCodeReader.DecodeCallback,
    BarCodeReader.ErrorCallback,
    BarCodeReader.PreviewCallback,
    BarCodeReader.PictureCallback,
    SurfaceTexture.OnFrameAvailableListener
{
    override fun onDecodeComplete(
        symbology: Int,
        length: Int,
        data: ByteArray?,
        reader: BarCodeReader?
    ) {
        Timber.tag("ReaderCallbackAggregator").d("onDecodeComplete: $symbology, $length, $data")
        readerStateMachine.processEvent(
            ReaderEvent.System.OnDecodeComplete(
                symbology = symbology,
                length = length,
                data = data
            )
        )
    }

    override fun onEvent(
        event: Int,
        info: Int,
        data: ByteArray?,
        reader: BarCodeReader?
    ) {
        Timber.tag("ReaderCallbackAggregator").d("onEvent: $event, $info, $data")
        when (event) {
            BarCodeReader.BCRDR_EVENT_SCAN_MODE_CHANGED -> {
                readerStateMachine.processEvent(
                    ReaderEvent.System.StopScanning
                )
            }
            BarCodeReader.BCRDR_EVENT_MOTION_DETECTED -> {
                readerStateMachine.processEvent(
                    ReaderEvent.System.MotionDetected
                )
            }
            else -> readerStateMachine.processEvent(
                ReaderEvent.System.OnEvent(
                    event = event,
                    info = info,
                    data = data
                )
            )
        }

    }

    override fun onError(error: Int, reader: BarCodeReader?) {
        readerStateMachine.processEvent(
            ReaderEvent.System.OnError(
                error = error
            )
        )
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        readerStateMachine.processEvent(
            ReaderEvent.System.OnFrameAvailable(
                surfaceTexture = surfaceTexture
            )
        )
    }

    override fun onPreviewFrame(
        data: ByteArray?,
        reader: BarCodeReader?
    ) {
        Timber.tag("ReaderCallbackAggregator").d("onPreviewFrame")
    }

    override fun onPictureTaken(
        format: Int,
        width: Int,
        height: Int,
        data: ByteArray?,
        reader: BarCodeReader?
    ) {
        Timber.tag("ReaderCallbackAggregator").d("onPictureTaken")
    }
}
