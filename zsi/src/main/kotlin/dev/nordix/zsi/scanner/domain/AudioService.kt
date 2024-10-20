package dev.nordix.hardware.domain

interface AudioService {
    fun beep(tone: BeepTone)
    fun beep(tone: BeepTone, millis: Long)

    enum class BeepTone(val hz: Int) {
        HZ_440(440),
        HZ_880(880),
        HZ_1320(1320),
        HZ_1760(1760),
        HZ_2200(2200),
        HZ_2640(2640),
        HZ_3080(3080),
    }
}
