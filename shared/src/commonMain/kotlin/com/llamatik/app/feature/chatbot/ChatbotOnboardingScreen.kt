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

class ChatbotOnboardingScreen(private val onAccept: () -> Unit) : Screen {

    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        LlamatikTheme {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
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
                    text = "\uD83D\uDC68\uD83C\uDFFB\u200D✈\uFE0F WELCOME TO Llamatik AI!",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = "Llamatik AI (Guy In Back Specialist Artificial Intelligence) is an experimental local assistant for DCS World pilots, designed to help you quickly find information from manuals, guides, and community websites — all without needing an internet connection.\n" +
                            "\n" +
                            "It can help you:\n" +
                            "Look up aircraft systems, weapons, procedures, and mission planning tips.\n" +
                            "Summarize complex documentation.\n" +
                            "Answer common questions from DCS World reference materials.\n" +
                            "\n" +
                            "\uD83D\uDD10 Privacy Notice\n\n" +
                            "Your privacy is fully protected. The chatbot runs entirely on your device.\n" +
                            "It does not collect, store, or share any personal data.\n" +
                            "No information is sent to external servers.\n" +
                            "\n" +
                            "This tool is based on publicly available content and is not affiliated with Eagle Dynamics.\n" +
                            "\n" +
                            "By continuing, you accept that Llamatik AI chatbot is provided for educational and informational purposes only, and complies with global privacy laws including GDPR, CCPA, and LGPD.\n" +
                            "\n",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
                        text = "Continue",
                        style = Typography.get().titleMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}