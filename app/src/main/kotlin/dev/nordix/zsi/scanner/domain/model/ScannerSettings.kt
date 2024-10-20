package dev.nordix.zsi.settings.domain.model

import android.content.Context
import dev.nordix.zsi.scanner.helpers.ScannerDefaults.getDefaultCodesParams

data class ScannerSettings(
    val codeSettings: Map<Int, Int>,
    val flashMode: FlashMode,
    val aimMode: AimMode,
    val beepMode: BeepMode,
    val vibrationMode: VibrationMode,
    val stopTimeout: Int,
    val useTrigger: Boolean,
) {

    sealed interface AtomicSetting {
        companion object {
            inline fun <reified T> fromInt(value: Int?): T? where T : Enum<T>, T : AtomicSetting {
                return T::class.java.enumConstants?.getOrNull(value ?: -1)
            }
        }
    }

    enum class FlashMode : AtomicSetting {
        OFF,
        ON,
    }

    enum class AimMode : AtomicSetting {
        OFF,
        ON,
    }

    enum class BeepMode : AtomicSetting {
        OFF,
        ON_END,
        ON_START_ON_END,
    }

    enum class VibrationMode : AtomicSetting {
        OFF,
        ON_END,
        ON_START_ON_END,
    }

    companion object {
        fun getDefault(context: Context) : ScannerSettings {
            return ScannerSettings(
                codeSettings = getDefaultCodesParams(context),
                flashMode = FlashMode.ON,
                aimMode = AimMode.ON,
                stopTimeout = 5000,
                beepMode = BeepMode.ON_END,
                vibrationMode = VibrationMode.ON_END,
                useTrigger = true,
            )
        }

        fun codeSettingsFromStringMap(map: Map<String, String>?) : Map<Int, Int>? {
            return map?.map { it.key.toInt() to it.value.toInt() }?.toMap()
        }
    }

}
