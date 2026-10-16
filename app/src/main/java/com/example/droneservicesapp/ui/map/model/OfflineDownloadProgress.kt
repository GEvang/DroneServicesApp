package com.example.droneservicesapp.ui.map.model

/**
 * Combines per-layer tile counts from a multi-layer offline download (imagery, labels) into one
 * 0–100 percentage, so the screen shows a single progress value.
 */
class OfflineDownloadProgress(layerCount: Int) {
    private val totals = IntArray(layerCount)
    private val processed = IntArray(layerCount)

    fun setTotal(layer: Int, total: Int) {
        totals[layer] = total.coerceAtLeast(0)
        processed[layer] = processed[layer].coerceAtMost(totals[layer])
    }

    fun setProcessed(layer: Int, count: Int) {
        processed[layer] = count.coerceIn(0, totals[layer])
    }

    fun complete(layer: Int) {
        processed[layer] = totals[layer]
    }

    /** Overall progress, or null until at least one layer has reported its tile count. */
    val percent: Int?
        get() {
            val total = totals.sum()
            if (total <= 0) return null
            return (processed.sum().toLong() * 100L / total).toInt().coerceIn(0, 100)
        }
}
