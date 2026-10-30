package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

class AudioRecorderManager(
    private val scope: CoroutineScope,
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onWaveformChanged: (List<Float>) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioRecorderMgr"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private val isRecording = AtomicBoolean(false)
    private var recordJob: Job? = null

    private val totalBytesRecorded = AtomicLong(0L)
    private val chunksRecordedCount = AtomicLong(0L)

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording.get()) {
            Log.d(TAG, "AudioRecord is already running.")
            return true
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid buffer size from getMinBufferSize: $minBufferSize")
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        // Try primary MIC source, fallback to VOICE_COMMUNICATION or DEFAULT
        val sourcesToTry = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT
        )

        var record: AudioRecord? = null
        for (source in sourcesToTry) {
            try {
                val candidate = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    Log.i(TAG, "AudioRecord initialized successfully with audio source: $source")
                    break
                } else {
                    Log.w(TAG, "AudioRecord candidate failed to initialize with source: $source")
                    candidate.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception initializing AudioRecord with source $source: ${e.message}")
            }
        }

        if (record == null) {
            Log.e(TAG, "All AudioRecord audio source attempts failed initialization.")
            return false
        }

        try {
            // Attach hardware acoustic echo canceler and noise suppressor if available
            val sessionId = record.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "AcousticEchoCanceler enabled on session $sessionId")
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "NoiseSuppressor enabled on session $sessionId")
                }
            }

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord failed to start recording (state: ${record.recordingState})")
                record.release()
                return false
            }

            audioRecord = record
            isRecording.set(true)
            totalBytesRecorded.set(0L)
            chunksRecordedCount.set(0L)
            Log.i(TAG, "Microphone capture active at ${SAMPLE_RATE}Hz mono 16-bit PCM.")

            recordJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048) // 64ms at 16kHz 16-bit mono
                while (isActive && isRecording.get()) {
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        val count = chunksRecordedCount.incrementAndGet()
                        val total = totalBytesRecorded.addAndGet(readBytes.toLong())

                        val chunk = buffer.copyOf(readBytes)
                        onAudioChunk(chunk)

                        // Compute RMS amplitude for visualizer orb (0.0 to 1.0)
                        val amplitude = calculateRms(chunk)
                        onAmplitudeChanged(amplitude)

                        // Compute multi-band waveform bars for real-time visualizer
                        val waveform = calculateWaveform(chunk, 32)
                        onWaveformChanged(waveform)

                        if (count % 50 == 0L) {
                            Log.d(TAG, "Mic capture healthy: $count chunks ($total bytes, RMS amp=${String.format("%.3f", amplitude)})")
                        }
                    } else if (readBytes < 0) {
                        Log.w(TAG, "AudioRecord.read returned error code: $readBytes")
                        Thread.sleep(20)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord pipeline: ${e.message}", e)
            stop()
            return false
        }
    }

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

    fun stop() {
        Log.i(TAG, "Stopping microphone capture pipeline...")
        isRecording.set(false)
        recordJob?.cancel()
        recordJob = null

        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null

            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
        onAmplitudeChanged(0f)
        onWaveformChanged(List(32) { 0.04f })
    }

    fun isRecording(): Boolean = isRecording.get()
}
