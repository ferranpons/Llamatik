package com.llamatik.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llamatik.app.ui.theme.Typography
import com.llamatik.app.ui.theme.errorContainer
import com.llamatik.app.ui.theme.infoContainer
import com.llamatik.app.ui.theme.successContainer
import com.llamatik.app.ui.theme.warningContainer

private const val DELIMITER = "##"

@Composable
fun ColoredSnackBarHost(
    hostState: SnackbarHostState,
    action: @Composable (() -> Unit)? = null
) {
    SnackbarHost(hostState) {
        val message = it.getSnackbarMessage()
        val color = when (message.type) {
            SnackbarType.SUCCESS -> Pair(successContainer, MaterialTheme.colorScheme.onPrimary)
            SnackbarType.ERROR -> Pair(errorContainer, MaterialTheme.colorScheme.onError)
            SnackbarType.INFO -> Pair(infoContainer, MaterialTheme.colorScheme.onPrimary)
            SnackbarType.WARNING -> Pair(warningContainer, MaterialTheme.colorScheme.onPrimary)
        }

        Snackbar(
            modifier = Modifier.padding(8.dp),
            containerColor = color.first,
            contentColor = color.second,
            action = action
        ) {
            hostState.currentSnackbarData?.let {
                Text(
                    text = message.message,
                    style = Typography.get().bodyMedium
                )
            }
        }
    }
}

suspend fun SnackbarHostState.showSnackbar(
    type: SnackbarType,
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short
): SnackbarResult {
    return showSnackbar(
        SnackbarMessage(type, message).toString(),
        actionLabel,
        withDismissAction,
        duration
    )
}

private data class SnackbarMessage(
    val type: SnackbarType,
    val message: String
) {
    override fun toString(): String {
        return "$type$DELIMITER$message"
    }

    companion object {
        fun fromString(string: String): SnackbarMessage {
            val (type, message) = string.split(DELIMITER)

            return SnackbarMessage(
                type = SnackbarType.valueOf(type),
                message = message
            )
        }
    }
}

private fun SnackbarData.getSnackbarMessage(): SnackbarMessage {
    return SnackbarMessage.fromString(visuals.message)
}

enum class SnackbarType {
    SUCCESS, ERROR, INFO, WARNING
}
