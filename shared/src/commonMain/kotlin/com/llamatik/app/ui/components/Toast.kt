package com.llamatik.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.llamatik.app.ui.theme.Typography
import kotlinx.coroutines.delay

const val TOAST_DURATION = 3000L

sealed interface ToastState {
    data object Hidden : ToastState
    class Shown(
        val message: String,
        val containerColor: Color? = null,
        val onContainerColor: Color? = null
    ) : ToastState
}

@Composable
fun Toast(
    state: MutableState<ToastState>
) {
    val value = state.value
    if (value is ToastState.Shown) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 100.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.size(320.dp, 50.dp),
                color = value.containerColor ?: MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = value.message,
                        color = value.onContainerColor
                            ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        style = Typography.get().bodyMedium
                    )
                }
                LaunchedEffect(value.message) {
                    delay(TOAST_DURATION)
                    state.value = ToastState.Hidden
                }
            }
        }
    }
}
