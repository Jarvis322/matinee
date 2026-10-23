package com.tcltv.sahne.airplay

/** ALAC (Apple Lossless) frames to PCM, in native code. */
class AlacDecoder {

    private var handle = create()

    val isReady: Boolean get() = handle != 0L

    /** 16-bit little-endian stereo PCM for one frame, or null when the frame is damaged. */
    fun decode(frame: ByteArray): ByteArray? = if (handle == 0L) null else decode(handle, frame)

    fun release() {
        if (handle != 0L) destroy(handle)
        handle = 0L
    }

    private external fun create(): Long

    private external fun decode(handle: Long, frame: ByteArray): ByteArray?

    private external fun destroy(handle: Long)

    private companion object {
        init {
            System.loadLibrary("matinee_airplay")
        }
    }
}
