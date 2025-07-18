package com.llamatik.app.platform

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@SuppressLint("DiscouragedApi")
@Composable
actual fun font(name: String, res: String, weight: FontWeight, style: FontStyle): Font {
    val context = LocalContext.current
    val id = context.resources.getIdentifier(res, "font", context.packageName)
    return Font(id, weight, style)
}

@SuppressLint("DiscouragedApi")
@Composable
fun getResourceId(name: String?, resourceFolder: String?): Int {
    val context = LocalContext.current
    return context.resources.getIdentifier(name, resourceFolder, context.packageName)
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class AudioPlayer actual constructor() {
    var soundPool: SoundPool? = null
    var mediaPlayer: MediaPlayer? = null
    val soundList: MutableList<Int> = mutableListOf()

    actual fun loadSound(resource: String): Int? {
        if (soundPool == null) {
            soundPool = SoundPool.Builder().setMaxStreams(2).build()
        }

        return soundPool?.load(resource, 1)
    }

    @Composable
    actual fun loadMusic(path: String) {
        val context = LocalContext.current
        mediaPlayer = MediaPlayer.create(context, Uri.parse(path))
        mediaPlayer?.let { mediaPlayer ->
            mediaPlayer.setVolume(0.8f, 0.8f)
            mediaPlayer.setOnCompletionListener {
                it.start()
            }
        }
    }

    actual fun playMusic() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    actual fun playSound(position: Int) {
        soundPool?.play(position, 99.5f, 99.5f, 0, 0, 1f)
    }

    actual fun pause() {
        soundPool?.autoPause()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    actual fun resume() {
        soundPool?.autoResume()
    }

    actual fun release() {
        soundPool?.release()
        mediaPlayer?.release()
    }
}
