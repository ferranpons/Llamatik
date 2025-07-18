package com.llamatik.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.llamatik.app.ui.theme.Typography

@Composable
fun Picker(
    modifier: Modifier,
    pickerModel: PickerModel,
    onClose: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable { onClose.invoke() },
        contentAlignment = Alignment.BottomCenter
    ) {
        PickerScaffold(pickerModel.title, pickerModel.description, pickerModel.options, onClose)
    }
}

@Composable
internal fun PickerScaffold(
    title: String,
    description: String?,
    options: List<PickerOption>,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable { onClose.invoke() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .wrapContentSize(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Gray)
            )
        }
        Text(
            text = title,
            style = Typography.get().headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(16.dp)
        )
        description?.let {
            Text(
                text = title,
                style = Typography.get().bodyMedium,
                modifier = Modifier
                    .padding(start = 16.dp)
            )
        }

        options.map {
            PickerItem(it.text, it.icon, it.action)
        }
    }
}

@Composable
fun PickerItem(text: String, icon: ImageVector?, action: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { action.invoke() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(60.dp)
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                icon?.let {
                    Icon(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterVertically),
                        imageVector = it,
                        contentDescription = null // decorative image
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = text,
                    style = Typography.get().bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

data class PickerModel(
    val title: String,
    val description: String?,
    val options: List<PickerOption>
)

data class PickerOption(val text: String, val icon: ImageVector?, val action: () -> Unit)
