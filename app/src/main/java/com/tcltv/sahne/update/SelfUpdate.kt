package com.tcltv.sahne.update

/** A newer Matinee published on GitHub, and how far installing it has got. */
data class SelfUpdate(
    val version: String,
    val publishedAt: Long,
    val stage: Stage,
    val percent: Int = 0,
) {
    enum class Stage {
        AVAILABLE,
        DOWNLOADING,

        /** Android's own install screen is up and waits for the user. */
        CONFIRMING,
        FAILED,
    }
}
