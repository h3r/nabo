package com.notabot.wowfishing.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioSplashDetector(
    private val onAudioLevel: (Int) -> Unit,
    private val onSplashDetected: () -> Unit
) {
    private val isRecording = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    var threshold: Int = 40
    @Volatile var isListeningForTrigger: Boolean = false
    @Volatile var ignoreUntilMillis: Long = 0L

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.get()) return

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                stop()
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            workerThread = Thread({ processAudio(bufferSize) }, "AudioSplashDetectorThread").apply {
                priority = Thread.NORM_PRIORITY + 2
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            stop()
        }
    }

    private fun processAudio(bufferSize: Int) {
        val audioBuffer = ShortArray(bufferSize / 2)

        while (isRecording.get()) {
            val record = audioRecord ?: break
            val readCount = record.read(audioBuffer, 0, audioBuffer.size)

            if (readCount > 0) {
                var sumSquares = 0.0
                for (i in 0 until readCount) {
                    val sample = audioBuffer[i].toDouble()
                    sumSquares += sample * sample
                }

                val rms = sqrt(sumSquares / readCount)
                // Map RMS (0 to ~32767) into a 0 to 100 responsive volume level
                val normalizedLevel = ((rms / 32767.0) * 200.0).coerceIn(0.0, 100.0).toInt()

                onAudioLevel(normalizedLevel)

                val now = SystemClock.uptimeMillis()
                if (isListeningForTrigger && now >= ignoreUntilMillis && normalizedLevel >= threshold) {
                    // Temporarily block trigger to prevent double-firing
                    isListeningForTrigger = false
                    ignoreUntilMillis = now + TRIGGER_COOLDOWN_MS
                    onSplashDetected()
                }
            } else if (readCount < 0) {
                Log.w(TAG, "AudioRecord read error code: $readCount")
                SystemClock.sleep(20)
            }
        }
    }

    fun armTrigger(gracePeriodMs: Long = 2500L) {
        ignoreUntilMillis = SystemClock.uptimeMillis() + gracePeriodMs
        isListeningForTrigger = true
    }

    fun disarmTrigger() {
        isListeningForTrigger = false
    }

    fun stop() {
        isRecording.set(false)
        isListeningForTrigger = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }

        workerThread?.interrupt()
        workerThread = null
    }

    companion object {
        private const val TAG = "AudioSplashDetector"
        private const val TRIGGER_COOLDOWN_MS = 4000L
    }
}
