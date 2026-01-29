package com.llamatik.app.feature.chatbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotState
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.localization.Localization
import com.llamatik.app.ui.icon.LlamatikIcons

@Composable
fun ChatInputBox(
    localization: Localization,
    state: ChatBotState,
    viewModel: ChatBotViewModel,
    showSuggestions: MutableState<Boolean>,
    suggestions: List<String> = listOf(
        localization.suggestion1,
        localization.suggestion2,
        localization.suggestion3,
        localization.suggestion4,
        localization.suggestion5,
        localization.suggestion6
    ),
    onOpenModelSelector: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue())
        }

        val isGenerating = state.isGenerating
        Column(
            horizontalAlignment = Alignment.Start,
        ) {
            if (showSuggestions.value && suggestions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(suggestions.size) { index ->
                        val hint = suggestions[index]
                        if (index == 0) {
                            Spacer(modifier = Modifier.size(16.dp))
                        }

                        Surface(
                            onClick = {
                                input = TextFieldValue(hint)
                                val message = input.text.trim()
                                if (message.isNotEmpty()) {
                                    input = TextFieldValue()
                                    viewModel.onMessageSendDirect(message)
                                    showSuggestions.value = false
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 6.dp)
                                .widthIn(max = 200.dp)
                        ) {
                            Text(
                                text = hint,
                                style = com.llamatik.app.ui.theme.Typography.get().labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (index == suggestions.size - 1) {
                            Spacer(modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val canSend = input.text.isNotBlank()
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    placeholder = { Text(localization.askMeAnything) },
                    textStyle = com.llamatik.app.ui.theme.Typography.get().bodyMedium,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating && canSend) {
                                val message = input.text.trim()
                                input = TextFieldValue()
                                viewModel.onMessageSendDirect(message)
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
                        if (isGenerating) {
                            IconButton(
                                onClick = {
                                    viewModel.stopGeneration()
                                },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = localization.stop,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (canSend) {
                                        val message = input.text.trim()
                                        input = TextFieldValue()
                                        viewModel.onMessageSendDirect(message)
                                        showSuggestions.value = false
                                        keyboardController?.hide()
                                    }
                                },
                                enabled = canSend,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
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
                    },
                )
            }

            GenerateModelSelector(
                selectedModelName = state.selectedGenerateModelName,
                onOpenModelSelector = onOpenModelSelector,
                onOpenSettings = onOpenSettings
            )
        }
    }
}
