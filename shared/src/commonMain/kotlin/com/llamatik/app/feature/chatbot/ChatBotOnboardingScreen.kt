package com.llamatik.app.feature.chatbot

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.a_pair_of_llamas_in_a_field_with_clouds_and_mounta
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.jetbrains.compose.resources.painterResource

class ChatBotOnboardingScreen(private val onAccept: () -> Unit) : Screen {

    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        LlamatikTheme {
            val scrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var sizeImage by remember { mutableStateOf(IntSize.Zero) }
                val gradient = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    startY = sizeImage.height.toFloat() / 3,
                    endY = sizeImage.height.toFloat()
                )

                Box {
                    Image(
                        modifier = Modifier.fillMaxWidth().height(140.dp).onGloballyPositioned {
                            sizeImage = it.size
                        },
                        contentScale = ContentScale.FillWidth,
                        painter = painterResource(Res.drawable.a_pair_of_llamas_in_a_field_with_clouds_and_mounta),
                        contentDescription = null
                    )
                    Box(modifier = Modifier.matchParentSize().background(gradient))
                }
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "\uD83E\uDD99\n${localization.welcome}",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = localization.onboardingMainText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Button(
                    onClick = { onAccept.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(50.dp)
                        .padding(start = 16.dp, end = 16.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(10)
                ) {
                    Text(
                        text = localization.actionContinue,
                        style = Typography.get().titleMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}