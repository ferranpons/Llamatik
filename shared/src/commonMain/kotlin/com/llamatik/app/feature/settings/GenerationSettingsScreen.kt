package com.llamatik.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.model.GenerateSettings
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.koin.core.parameter.ParametersHolder
import kotlin.math.roundToInt

class GenerationSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val localization = getCurrentLocalization()

        val viewModel = navigator.koinNavigatorScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )
        val state by viewModel.state.collectAsState()

        LlamatikTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = localization.generationSettings,
                                style = Typography.get().titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go back"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    GenerationSettingsContent(
                        current = state.generateSettings,
                        onApply = {
                            viewModel.onGenerateSettingsApplied(it)
                            navigator.pop()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerationSettingsContent(
    current: GenerateSettings,
    onApply: (GenerateSettings) -> Unit,
) {
    val localization = getCurrentLocalization()
    val settings = rememberGenerateSettingsState(current)

    ParamSlider(
        label = localization.temperature,
        value = settings.value.temperature,
        valueRange = 0.0f..2.0f,
        step = 0.01f,
        format = { "$it" },
        onChange = { settings.value = settings.value.copy(temperature = it) }
    )

    ParamIntField(
        label = localization.maxTokens,
        value = settings.value.maxTokens,
        min = 16,
        max = 8192,
        onChange = { settings.value = settings.value.copy(maxTokens = it) }
    )

    ParamSlider(
        label = localization.topP,
        value = settings.value.topP,
        valueRange = 0.0f..1.0f,
        step = 0.01f,
        format = { "$it" },
        onChange = { settings.value = settings.value.copy(topP = it) }
    )

    ParamIntField(
        label = localization.topK,
        value = settings.value.topK,
        min = 1,
        max = 1000,
        onChange = { settings.value = settings.value.copy(topK = it) }
    )

    ParamSlider(
        label = localization.repeatPenalty,
        value = settings.value.repeatPenalty,
        valueRange = 0.8f..2.0f,
        step = 0.01f,
        format = { "$it" },
        onChange = { settings.value = settings.value.copy(repeatPenalty = it) }
    )

    ParamIntField(
        label = localization.contextLength,
        value = settings.value.contextLength,
        min = 512,
        max = 32768,
        onChange = { settings.value = settings.value.copy(contextLength = it) }
    )

    ParamIntField(
        label = localization.numThreads,
        value = settings.value.numThreads,
        min = 1,
        max = 16,
        onChange = { settings.value = settings.value.copy(numThreads = it) }
    )

    ParamToggle(
        label = localization.useMmap,
        checked = settings.value.useMmap,
        onChange = { settings.value = settings.value.copy(useMmap = it) }
    )

    ParamToggle(
        label = localization.flashAttention,
        checked = settings.value.flashAttention,
        onChange = { settings.value = settings.value.copy(flashAttention = it) }
    )

    ParamIntField(
        label = localization.batchSize,
        value = settings.value.batchSize,
        min = 32,
        max = 2048,
        onChange = { settings.value = settings.value.copy(batchSize = it) }
    )

    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onApply(settings.value) }
        ) { Text(localization.apply) }
    }

    Spacer(Modifier.height(16.dp))
}

private val GenerateSettingsSaver: Saver<GenerateSettings, Any> = listSaver(
    save = { gs ->
        listOf(
            gs.temperature, gs.maxTokens, gs.topP, gs.topK, gs.repeatPenalty,
            gs.contextLength, gs.numThreads, gs.useMmap, gs.flashAttention, gs.batchSize
        )
    },
    restore = { list ->
        GenerateSettings(
            temperature = (list[0] as Number).toFloat(),
            maxTokens = (list[1] as Number).toInt(),
            topP = (list[2] as Number).toFloat(),
            topK = (list[3] as Number).toInt(),
            repeatPenalty = (list[4] as Number).toFloat(),
            contextLength = (list[5] as Number).toInt(),
            numThreads = (list[6] as Number).toInt(),
            useMmap = list[7] as Boolean,
            flashAttention = list[8] as Boolean,
            batchSize = (list[9] as Number).toInt(),
        )
    }
)

@Composable
private fun rememberGenerateSettingsState(initial: GenerateSettings): MutableState<GenerateSettings> {
    return rememberSaveable(stateSaver = GenerateSettingsSaver) { mutableStateOf(initial) }
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
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = Typography.get().labelLarge, modifier = Modifier.weight(1f))
            Text(format(value), style = Typography.get().labelLarge)
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
        Text(
            "$min–$max",
            style = Typography.get().labelSmall
        )
    }
}

@Composable
private fun ParamToggle(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Text(label, style = Typography.get().labelLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
