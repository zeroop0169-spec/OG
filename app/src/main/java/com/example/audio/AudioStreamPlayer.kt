package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

class AudioStreamPlayer(
    private val scope: CoroutineScope,
    private val onPlaybackStateChange: (isPlaying: Boolean) -> Unit,
    private val onOutputLevel: (Float) -> Unit,
    private val onOutputWaveform: (List<Float>) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioStreamPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate = DEFAULT_SAMPLE_RATE
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val isRunning = AtomicBoolean(false)
    private var playbackJob: Job? = null

    private val totalBytesPlayed = AtomicLong(0L)
    private var lastAudioChunkTime = 0L
    private var currentlyReportingPlaying = false

    init {
        initAudioTrack(DEFAULT_SAMPLE_RATE)
    }

    private fun initAudioTrack(sampleRate: Int) {
        currentSampleRate = sampleRate
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = if (minBufferSize > 0) (minBufferSize * 2).coerceAtLeast(8192) else 16384

        try {
            audioTrack?.release()
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            Log.i(TAG, "AudioTrack initialized at ${sampleRate}Hz mono 16-bit PCM, bufferSize=$bufferSize, state=${audioTrack?.state}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack: ${e.message}", e)
        }
    }

    fun start() {
        if (isRunning.get()) return
        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            initAudioTrack(currentSampleRate)
        }

        try {
            audioTrack?.play()
            isRunning.set(true)
            Log.i(TAG, "AudioStreamPlayer started. AudioTrack playState=${audioTrack?.playState}")

            playbackJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRunning.get()) {
                    val chunk = audioQueue.poll()
                    if (chunk != null) {
                        lastAudioChunkTime = System.currentTimeMillis()
                        if (!currentlyReportingPlaying) {
                            currentlyReportingPlaying = true
                            onPlaybackStateChange(true)
                        }

                        // Compute output RMS for visualizer
                        val level = calculateRms(chunk)
                        onOutputLevel(level)

                        // Compute multi-band waveform bars for real-time visualizer
                        val waveform = calculateWaveform(chunk, 32)
                        onOutputWaveform(waveform)

                        // Ensure AudioTrack is actively playing
                        if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            try { audioTrack?.play() } catch (e: Exception) { Log.w(TAG, "play() retry: ${e.message}") }
                        }

                        val written = audioTrack?.write(chunk, 0, chunk.size) ?: -1
                        if (written > 0) {
                            totalBytesPlayed.addAndGet(written.toLong())
                        } else {
                            Log.w(TAG, "AudioTrack.write returned: $written")
                            if (written == AudioTrack.ERROR_DEAD_OBJECT || written == AudioTrack.ERROR_INVALID_OPERATION) {
                                initAudioTrack(currentSampleRate)
                                audioTrack?.play()
                            }
                        }
                    } else {
                        // Queue is temporarily empty. Debounce by 350ms before declaring playback ended
                        // to avoid flickering false barge-in and state oscillations between network chunks.
                        if (currentlyReportingPlaying && System.currentTimeMillis() - lastAudioChunkTime > 350L) {
                            currentlyReportingPlaying = false
                            onPlaybackStateChange(false)
                            onOutputLevel(0f)
                            onOutputWaveform(List(32) { 0.04f })
                            Log.d(TAG, "Audio queue drained. Playback finished.")
                        }
                        Thread.sleep(15)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioTrack: ${e.message}", e)
        }
    }

    fun enqueueAudio(pcmBytes: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        if (sampleRate != currentSampleRate && sampleRate > 0) {
            Log.i(TAG, "Sample rate changed from $currentSampleRate to $sampleRate. Reinitializing AudioTrack.")
            stopAndFlush()
            initAudioTrack(sampleRate)
        }

        if (!isRunning.get()) {
            start()
        }
        audioQueue.offer(pcmBytes)
    }

    /**
     * Barge-in handler: immediately halts active speech playback and flushes queued audio
     */
    fun stopAndFlush() {
        audioQueue.clear()
        currentlyReportingPlaying = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
        }
        onPlaybackStateChange(false)
        onOutputLevel(0f)
        onOutputWaveform(List(32) { 0.04f })
    }

    fun isPlaying(): Boolean = currentlyReportingPlaying || !audioQueue.isEmpty()

    private fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        var sum = 0.0
        val numSamples = pcmBytes.size / 2
        for (i in 0 until numSamples) {
            val sample = (pcmBytes[i * 2 + 1].toInt() shl 8) or (pcmBytes[i * 2].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
        }
        val rms = sqrt(sum / numSamples)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun calculateWaveform(pcmBytes: ByteArray, barCount: Int = 32): List<Float> {
        val numSamples = pcmBytes.size / 2
        if (numSamples < barCount) return List(barCount) { 0.04f }

        val samplesPerBar = (numSamples / barCount).coerceAtLeast(1)
        val result = ArrayList<Float>(barCount)

        for (b in 0 until barCount) {
            var sumSquares = 0.0
            val startSample = b * samplesPerBar
            val count = samplesPerBar.coerceAtMost(numSamples - startSample)
            for (i in 0 until count) {
                val sampleIdx = (startSample + i) * 2
                if (sampleIdx + 1 < pcmBytes.size) {
                    val sample = (pcmBytes[sampleIdx + 1].toInt() shl 8) or (pcmBytes[sampleIdx].toInt() and 0xFF)
                    val s = sample.toDouble()
                    sumSquares += s * s
                }
            }
            val rms = if (count > 0) sqrt(sumSquares / count) else 0.0
            // Power curve (sqrt) to boost quieter speech frequencies visually
            val normalized = (rms / 26000.0).coerceIn(0.0, 1.0)
            val boosted = sqrt(normalized).toFloat().coerceIn(0.04f, 1.0f)
            result.add(boosted)
        }
        return result
    }

    fun release() {
        Log.i(TAG, "Releasing AudioStreamPlayer (total bytes played: ${totalBytesPlayed.get()})")
        isRunning.set(false)
        playbackJob?.cancel()
        playbackJob = null
        audioQueue.clear()
        currentlyReportingPlaying = false

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
        onPlaybackStateChange(false)
        onOutputLevel(0f)
        onOutputWaveform(List(32) { 0.04f })
    }
}
