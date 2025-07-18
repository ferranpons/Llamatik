package com.dcshub.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Typography {
    @Composable
    fun get() = androidx.compose.material3.Typography(
        displayLarge = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        displayMedium = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        displaySmall = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        headlineLarge = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.W700,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.W700,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        titleSmall = TextStyle(
            fontWeight = FontWeight.W500,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        bodySmall = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.W400,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            fontFamily = Fonts.poppinsFamily()
        ),
        labelSmall = TextStyle(
            fontFamily = Fonts.poppinsFamily(),
            fontWeight = FontWeight.W500,
            fontSize = 10.sp,
            lineHeight = 16.sp
        ),
    )
}
