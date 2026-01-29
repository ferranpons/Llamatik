package com.llamatik.app.feature.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llamatik.app.feature.chatbot.model.GenerateSettings
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.theme.Typography
import kotlin.math.roundToInt

private val GenerateSettingsSaver: Saver<GenerateSettings, Any> = listSaver(
    save = { gs ->
        listOf(gs.temperature, gs.maxTokens, gs.topP, gs.topK, gs.repeatPenalty)
    },
    restore = { list ->
        GenerateSettings(
            temperature   = (list[0] as Number).toFloat(),
            maxTokens     = (list[1] as Number).toInt(),
            topP          = (list[2] as Number).toFloat(),
            topK          = (list[3] as Number).toInt(),
            repeatPenalty = (list[4] as Number).toFloat()
        )
    }
)

@Composable
private fun rememberGenerateSettingsState(initial: GenerateSettings): MutableState<GenerateSettings> {
    return rememberSaveable(stateSaver = GenerateSettingsSaver) {
        mutableStateOf(initial)
    }
}

@Composable
fun ModelSettingsBottomSheet(
    current: GenerateSettings,
    onApply: (GenerateSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            ParamsView(
                initial = current,
                onApply = onApply,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ParamsView(
    initial: GenerateSettings,
    onApply: (GenerateSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val localization = getCurrentLocalization()
    val generateSettings = rememberGenerateSettingsState(initial)

    Text(
        text = localization.generationSettings,
        style = Typography.get().titleLarge
    )
    Spacer(Modifier.height(4.dp))

    ParamSlider(
        label = localization.temperature,
        value = generateSettings.value.temperature,
        valueRange = 0.0f..2.0f,
        step = 0.01f,
        format = { "$it" },
        onChange = { generateSettings.value = generateSettings.value.copy(temperature = it) }
    )

    ParamIntField(
        label = localization.maxTokens,
        value = generateSettings.value.maxTokens,
        min = 16,
        max = 8192,
        onChange = { generateSettings.value = generateSettings.value.copy(maxTokens = it) }
    )

    ParamSlider(
        label = localization.topP,
        value = generateSettings.value.topP,
        valueRange = 0.0f..1.0f,
        step = 0.01f,
        format = { "$it" },
        onChange = { generateSettings.value = generateSettings.value.copy(topP = it) }
    )

    ParamIntField(
        label = localization.topK,
        value = generateSettings.value.topK,
        min = 1,
        max = 1000,
        onChange = { generateSettings.value = generateSettings.value.copy(topK = it) }
    )

    ParamSlider(
        label = localization.repeatPenalty,
        value = generateSettings.value.repeatPenalty,
        valueRange = 0.8f..2.0f,
        step = 0.01f,
        format = { "$it" },
        onChange = { generateSettings.value = generateSettings.value.copy(repeatPenalty = it) }
    )

    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onDismiss) { Text(localization.close) }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                onApply(generateSettings.value)
                onDismiss()
            }
        ) { Text(localization.apply) }
    }

    Spacer(Modifier.height(16.dp))
}


@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = Typography.get().labelLarge)
            Text(format(value), style = Typography.get().labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = { onChange((it / step).roundToInt() * step) },
            valueRange = valueRange
        )
    }
}

@Composable
private fun ParamIntField(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(value.toString()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter { it.isDigit() }
                val v = text.toIntOrNull()
                if (v != null) onChange(v.coerceIn(min, max))
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("${min}–$max", style = Typography.get().labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
