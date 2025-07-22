@file:OptIn(ExperimentalMaterial3Api::class)

package com.llamatik.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.localization.Localization
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography

class DisclaimerScreen : Screen {
    private val localization = getCurrentLocalization()

    @Composable
    override fun Content() {
        LlamatikTheme {
            val currentNavigator = LocalNavigator.currentOrThrow

            AboutView(localization) {
                currentNavigator.pop()
            }
        }
    }

    @Composable
    fun AboutView(localization: Localization, onClose: () -> Unit) {
        val scrollState = rememberScrollState()

        BoxWithConstraints(Modifier.fillMaxSize(), propagateMinConstraints = true) {
            Scaffold(
                topBar = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { onClose.invoke() }) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowBack,
                                        contentDescription = null
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = localization.onboardingPromoTitle1,
                                    style = Typography.get().titleMedium
                                )
                            },
                            colors = TopAppBarDefaults.mediumTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Spacer(
                        modifier = Modifier.fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.colorScheme.surfaceDim)
                    )

                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                        text = "Developed by Ferran Pons",
                        style = Typography.get().headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                        text = "",
                        style = Typography.get().bodyMedium,
                        fontWeight = FontWeight.Normal,
                    )

                    Spacer(modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
