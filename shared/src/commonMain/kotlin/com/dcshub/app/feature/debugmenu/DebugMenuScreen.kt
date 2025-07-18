@file:OptIn(ExperimentalMaterial3Api::class)

package com.dcshub.app.feature.debugmenu

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.dcshub.app.feature.debugmenu.viewmodel.DebugMenuSideEffects
import com.dcshub.app.feature.debugmenu.viewmodel.DebugMenuViewModel
import com.dcshub.app.localization.AvailableLanguages
import com.dcshub.app.localization.SetLanguage
import com.dcshub.app.localization.getCurrentLanguage
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.platform.Environment
import com.dcshub.app.platform.ServerEnvironment
import com.dcshub.app.ui.components.ColoredSnackBarHost
import com.dcshub.app.ui.components.Picker
import com.dcshub.app.ui.components.PickerModel
import com.dcshub.app.ui.components.PickerOption
import com.dcshub.app.ui.icon.LlamatikIcons
import com.dcshub.app.ui.theme.LlamatikTheme
import com.dcshub.app.ui.theme.Typography

class DebugMenuScreen : Screen {
    private lateinit var showingModal: MutableState<Boolean>

    @Composable
    override fun Content() {
        LlamatikTheme {
            val currentNavigator = LocalNavigator.currentOrThrow
            val snackbarHostState = remember { SnackbarHostState() }
            showingModal = remember { mutableStateOf(false) }

            val viewModel = koinScreenModel<DebugMenuViewModel>()

            SetupSideEffects(viewModel)
            DebugMenuView(viewModel, snackbarHostState) {
                currentNavigator.pop()
            }
        }
    }

    @Composable
    private fun SetupSideEffects(
        viewModel: DebugMenuViewModel
    ) {
        val coroutineScope = rememberCoroutineScope()
        val sideEffects = viewModel.sideEffects.collectAsState(
            DebugMenuSideEffects.Initial,
            coroutineScope.coroutineContext
        )
        when (sideEffects.value) {
            DebugMenuSideEffects.Initial -> {}
        }
    }

    @Composable
    fun DebugMenuView(
        viewModel: DebugMenuViewModel,
        snackbarHostState: SnackbarHostState,
        onClose: () -> Unit
    ) {
        val localization = getCurrentLocalization()
        val state by viewModel.state.collectAsState()

        if (state.currentLanguage != getCurrentLanguage()) {
            SetLanguage(state.currentLanguage)
        }

        val pickerModel = remember {
            mutableStateOf(
                PickerModel(
                    "Choose Environment",
                    null,
                    ServerEnvironment.toPickerList {
                        viewModel.onSelectedEnvironment(it)
                        showingModal.value = false
                    }
                )
            )
        }

        LlamatikTheme {
            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        snackbar = {
                            ColoredSnackBarHost(snackbarHostState)
                        }
                    )
                },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = localization.debugMenuTitle,
                                style = Typography.get().bodyLarge
                            )
                        },
                        colors = TopAppBarDefaults.mediumTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        navigationIcon = {
                            IconButton(onClick = { onClose.invoke() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Go back"
                                )
                            }
                        }
                    )
                }
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .padding(it).padding(bottom = 46.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(scrollState)
                ) {
                    /*
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row {
                                Text(
                                    text = "Environment:"
                                )
                                Spacer(Modifier.size(16.dp))
                                Text(
                                    text = state.currentEnvironment.toString(),
                                    style = Typography.get().bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "URL: ${state.currentEnvironment.url}",
                                style = Typography.get().labelSmall
                            )
                        }

                        Button(
                            onClick = {
                                pickerModel.value = PickerModel(
                                    "Choose Environment",
                                    null,
                                    ServerEnvironment.toPickerList {
                                        viewModel.onSelectedEnvironment(it)
                                        showingModal.value = false
                                    }
                                )
                                showingModal.value = true
                            }
                        ) {
                            Text(
                                text = "Change"
                            )
                        }
                    }

                    Spacer(Modifier.size(16.dp))
                     */
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row {
                                Text(
                                    text = "Language: "
                                )
                                Spacer(Modifier.size(16.dp))
                                Text(
                                    text = state.currentLanguage.toString(),
                                    style = Typography.get().bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                pickerModel.value = PickerModel(
                                    "Choose Language",
                                    null,
                                    AvailableLanguages.toPickerList { language ->
                                        viewModel.onSelectedLanguage(language)
                                        showingModal.value = false
                                    }
                                )
                                showingModal.value = true
                            }
                        ) {
                            Text(
                                text = "Change"
                            )
                        }
                    }

                    Spacer(Modifier.size(16.dp))

                    LabelledSwitch(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                        label = "Mocked Content",
                        checked = state.isMockedContentChecked
                    ) {
                        viewModel.onMockedContentCheckChanged(!state.isMockedContentChecked)
                    }

                    Spacer(Modifier.size(16.dp))

                    LabelledSwitch(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                        label = "Mocked User",
                        checked = state.isMockedUserChecked
                    ) {

                    }
                }
            }
            if (showingModal.value) {
                Picker(
                    modifier = Modifier.padding(bottom = 0.dp),
                    pickerModel = pickerModel.value
                ) { showingModal.value = false }
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

private fun AvailableLanguages.Companion.toPickerList(action: (AvailableLanguages) -> Unit): List<PickerOption> {
    val pickerList = mutableListOf<PickerOption>()
    this.languages.map {
        pickerList.add(
            PickerOption(it.name, null) {
                action.invoke(it)
            }
        )
    }
    return pickerList
}

private fun ServerEnvironment.Companion.toPickerList(action: (Environment) -> Unit): List<PickerOption> {
    val pickerList = mutableListOf<PickerOption>()
    this.environments.map {
        pickerList.add(
            PickerOption(it.name, null) {
                action.invoke(it)
            }
        )
    }
    return pickerList
}
