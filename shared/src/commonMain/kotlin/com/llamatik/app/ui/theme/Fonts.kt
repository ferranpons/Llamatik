package com.llamatik.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.llamatik.app.platform.font

object Fonts {
    @Composable
    fun poppinsFamily() = FontFamily(
        font(
            "Poppins Light",
            "poppins_light",
            FontWeight.Light,
            FontStyle.Normal
        ),
        font(
            "Poppins Regular",
            "poppins_regular",
            FontWeight.Normal,
            FontStyle.Normal
        ),
        font(
            "Poppins Italic",
            "poppins_italic",
            FontWeight.Normal,
            FontStyle.Italic
        ),
        font(
            "Poppins Medium",
            "poppins_medium",
            FontWeight.Medium,
            FontStyle.Normal
        ),
        font(
            "Poppins Bold",
            "poppins_bold",
            FontWeight.Bold,
            FontStyle.Normal
        )
    )
}
