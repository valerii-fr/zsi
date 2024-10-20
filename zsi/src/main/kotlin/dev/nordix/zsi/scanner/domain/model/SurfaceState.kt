package dev.nordix.zsi.scanner.domain.model

sealed interface SurfaceState {
    object Ignored : SurfaceState
    object Requested : SurfaceState
    object Available : SurfaceState
}
