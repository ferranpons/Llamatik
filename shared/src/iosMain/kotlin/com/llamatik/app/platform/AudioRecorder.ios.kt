@file:OptIn(BetaInteropApi::class)

package com.llamatik.app.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.numberWithBool
import platform.Foundation.numberWithDouble
import platform.Foundation.numberWithInt

actual class AudioRecorder actual constructor() {

    private var recorder: AVAudioRecorder? = null
    private var path: String? = null

    actual val isRecording: Boolean
        get() = recorder?.recording == true

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun start(outputWavPath: String) = withContext(Dispatchers.Main) {
        if (isRecording) return@withContext

        // Ensure parent directory exists (safe + no NSString helpers needed)
        val fileUrl = NSURL.fileURLWithPath(outputWavPath)
        val dirUrl = fileUrl.URLByDeletingLastPathComponent()
        if (dirUrl != null) {
            NSFileManager.defaultManager.createDirectoryAtURL(
                url = dirUrl,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeDefault,
            options = 0u,
            error = null
        )
        session.setActive(true, error = null)

        // IMPORTANT: use a Kotlin Map (matches AVAudioRecorder ctor on K/N)
        val settings: Map<Any?, Any?> = mapOf(
            AVFormatIDKey!! to NSNumber.numberWithInt(kAudioFormatLinearPCM.toInt()),
            AVSampleRateKey!! to NSNumber.numberWithDouble(16000.0),
            AVNumberOfChannelsKey!! to NSNumber.numberWithInt(1),
            AVLinearPCMBitDepthKey!! to NSNumber.numberWithInt(16),
            AVLinearPCMIsFloatKey!! to NSNumber.numberWithBool(false),
            AVLinearPCMIsBigEndianKey!! to NSNumber.numberWithBool(false),
        )

        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val rec = AVAudioRecorder(uRL = fileUrl, settings = settings, error = err.ptr)
            require(err.value == null) {
                "AVAudioRecorder init error: ${err.value?.localizedDescription ?: "unknown"}"
            }

            // These are real Obj-C methods exposed as functions
            val okPrepare = rec.prepareToRecord()
            require(okPrepare) { "AVAudioRecorder.prepareToRecord() failed" }

            val okRecord = rec.record()
            require(okRecord) { "AVAudioRecorder.record() failed" }

            recorder = rec
            path = outputWavPath
        }
    }

    actual suspend fun stop(): String = withContext(Dispatchers.Main) {
        val p = path ?: ""
        runCatching { recorder?.stop() }
        recorder = null
        path = null
        p
    }
}
