package com.llamatik.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

actual class AudioRecorder actual constructor() {

    private val recording = AtomicBoolean(false)
    actual val isRecording: Boolean get() = recording.get()

    private val format = AudioFormat(16000f, 16, 1, true, false)
    private var line: TargetDataLine? = null
    private var outputPath: String? = null
    private var thread: Thread? = null

    actual suspend fun start(outputWavPath: String) = withContext(Dispatchers.IO) {
        if (recording.get()) return@withContext

        val file = File(outputWavPath)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()

        val info = DataLine.Info(TargetDataLine::class.java, format)
        val target = (AudioSystem.getLine(info) as TargetDataLine).apply {
            open(format)
            start()
        }

        line = target
        outputPath = outputWavPath
        recording.set(true)

        thread = Thread {
            try {
                AudioInputStream(target, format, AudioSystem.NOT_SPECIFIED).use { ais ->
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }.apply { start() }
    }

    actual suspend fun stop(): String = withContext(Dispatchers.IO) {
        val p = outputPath ?: ""
        if (!recording.get()) return@withContext p

        recording.set(false)

        try {
            line?.stop()
            line?.close()
        } catch (_: Throwable) {
        } finally {
            line = null
        }

        try {
            thread?.join(1500)
        } catch (_: Throwable) {
        } finally {
            thread = null
        }

        outputPath = null
        p
    }
}
