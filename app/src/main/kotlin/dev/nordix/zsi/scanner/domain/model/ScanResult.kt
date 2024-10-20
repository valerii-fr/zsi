package dev.nordix.zsi.scanner.domain.model

import java.time.Instant

data class ScanResult(
    val timestamp: Instant,
    val payload: List<Payload>
) {
    sealed interface Payload {
        val size: Int
        val timestamp: Instant

        data class StringValue(
            override val timestamp: Instant,
            override val size: Int,
            val value: String?
        ) : Payload

        @Suppress("ArrayInDataClass")
        data class ByteArrayValue(
            override val timestamp: Instant,
            override val size: Int,
            val value: ByteArray
        ) : Payload
    }

}
