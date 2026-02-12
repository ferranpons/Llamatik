package com.llamatik.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.*

actual class AudioRecorder actual constructor() {

    private var recorder: AVAudioRecorder? = null
    private var path: String? = null

    actual val isRecording: Boolean
        get() = recorder?.recording() == true

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun start(outputWavPath: String) = withContext(Dispatchers.Main) {
        if (isRecording) return@withContext

        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeDefault,
            options = 0u,
            error = null
        )
        session.setActive(true, error = null)

        val url = NSURL.fileURLWithPath(outputWavPath)

        val settings = mutableDictionaryOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatLinearPCM,
            AVSampleRateKey to 16000.0,
            AVNumberOfChannelsKey to 1,
            AVLinearPCMBitDepthKey to 16,
            AVLinearPCMIsFloatKey to false,
            AVLinearPCMIsBigEndianKey to false,
            AVEncoderAudioQualityKey to AVAudioQualityHigh
        )

        val errPtr = createNullableVar<NSError>()
        val rec = AVAudioRecorder(url, settings, errPtr.ptr)
        val err = errPtr.value
        require(err == null) { "AVAudioRecorder init error: ${err?.localizedDescription}" }

        rec.prepareToRecord()
        val ok = rec.record()
        require(ok) { "AVAudioRecorder.record() failed" }

        recorder = rec
        path = outputWavPath
    }

    actual suspend fun stop(): String = withContext(Dispatchers.Main) {
        val p = path ?: ""
        recorder?.stop()
        recorder = null
        path = null
        p
    }
}
