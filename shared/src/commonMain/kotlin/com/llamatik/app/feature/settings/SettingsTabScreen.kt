package com.llamatik.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.feature.chatbot.ui.ModelSettingsBottomSheet
import com.llamatik.app.feature.chatbot.viewmodel.ChatBotViewModel
import com.llamatik.app.feature.debugmenu.viewmodel.DebugMenuViewModel
import com.llamatik.app.localization.AvailableLanguages
import com.llamatik.app.localization.SetLanguage
import com.llamatik.app.localization.displayName
import com.llamatik.app.localization.getCurrentLanguage
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.components.ColoredSnackBarHost
import com.llamatik.app.ui.icon.LlamatikIcons
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.koin.core.parameter.ParametersHolder

class SettingsTabScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val chatViewModel = koinScreenModel<ChatBotViewModel>(
            parameters = { ParametersHolder(listOf(navigator).toMutableList(), false) }
        )
        val state by chatViewModel.state.collectAsState()
        val showModelSettings = remember { mutableStateOf(false) }

        val debugViewModel = koinScreenModel<DebugMenuViewModel>()
        val snackbarHostState = remember { SnackbarHostState() }

        LlamatikTheme {
            SettingsMenuView(
                viewModel = debugViewModel,
                snackbarHostState = snackbarHostState,
                onClose = { navigator.pop() },
                onOpenModelSettings = { showModelSettings.value = true },
            )

            if (showModelSettings.value) {
                ModelSettingsBottomSheet(
                    current = state.generateSettings,
                    onApply = { chatViewModel.onGenerateSettingsApplied(it) },
                    onDismiss = { showModelSettings.value = false }
                )
            }
        }
    }

    @Composable
    fun SettingsMenuView(
        viewModel: DebugMenuViewModel,
        snackbarHostState: SnackbarHostState,
        onClose: () -> Unit,
        onOpenModelSettings: (() -> Unit)? = null,
    ) {
        val localization = getCurrentLocalization()
        val state by viewModel.state.collectAsState()
        val showLanguagePicker = remember { mutableStateOf(false) }

        if (state.currentLanguage != getCurrentLanguage()) {
            SetLanguage(state.currentLanguage)
        }

        LlamatikTheme {
            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        snackbar = { ColoredSnackBarHost(snackbarHostState) }
                    )
                },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = localization.settings,
                                style = Typography.get().titleLarge
                            )
                        },
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    )
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .padding(paddingValues)
                        .padding(bottom = 46.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = localization.language, style = Typography.get().bodyMedium)
                            Text(
                                text = state.currentLanguage.displayName,
                                style = Typography.get().labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Button(onClick = { showLanguagePicker.value = true }) {
                            Text(text = localization.change)
                        }
                    }

                    if (onOpenModelSettings != null) {
                        Spacer(Modifier.size(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(localization.generationSettings)
                            Button(onClick = onOpenModelSettings) {
                                Text(localization.configure)
                            }
                        }
                    }
                }
            }
        }

        if (showLanguagePicker.value) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { showLanguagePicker.value = false },
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                sheetState = sheetState,
            ) {
                LlamatikTheme {
                    Text(
                        text = localization.chooseLanguage,
                        style = Typography.get().titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.size(16.dp))

                    LazyColumn {
                        items(AvailableLanguages.languages) { language ->
                            if (language.ordinal == 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onSelectedLanguage(language)
                                        showLanguagePicker.value = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = language.displayName,
                                    style = Typography.get().bodyLarge
                                )
                                if (language == state.currentLanguage) {
                                    Icon(
                                        imageVector = LlamatikIcons.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    @Composable
    fun LabelledCheckbox(
        modifier: Modifier = Modifier,
        label: String,
        checked: Boolean,
        enabled: Boolean = true,
        colors: CheckboxColors = CheckboxDefaults.colors(),
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = colors
            )
            Spacer(Modifier.width(32.dp))
            Text(label)
        }
    }

    @Composable
    fun LabelledSwitch(
        label: String,
        modifier: Modifier = Modifier,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = LlamatikIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}
