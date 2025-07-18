@file:OptIn(ExperimentalFoundationApi::class, ExperimentalFoundationApi::class)

package com.dcshub.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dcshub.app.ui.theme.Typography
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val defaultAction = {}

class OnboardingComponent(
    private val carouselItems: List<CarouselItem>
) {
    @Composable
    fun DrawCarousel() {
        val coroutineScope = rememberCoroutineScope()
        val state = rememberPagerState(
            initialPage = 0,
            initialPageOffsetFraction = 0f,
            pageCount = { carouselItems.size }
        )

        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                Carousel(state, carouselItems)
            }
            Spacer(modifier = Modifier.size(16.dp))
            state.currentPage.let {
                val item = carouselItems[it]
                Button(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    onClick = {
                        if (item.action != defaultAction) {
                            item.action.invoke()
                        } else {
                            coroutineScope.launch { state.animateScrollToPage(state.currentPage + 1) }
                        }
                    }
                ) {
                    Text(
                        modifier = Modifier.padding(4.dp),
                        text = item.actionText,
                        style = Typography.get().bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.size(4.dp))
            DotsIndicator(
                totalDots = carouselItems.size,
                selectedIndex = state.currentPage,
                selectedColor = Color.DarkGray,
                unSelectedColor = Color.Gray
            )
        }
    }

    @Composable
    fun Carousel(state: PagerState, carouselItemsList: List<CarouselItem>) {
        HorizontalPager(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            val item = carouselItemsList[it]
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        painterResource(item.imageResource),
                        "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f)
                            .padding(start = 24.dp, end = 24.dp),
                        contentScale = ContentScale.FillWidth
                    )
                    item.title?.let { title ->
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = title,
                            textAlign = TextAlign.Center,
                            style = Typography.get().headlineMedium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    item.description?.let { description ->
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = description,
                            textAlign = TextAlign.Center,
                            style = Typography.get().bodyLarge
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DotsIndicator(
        totalDots: Int,
        selectedIndex: Int,
        selectedColor: Color,
        unSelectedColor: Color
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            items(totalDots) { index ->
                if (index == selectedIndex) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(unSelectedColor)
                    )
                }

                if (index != totalDots - 1) {
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

data class CarouselItem(
    val imageResource: DrawableResource,
    val title: AnnotatedString?,
    val description: AnnotatedString?,
    val actionText: String,
    val action: () -> Unit = defaultAction
)
