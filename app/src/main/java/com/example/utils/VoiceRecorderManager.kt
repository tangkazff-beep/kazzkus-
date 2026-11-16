package com.example.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorderManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var outputFile: File? = null

    fun startRecording(): File {
        stopRecording()
        val dir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
        val file = File(dir, "vn_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        return file
    }

    fun getMaxAmplitudeNormalized(): Float {
        return try {
            val amp = recorder?.maxAmplitude ?: 0
            (amp / 32767f).coerceIn(0.05f, 1f)
        } catch (_: Exception) {
            0.05f
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null
            outputFile
        } catch (e: Exception) {
            Log.w("VoiceRecorder", "Error stopping recorder: ${e.message}")
            recorder?.release()
            recorder = null
            null
        }
    }

    fun playAudio(filePath: String, onComplete: () -> Unit) {
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener {
                    stopPlayback()
                    onComplete()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Playback failed", e)
            onComplete()
        }
    }

    fun stopPlayback() {
        try {
            player?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            player = null
        }
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
