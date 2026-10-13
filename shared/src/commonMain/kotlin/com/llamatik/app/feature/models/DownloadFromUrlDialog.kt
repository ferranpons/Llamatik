package com.llamatik.app.feature.models

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.llamatik.app.feature.chatbot.model.ModelCategory
import com.llamatik.app.localization.getCurrentLocalization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFromUrlDialog(
    initialCategory: ModelCategory? = null,
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String, category: ModelCategory) -> Unit,
) {
    val localization = getCurrentLocalization()

    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(initialCategory ?: ModelCategory.Generate) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (!nameManuallyEdited) {
            name = extractModelNameFromUrl(url)
        }
    }

    val categoryLabel = { cat: ModelCategory ->
        when (cat) {
            ModelCategory.Generate -> localization.generateModels
            ModelCategory.Stt -> localization.sttModels
            ModelCategory.StableDiffusion -> localization.imageGenerationModels
            ModelCategory.Vlm -> localization.vlmModels
            ModelCategory.Embed -> localization.embedModels
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localization.downloadFromUrl) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(localization.modelUrlLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameManuallyEdited = it.isNotEmpty()
                    },
                    label = { Text(localization.modelNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (initialCategory == null) {
                    Spacer(Modifier.height(12.dp))
                    ExposedDropdownMenuBox(
                        expanded = categoryMenuExpanded,
                        onExpandedChange = { categoryMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = categoryLabel(selectedCategory),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(localization.categoryLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                        ) {
                            ModelCategory.entries.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(categoryLabel(cat)) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedUrl = url.trim()
                    val trimmedName = name.trim().ifBlank { extractModelNameFromUrl(trimmedUrl) }
                    if (trimmedUrl.isNotBlank() && trimmedName.isNotBlank()) {
                        onConfirm(trimmedUrl, trimmedName, selectedCategory)
                    }
                },
                enabled = url.isNotBlank(),
            ) {
                Text(localization.download)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localization.cancel)
            }
        },
    )
}

fun extractModelNameFromUrl(url: String): String {
    val path = url.substringBefore("?").substringAfterLast("/")
    val nameWithoutExt = path.substringBeforeLast(".")
    return if (nameWithoutExt.isNotBlank()) nameWithoutExt else path
}
