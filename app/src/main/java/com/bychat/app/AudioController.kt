package com.bychat.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import java.io.File

class AudioController(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var player: MediaPlayer? = null

    fun start(): Boolean = try {
        output = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(22_050)
            setOutputFile(output!!.absolutePath)
            prepare()
            start()
        }
        true
    } catch (_: Exception) { cancel(); false }

    fun stop(): String? = try {
        recorder?.stop()
        recorder?.release()
        recorder = null
        val file = output ?: return null
        if (file.length() !in 1..2_000_000) null else Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    } catch (_: Exception) { null } finally { output?.delete(); output = null }

    fun cancel() {
        runCatching { recorder?.stop() }
        recorder?.release(); recorder = null
        output?.delete(); output = null
    }

    fun play(encoded: String): Boolean = try {
        player?.release()
        val file = File.createTempFile("bychat_audio_", ".m4a", context.cacheDir).apply { writeBytes(Base64.decode(encoded, Base64.NO_WRAP)) }
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { it.release(); file.delete(); if (player === it) player = null }
            setOnErrorListener { mp, _, _ -> mp.release(); file.delete(); if (player === mp) player = null; true }
            prepare()
            start()
        }
        true
    } catch (_: Exception) { false }

    fun release() { cancel(); player?.release(); player = null }
}
