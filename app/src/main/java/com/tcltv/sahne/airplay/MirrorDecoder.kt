package com.tcltv.sahne.airplay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import kotlin.math.pow

private const val TAG = "MirrorDecoder"
private const val DEQUEUE_TIMEOUT_US = 10_000L
private const val SAMPLE_RATE = 44_100

/** Decodes the phone's H.264 screen and draws it straight onto [surface]. */
class VideoDecoder(private val surface: Surface) {

    private var codec: MediaCodec? = null
    private var drain: Thread? = null

    @Volatile
    private var running = false

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080).apply {
            setInteger(MediaFormat.KEY_MAX_WIDTH, 3840)
            setInteger(MediaFormat.KEY_MAX_HEIGHT, 2160)
            setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        }
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        decoder.configure(format, surface, null, 0)
        decoder.start()
        codec = decoder
        running = true
        drain = Thread({ drainOutput(decoder) }, "airplay-video-out").apply { start() }
    }

    fun queue(data: ByteArray, presentationNanos: Long) {
        val decoder = codec ?: return
        try {
            val index = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) return
            val buffer = decoder.getInputBuffer(index) ?: return
            buffer.clear()
            buffer.put(data)
            decoder.queueInputBuffer(index, 0, data.size, presentationNanos / 1000, 0)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "video decoder not ready", e)
        }
    }

    fun flush() {
        try {
            codec?.flush()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "video flush failed", e)
        }
    }

    private fun drainOutput(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (running) {
            try {
                val index = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                // Frames are shown the moment they decode: the phone already paces them, and waiting only adds lag.
                if (index >= 0) decoder.releaseOutputBuffer(index, true)
            } catch (e: IllegalStateException) {
                return
            }
        }
    }

    fun stop() {
        running = false
        drain?.join(500)
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }
}

/** Decodes the phone's audio (AAC-ELD or AAC-LC) and plays it. ALAC, used by some music streams, is not supported yet. */
class AudioPlayer {

    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private var drain: Thread? = null
    private var codecType = 0

    @Volatile
    private var running = false

    @Volatile
    private var gain = 1f

    fun start(codecType: Int) {
        stop()
        val config = when (codecType) {
            // AAC-ELD, 44.1 kHz stereo; the bytes are the AudioSpecificConfig the phone's encoder uses.
            ELD -> byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50, 0x00)
            // AAC-LC, 44.1 kHz stereo.
            LC -> byteArrayOf(0x12, 0x10)
            else -> {
                Log.w(TAG, "audio codec $codecType is not supported")
                return
            }
        }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, if (codecType == ELD) AAC_OBJECT_ELD else AAC_OBJECT_LC)
            setByteBuffer("csd-0", ByteBuffer.wrap(config))
        }
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        decoder.configure(format, null, null, 0)
        decoder.start()
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(minBuffer * 2)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack.setVolume(gain)
        audioTrack.play()
        this.codecType = codecType
        codec = decoder
        track = audioTrack
        running = true
        drain = Thread({ drainOutput(decoder, audioTrack) }, "airplay-audio-out").apply { start() }
    }

    fun queue(data: ByteArray, type: Int, presentationNanos: Long) {
        if (codec == null || type != codecType) start(type)
        val decoder = codec ?: return
        try {
            val index = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) return
            val buffer = decoder.getInputBuffer(index) ?: return
            buffer.clear()
            buffer.put(data)
            decoder.queueInputBuffer(index, 0, data.size, presentationNanos / 1000, 0)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "audio decoder not ready", e)
        }
    }

    /** AirPlay sends volume in decibels from -30 (quiet) to 0 (full), and -144 for mute. */
    fun setVolume(decibels: Float) {
        gain = if (decibels <= -30f) 0f else 10.0.pow(0.05 * decibels).toFloat().coerceIn(0f, 1f)
        track?.setVolume(gain)
    }

    fun flush() {
        runCatching { track?.pause(); track?.flush(); track?.play() }
        runCatching { codec?.flush() }
    }

    private fun drainOutput(decoder: MediaCodec, audioTrack: AudioTrack) {
        val info = MediaCodec.BufferInfo()
        val scratch = ByteArray(16 * 1024)
        while (running) {
            try {
                val index = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                if (index >= 0) {
                    val output = decoder.getOutputBuffer(index)
                    if (output != null && info.size > 0) {
                        output.position(info.offset)
                        var remaining = info.size
                        while (remaining > 0) {
                            val chunk = minOf(remaining, scratch.size)
                            output.get(scratch, 0, chunk)
                            audioTrack.write(scratch, 0, chunk)
                            remaining -= chunk
                        }
                    }
                    decoder.releaseOutputBuffer(index, false)
                }
            } catch (e: IllegalStateException) {
                return
            }
        }
    }

    fun stop() {
        running = false
        drain?.join(500)
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        codec = null
        track = null
        codecType = 0
    }

    private companion object {
        const val ELD = 8
        const val LC = 4
        const val AAC_OBJECT_LC = 2
        const val AAC_OBJECT_ELD = 39
    }
}
