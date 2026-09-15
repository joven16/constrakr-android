package com.constrakr.liveness.depth

/**
 * Abstraction for TrueDepth-style depth on Android.
 * Galaxy S8 / A17: use [NoDepthProvider] — do not fake depth from RGB.
 */
interface DepthProvider {
    val isAvailable: Boolean
    suspend fun getDepthFrame(): DepthFrame?
}

data class DepthFrame(
    val depthRangeMeters: Float,
    val planeResidualMeters: Float,
    val noseProminenceMeters: Float,
    val validDepthRatio: Float,
    val medianDepthMeters: Float
)

/** Default for Samsung Galaxy S8, A17, and most field devices. */
class NoDepthProvider : DepthProvider {
    override val isAvailable: Boolean = false
    override suspend fun getDepthFrame(): DepthFrame? = null
}
