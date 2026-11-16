package com.example.domain.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesizes an unmistakable referee trill whistle ("Trillerpfeife") via Android AudioTrack.
 * Zero external audio assets required and completely crash-safe in non-audio/JVM test environments.
 */
object WhistleSoundEngine {

    suspend fun playTrillWhistle() = withContext(Dispatchers.IO) {
        try {
            val sampleRate = 22050
            val durationSeconds = 1.05
            val numSamples = (sampleRate * durationSeconds).toInt()
            val pcm = ShortArray(numSamples)

            val trillRateHz = 28.0 // Pea-trill modulation speed inside referee whistle

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Two energetic whistle blasts: 0.00..0.38s and 0.46..1.00s
                val envelope = when {
                    t < 0.03 -> t / 0.03
                    t < 0.35 -> 1.0
                    t < 0.38 -> (0.38 - t) / 0.03
                    t < 0.46 -> 0.0
                    t < 0.49 -> (t - 0.46) / 0.03
                    t < 0.96 -> 1.0
                    t < 1.02 -> (1.02 - t) / 0.06
                    else -> 0.0
                }

                // Dual-frequency FM + AM trill modulation typical of a Fox-40 / pea trill whistle
                val trillMod = sin(2.0 * PI * trillRateHz * t)
                val carrierFreq = 2920.0 + 180.0 * trillMod
                val harmonicFreq = carrierFreq * 1.48
                val ampMod = 0.72 + 0.28 * trillMod

                val wave = 0.75 * sin(2.0 * PI * carrierFreq * t) +
                        0.25 * sin(2.0 * PI * harmonicFreq * t)
                val sample = (wave * ampMod * envelope * Short.MAX_VALUE * 0.85).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcm[i] = sample.toShort()
            }

            val bufferBytes = pcm.size * 2
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferBytes)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.setVolume(AudioTrack.getMaxVolume())
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    kotlinx.coroutines.delay(1150L)
                }
            } finally {
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                } catch (_: Throwable) {
                }
                try {
                    track.release()
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
            // Gracefully ignore audio failures in test / muted environments
        }
    }
}
