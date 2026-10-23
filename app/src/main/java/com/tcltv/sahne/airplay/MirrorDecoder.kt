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

/**
 * Decodes the phone's H.264 screen and draws it straight onto [surface]. A new SPS (the phone rotated or changed
 * resolution) gets a freshly configured decoder, because many TV decoders fail on a size change mid-stream; a
 * decoder that fails anyway is rebuilt at the next keyframe, which always carries its SPS.
 */
class VideoDecoder(private val surface: Surface, private val onVideoSize: (width: Int, height: Int) -> Unit) {

    private var codec: MediaCodec? = null
    private var drain: Thread? = null
    private var sps: ByteArray? = null

    @Volatile
    private var running = false

    @Volatile
    private var failed = false

    private var rendered = false

    fun queue(data: ByteArray, presentationNanos: Long) {
        val frameSps = spsOf(data)
        if (frameSps != null && (codec == null || failed || !frameSps.contentEquals(sps))) {
            restart(frameSps)
        }
        val decoder = codec?.takeIf { !failed } ?: return
        try {
            val index = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) return
            val buffer = decoder.getInputBuffer(index) ?: return
            buffer.clear()
            buffer.put(data)
            decoder.queueInputBuffer(index, 0, data.size, presentationNanos / 1000, 0)
        } catch (e: IllegalStateException) {
            markFailed(e)
        }
    }

    fun flush() {
        try {
            codec?.flush()
        } catch (e: IllegalStateException) {
            markFailed(e)
        }
    }

    private fun restart(newSps: ByteArray) {
        release()
        Log.i(TAG, "starting video decoder for a new stream (${newSps.size}-byte SPS)")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080).apply {
            setInteger(MediaFormat.KEY_MAX_WIDTH, 3840)
            setInteger(MediaFormat.KEY_MAX_HEIGHT, 2160)
        }
        val decoder = try {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                it.configure(format, surface, null, 0)
                it.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "video decoder could not start", e)
            return
        }
        Log.i(TAG, "video decoder ${decoder.name}")
        codec = decoder
        sps = newSps
        failed = false
        running = true
        drain = Thread({ drainOutput(decoder) }, "airplay-video-out").apply { start() }
    }

    private fun markFailed(e: IllegalStateException) {
        if (failed) return
        failed = true
        val detail = (e as? MediaCodec.CodecException)?.let { " (${it.diagnosticInfo}, recoverable=${it.isRecoverable}, transient=${it.isTransient})" }.orEmpty()
        Log.e(TAG, "video decoder failed$detail; waiting for the next keyframe", e)
    }

    private fun drainOutput(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (running) {
            try {
                val index = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                when {
                    // Frames are shown the moment they decode: the phone already paces them, and waiting only adds lag.
                    index >= 0 -> {
                        decoder.releaseOutputBuffer(index, true)
                        if (!rendered) {
                            rendered = true
                            Log.i(TAG, "first mirrored frame on screen")
                        }
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> reportSize(decoder.outputFormat)
                }
            } catch (e: IllegalStateException) {
                if (running) markFailed(e)
                return
            }
        }
    }

    /** The picture's own size, from the crop rectangle when the decoder pads the frame to its block size. */
    private fun reportSize(format: MediaFormat) {
        val width = if (format.containsKey("crop-right")) format.getInteger("crop-right") - format.getInteger("crop-left") + 1 else format.getInteger(MediaFormat.KEY_WIDTH)
        val height = if (format.containsKey("crop-bottom")) format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1 else format.getInteger(MediaFormat.KEY_HEIGHT)
        Log.i(TAG, "video ${width}x$height")
        if (width > 0 && height > 0) onVideoSize(width, height)
    }

    private fun release() {
        running = false
        drain?.join(500)
        drain = null
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    fun stop() {
        release()
        sps = null
    }

    companion object {
        /** The SPS NAL unit when the frame starts with one (Annex B start code, NAL type 7), without its start code. */
        fun spsOf(data: ByteArray): ByteArray? {
            if (data.size < 6 || data[0].toInt() != 0 || data[1].toInt() != 0 || data[2].toInt() != 0 || data[3].toInt() != 1) return null
            if (data[4].toInt() and 0x1F != 7) return null
            var end = 5
            while (end + 3 < data.size && !(data[end].toInt() == 0 && data[end + 1].toInt() == 0 && data[end + 2].toInt() == 0 && data[end + 3].toInt() == 1)) end++
            return data.copyOfRange(4, if (end + 3 < data.size) end else data.size)
        }
    }
}

/** Decodes the phone's audio (AAC-ELD and AAC-LC for mirroring, ALAC for music) and plays it. */
class AudioPlayer {

    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private var drain: Thread? = null
    private var alac: AlacDecoder? = null
    private var codecType = 0

    @Volatile
    private var running = false

    @Volatile
    private var gain = 1f

    fun start(codecType: Int) {
        stop()
        if (codecType == ALAC) {
            val decoder = AlacDecoder()
            if (!decoder.isReady) return
            alac = decoder
            track = createTrack()
            this.codecType = codecType
            return
        }
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
        val audioTrack = createTrack()
        this.codecType = codecType
        codec = decoder
        track = audioTrack
        running = true
        drain = Thread({ drainOutput(decoder, audioTrack) }, "airplay-audio-out").apply { start() }
    }

    private fun createTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(minBuffer * 2)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack.setVolume(gain)
        audioTrack.play()
        return audioTrack
    }

    fun queue(data: ByteArray, type: Int, presentationNanos: Long) {
        if (type != codecType || (codec == null && alac == null)) start(type)
        alac?.let { decoder ->
            val pcm = decoder.decode(data) ?: return
            track?.write(pcm, 0, pcm.size)
            return
        }
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
        alac?.release()
        alac = null
        codec = null
        track = null
        codecType = 0
    }

    private companion object {
        const val ALAC = 2
        const val ELD = 8
        const val LC = 4
        const val AAC_OBJECT_LC = 2
        const val AAC_OBJECT_ELD = 39
    }
}
