package dev.nordix.hardware.domain

interface HardwareProvider {
    val audio: AudioService
    val vibration: VibrationService
}
