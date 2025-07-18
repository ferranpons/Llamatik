@file:OptIn(ExperimentalMaterial3Api::class)

package com.dcshub.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dcshub.app.data.repositories.FeedItem
import com.dcshub.app.platform.shimmerLoadingAnimation
import com.dcshub.app.resources.Res
import com.dcshub.app.resources.unofficial_dcs_companion_logo
import com.dcshub.app.ui.icon.LlamatikIcons
import com.dcshub.app.ui.theme.Typography
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun NewsCardSmall(
    feedItem: FeedItem,
    width: Dp,
    height: Dp,
    onClick: () -> Unit
) {
    val imageHeight = 70.dp
    val roundedCornerSize = 10.dp

    Card(
        onClick = { onClick() },
        modifier = Modifier.size(width, height).padding(start = 16.dp, top = 16.dp),
        shape = RoundedCornerShape(roundedCornerSize),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ),
        interactionSource = remember { MutableInteractionSource() }
    ) {
        Column {
            if (feedItem.enclosure?.url != null) {
                KamelImage(
                    resource = asyncPainterResource(data = feedItem.enclosure.url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(roundedCornerSize))
                        .height(imageHeight),
                    onLoading = {
                        Box(
                            modifier = Modifier
                                .clip(shape = RoundedCornerShape(roundedCornerSize))
                                .background(color = MaterialTheme.colorScheme.primaryContainer)
                                .height(imageHeight)
                                .fillMaxWidth()
                                .shimmerLoadingAnimation(isLoadingCompleted = false)
                        )
                    },
                    onFailure = {
                        Box(
                            modifier = Modifier
                                .clip(shape = RoundedCornerShape(roundedCornerSize))
                                .background(color = MaterialTheme.colorScheme.primaryContainer)
                                .height(imageHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                modifier = Modifier.size(36.dp),
                                imageVector = LlamatikIcons.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                )
            } else {
                Image(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(roundedCornerSize))
                        .height(imageHeight)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    painter = painterResource(Res.drawable.unofficial_dcs_companion_logo),
                    contentScale = ContentScale.Inside,
                    contentDescription = null,
                )
            }
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = feedItem.title,
                style = Typography.get().titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = feedItem.pubDate,
                style = Typography.get().labelSmall,
            )
            Text(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = feedItem.description.toRichHtmlString(),
                style = Typography.get().bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
