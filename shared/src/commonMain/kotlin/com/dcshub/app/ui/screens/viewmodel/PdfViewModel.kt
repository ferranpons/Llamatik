package com.dcshub.app.ui.screens.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import co.touchlab.kermit.Logger
import com.dcshub.app.data.usecases.GetPdfUseCase
import com.dcshub.app.platform.RootNavigatorRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import net.thauvin.erik.urlencoder.UrlEncoderUtil

class PdfViewModel(
    private val userManualUrl: String,
    private val getPdfUseCase: GetPdfUseCase,
    private val navigator: Navigator,
    private val rootNavigatorRepository: RootNavigatorRepository,
) : ScreenModel {
    private val _state = MutableStateFlow(PdfScreenState(null, null))
    val state = _state.asStateFlow()
    private val _sideEffects = Channel<PdfScreenSideEffects>()
    val sideEffects: Flow<PdfScreenSideEffects> = _sideEffects.receiveAsFlow()

    fun onStarted() {
    }

    fun onDownloadFileClicked() {
        screenModelScope.launch {
            _sideEffects.trySend(PdfScreenSideEffects.Downloading)
            getPdfUseCase.downloadFile(userManualUrl)
                .onSuccess { result ->
                    val file = FileKit.openFileSaver(
                        suggestedName = userManualUrl.urlToFileName(),
                        extension = "pdf"
                    )
                    result.first?.let { bytesArray ->
                        file?.write(bytesArray)
                    }
                    _sideEffects.trySend(PdfScreenSideEffects.OnDownloaded)
                }
                .onFailure {
                    _sideEffects.trySend(PdfScreenSideEffects.OnDownloadedError)
                    Logger.w(it) { "Error on downloading user manual" }
                }
        }
    }
}

data class PdfScreenState(
    val pdfData: ByteArray? = null,
    val encodedBase64Data: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PdfScreenState

        if (pdfData != null) {
            if (other.pdfData == null) return false
            if (!pdfData.contentEquals(other.pdfData)) return false
        } else if (other.pdfData != null) return false
        if (encodedBase64Data != other.encodedBase64Data) return false

        return encodedBase64Data == other.encodedBase64Data
    }

    override fun hashCode(): Int {
        var result = pdfData.contentHashCode()
        result = 31 * result + encodedBase64Data.hashCode()
        return result
    }
}

sealed class PdfScreenSideEffects {
    data object Initial : PdfScreenSideEffects()
    data object OnLoaded : PdfScreenSideEffects()
    data object OnLoadError : PdfScreenSideEffects()
    data object Downloading : PdfScreenSideEffects()
    data object OnDownloaded : PdfScreenSideEffects()
    data object OnDownloadedError : PdfScreenSideEffects()
}

fun String.urlToFileName(): String {
    val filename = this.substring(this.lastIndexOf("/") + 1).removeExtension()
    return UrlEncoderUtil.decode(filename)
}

fun String.removeExtension(): String {
    val lastIndex = this.lastIndexOf('.')
    if (lastIndex != -1) {
        return this.substring(0, lastIndex)
    }
    return this
}
