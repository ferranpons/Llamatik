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
        if (!recording.compareAndSet(false, true)) return@withContext

        val file = File(outputWavPath)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()

        try {
            val info = DataLine.Info(TargetDataLine::class.java, format)
            val target = (AudioSystem.getLine(info) as TargetDataLine).apply {
                open(format)
                start()
            }

            line = target
            outputPath = outputWavPath

            thread = Thread({
                try {
                    // ✅ This constructor exists reliably on JDKs:
                    AudioInputStream(target).use { ais ->
                        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file)
                    }
                } catch (t: Throwable) {
                    println("[AudioRecorder] Error while writing audio: ${t.message}")
                    t.printStackTrace()
                } finally {
                    // Ensure the line is closed no matter what
                    try {
                        if (target.isOpen) {
                            target.stop()
                            target.close()
                        }
                    } catch (_: Throwable) {
                    }
                    recording.set(false)
                }
            }, "AudioRecorder-Writer").apply {
                isDaemon = true
                start()
            }
        } catch (t: Throwable) {
            recording.set(false)
            line = null
            outputPath = null
            thread = null
            throw t
        }
    }

    actual suspend fun stop(): String = withContext(Dispatchers.IO) {
        val p = outputPath ?: ""
        if (!recording.get() && line == null) return@withContext p

        recording.set(false)

        try {
            line?.let {
                if (it.isOpen) {
                    it.stop()
                    it.close()
                }
            }
        } catch (_: Throwable) {
        } finally {
            line = null
        }

        try {
            thread?.join(2500)
        } catch (_: Throwable) {
        } finally {
            thread = null
        }

        outputPath = null
        p
    }
}
