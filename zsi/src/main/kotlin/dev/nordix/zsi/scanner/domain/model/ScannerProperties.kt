package dev.nordix.zsi.scanner.domain.model

data class ScannerProperties(
    val id: Int,
    val codesConfig: Map<Int, Int>
){

    companion object {
        val INITIAL = ScannerProperties(
            id = -1,
            codesConfig = mapOf()
        )
    }

}