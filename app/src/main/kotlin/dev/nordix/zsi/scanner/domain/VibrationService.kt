package dev.nordix.hardware.domain

interface VibrationService {
    fun vibrate()
    fun vibrate(millis: Long)
}
