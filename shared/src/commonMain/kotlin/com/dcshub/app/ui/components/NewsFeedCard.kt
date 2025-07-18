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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dcshub.app.data.repositories.FeedItem
import com.dcshub.app.platform.shimmerLoadingAnimation
import com.dcshub.app.resources.Res
import com.dcshub.app.resources.llamatik_icon_logo
import com.dcshub.app.ui.icon.LlamatikIcons
import com.dcshub.app.ui.theme.Typography
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun NewsFeedCard(
    feedItem: FeedItem,
    onClick: () -> Unit
) {
    val imageHeight = 160.dp
    val roundedCornerSize = 10.dp

    Card(
        onClick = { onClick() },
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
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
                    contentDescription = "image",
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
                                contentDescription = "image",
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
                    painter = painterResource(Res.drawable.llamatik_icon_logo),
                    contentScale = ContentScale.Inside,
                    contentDescription = "",
                )
            }
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = feedItem.title,
                style = Typography.get().titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = feedItem.pubDate,
                style = Typography.get().bodySmall,
            )
            Text(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                text = feedItem.description.toRichHtmlString(),
                style = Typography.get().bodyMedium
            )
        }
    }
}

@Composable
fun String.toRichHtmlString(): AnnotatedString {
    val state = rememberRichTextState()

    LaunchedEffect(this) {
        state.setHtml(this@toRichHtmlString)
    }

    return state.annotatedString
}
