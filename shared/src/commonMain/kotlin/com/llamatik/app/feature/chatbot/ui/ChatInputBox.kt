package com.llamatik.app.feature.chatbot.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotState
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.chatbot.viewmodel.GenerationMode
import com.llamatik.app.localization.Localization
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.Typography
import kotlin.math.PI
import kotlin.math.sin

private const val ROUNDED_CORNER_SIZE = 16
private const val BUTTON_SIZE = 40
private const val HORIZONTAL_PADDING = 16

@Composable
fun ChatInputBox(
    localization: Localization,
    state: ChatBotState,
    viewModel: ChatBotViewModel,
    showSuggestions: MutableState<Boolean>,
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    suggestions: List<String> = listOf(
        localization.suggestion1,
        localization.suggestion2,
        localization.suggestion3,
        localization.suggestion4,
        localization.suggestion5,
        localization.suggestion6
    ),
    onOpenChatHistory: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
    isListening: Boolean,
    isTranscribing: Boolean,
    onMicClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isGenerating = state.isGenerating

        Column(horizontalAlignment = Alignment.Start) {
            if (showSuggestions.value && suggestions.isNotEmpty()) {
                LazyRow(modifier = Modifier.fillMaxWidth()) {
                    items(suggestions.size) { index ->
                        val hint = suggestions[index]
                        if (index == 0) {
                            Spacer(modifier = Modifier.size(HORIZONTAL_PADDING.dp))
                        }

                        Surface(
                            onClick = {
                                val message = hint.trim()
                                if (message.isNotEmpty()) {
                                    onInputChange(TextFieldValue())
                                    when (state.generationMode) {
                                        GenerationMode.TEXT -> viewModel.onMessageSendDirect(message)
                                        GenerationMode.IMAGE -> viewModel.onImagePromptSendDirect(message)
                                    }
                                    showSuggestions.value = false
                                }
                            },
                            shape = RoundedCornerShape(ROUNDED_CORNER_SIZE.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 6.dp)
                                .widthIn(max = 200.dp)
                        ) {
                            Text(
                                text = hint,
                                style = Typography.get().labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (index == suggestions.size - 1) {
                            Spacer(modifier = Modifier.size(HORIZONTAL_PADDING.dp))
                        }
                    }
                }
            }

            // --- NEW: status pill + waveform (only when listening/transcribing) ---
            if (isListening || isTranscribing) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = HORIZONTAL_PADDING.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(ROUNDED_CORNER_SIZE.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = LlamatikIcons.Microphone,
                            contentDescription = localization.voiceInput,
                            tint = if (isListening) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        Text(
                            text = when {
                                isTranscribing -> localization.transcribing
                                else -> localization.listening
                            },
                            style = Typography.get().labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.size(10.dp))

                        if (isListening) {
                            RecordingWaveform(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(18.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = HORIZONTAL_PADDING.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = onOpenChatHistory,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = LlamatikIcons.ChatHistory,
                        contentDescription = localization.chatHistory,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Surface(
                    shape = RoundedCornerShape(ROUNDED_CORNER_SIZE.dp),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val clipboard = LocalClipboardManager.current

                    val canSend = input.text.isNotBlank()
                    val clipboardText = clipboard.getText()?.text?.trim().orEmpty()
                    val canPaste = clipboardText.isNotBlank()

                    TextField(
                        value = input,
                        onValueChange = { onInputChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        placeholder = { Text(localization.askMeAnything) },
                        textStyle = Typography.get().bodyMedium,
                        singleLine = false,
                        minLines = 1,
                        maxLines = 6,
                        shape = RoundedCornerShape(ROUNDED_CORNER_SIZE.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (!isGenerating && canSend) {
                                    val message = input.text.trim()
                                    onInputChange(TextFieldValue())
                                    when (state.generationMode) {
                                        GenerationMode.TEXT -> viewModel.onMessageSendDirect(message)
                                        GenerationMode.IMAGE -> viewModel.onImagePromptSendDirect(message)
                                    }
                                    showSuggestions.value = false
                                    keyboardController?.hide()
                                }
                            },
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (canPaste) {
                                    IconButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text?.trim().orEmpty()
                                            if (text.isNotBlank()) {
                                                onInputChange(
                                                    TextFieldValue(
                                                        text = text,
                                                        selection = TextRange(text.length)
                                                    )
                                                )
                                                showSuggestions.value = false
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .size(BUTTON_SIZE.dp)
                                            .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            imageVector = LlamatikIcons.Paste,
                                            contentDescription = localization.paste,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.size(6.dp))
                                }

                                IconButton(
                                    onClick = {
                                        val next =
                                            if (state.generationMode == GenerationMode.TEXT) GenerationMode.IMAGE
                                            else GenerationMode.TEXT
                                        viewModel.setGenerationMode(next)
                                    },
                                    enabled = !isGenerating && !isTranscribing,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(BUTTON_SIZE.dp)
                                        .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Icon(
                                        imageVector = if (state.generationMode == GenerationMode.TEXT) LlamatikIcons.Image else LlamatikIcons.Text,
                                        contentDescription =
                                            if (state.generationMode == GenerationMode.TEXT) "Image generation" else "Text generation",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.size(6.dp))

                                if (!canSend && !isGenerating && !isTranscribing) {
                                    val micEnabled = !isTranscribing && !isGenerating
                                    IconButton(
                                        onClick = { if (micEnabled) onMicClick() },
                                        enabled = micEnabled,
                                        modifier = Modifier
                                            .size(BUTTON_SIZE.dp)
                                            .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE.dp))
                                            .background(
                                                when {
                                                    isListening -> MaterialTheme.colorScheme.errorContainer
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Filled.Stop else LlamatikIcons.Microphone,
                                            contentDescription = localization.voiceInput,
                                            tint = when {
                                                isListening -> MaterialTheme.colorScheme.onErrorContainer
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.size(6.dp))
                                }

                                if (isGenerating) {
                                    IconButton(
                                        onClick = { viewModel.stopGeneration() },
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(BUTTON_SIZE.dp)
                                            .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Stop,
                                            contentDescription = localization.stop,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                } else {
                                    if (canSend) {
                                        IconButton(
                                            onClick = {
                                                val message = input.text.trim()
                                                onInputChange(TextFieldValue())
                                                when (state.generationMode) {
                                                    GenerationMode.TEXT -> viewModel.onMessageSendDirect(message)
                                                    GenerationMode.IMAGE -> viewModel.onImagePromptSendDirect(message)
                                                }
                                                showSuggestions.value = false
                                                keyboardController?.hide()
                                            },
                                            enabled = canSend,
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(BUTTON_SIZE.dp)
                                                .clip(RoundedCornerShape(ROUNDED_CORNER_SIZE.dp))
                                                .background(
                                                    if (canSend) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                        ) {
                                            Icon(
                                                imageVector = LlamatikIcons.Send,
                                                contentDescription = localization.send,
                                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            GenerateModelSelector(
                selectedModelName = state.selectedGenerateModelName,
                onOpenModelSelector = onOpenModelSelector,
                onOpenSettings = onOpenSettings
            )
        }
    }
}

@Composable
private fun RecordingWaveform(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing)
        ),
        label = "phase"
    )

    val bars = remember { 22 }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = (w / (bars * 2f)).coerceAtLeast(2f)
        val gap = barW
        val radius = CornerRadius(barW / 2f, barW / 2f)

        for (i in 0 until bars) {
            val t = (i.toFloat() / bars.toFloat()) + phase.value
            val amp = (sin(t * 2f * PI).toFloat() * 0.5f + 0.5f)
            val barH = (h * (0.25f + 0.75f * amp)).coerceAtLeast(2f)

            val x = i * (barW + gap)
            val y = (h - barH) / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = radius
            )
        }
    }
}
