package com.llamatik.app.platform

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

actual class AudioRecorder actual constructor() {

    private val recording = AtomicBoolean(false)
    actual val isRecording: Boolean get() = recording.get()

    private var audioRecord: AudioRecord? = null
    private var outputPath: String? = null
    private var writerThread: Thread? = null
    private var bytesWritten: Long = 0L

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    @RequiresPermission(value = "android.permission.RECORD_AUDIO")
    actual suspend fun start(outputWavPath: String) = withContext(Dispatchers.IO) {
        if (recording.get()) return@withContext

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        require(minBuf > 0) { "AudioRecord.getMinBufferSize failed: $minBuf" }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            minBuf * 2
        )

        require(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized" }

        // Prepare file
        val file = File(outputWavPath)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        FileOutputStream(file).use { fos ->
            // Reserve 44 bytes for WAV header (we’ll write real values on stop)
            fos.write(ByteArray(44))
        }

        outputPath = outputWavPath
        bytesWritten = 0L
        audioRecord = record
        recording.set(true)

        record.startRecording()

        val buf = ByteArray(minBuf)
        writerThread = Thread {
            try {
                FileOutputStream(file, true).use { fos ->
                    while (recording.get()) {
                        val read = record.read(buf, 0, buf.size)
                        if (read > 0) {
                            fos.write(buf, 0, read)
                            bytesWritten += read.toLong()
                        }
                    }
                    fos.flush()
                }
            } catch (_: Throwable) {
                // Swallow; caller will stop and handle file existence
            }
        }.apply { start() }
    }

    actual suspend fun stop(): String = withContext(Dispatchers.IO) {
        val path = outputPath ?: ""
        if (!recording.get()) return@withContext path

        recording.set(false)

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }

        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        } finally {
            audioRecord = null
        }

        try {
            writerThread?.join(1500)
        } catch (_: Throwable) {
        } finally {
            writerThread = null
        }

        if (path.isNotBlank()) {
            writeWavHeader(path, dataBytes = bytesWritten, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
        }
        outputPath = null
        path
    }

    private fun writeWavHeader(
        path: String,
        dataBytes: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val riffChunkSize = 36 + dataBytes

        RandomAccessFile(path, "rw").use { raf ->
            raf.seek(0)

            fun writeString(s: String) = raf.write(s.toByteArray(Charsets.US_ASCII))
            fun writeLEInt(v: Int) {
                raf.write(v and 0xFF)
                raf.write((v shr 8) and 0xFF)
                raf.write((v shr 16) and 0xFF)
                raf.write((v shr 24) and 0xFF)
            }
            fun writeLEShort(v: Int) {
                raf.write(v and 0xFF)
                raf.write((v shr 8) and 0xFF)
            }

            writeString("RIFF")
            writeLEInt(riffChunkSize.toInt())
            writeString("WAVE")

            writeString("fmt ")
            writeLEInt(16)                // Subchunk1Size (PCM)
            writeLEShort(1)               // AudioFormat (1=PCM)
            writeLEShort(channels)
            writeLEInt(sampleRate)
            writeLEInt(byteRate)
            writeLEShort(blockAlign)
            writeLEShort(bitsPerSample)

            writeString("data")
            writeLEInt(dataBytes.toInt())
        }
    }
}
