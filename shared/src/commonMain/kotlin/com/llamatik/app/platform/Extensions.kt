package com.llamatik.app.platform

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

private const val EMAIL_FORMAT_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$"

fun String.isValidEmail(): Boolean {
    return this.matches(EMAIL_FORMAT_REGEX.toRegex())
}

fun Modifier.shimmerLoadingAnimation(
    isLoadingCompleted: Boolean = true,
    isLightModeActive: Boolean = true,
    widthOfShadowBrush: Int = 500,
    angleOfAxisY: Float = 270f,
    durationMillis: Int = 1000
): Modifier {
    if (isLoadingCompleted) {
        return this
    } else {
        return composed {
            val shimmerColors = ShimmerAnimationData(isLightMode = isLightModeActive).getColours()

            val transition = rememberInfiniteTransition(label = "")

            val translateAnimation = transition.animateFloat(
                initialValue = 0f,
                targetValue = (durationMillis + widthOfShadowBrush).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = durationMillis,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "Shimmer loading animation"
            )

            this.background(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(x = translateAnimation.value - widthOfShadowBrush, y = 0.0f),
                    end = Offset(x = translateAnimation.value, y = angleOfAxisY)
                )
            )
        }
    }
}

data class ShimmerAnimationData(
    private val isLightMode: Boolean
) {
    fun getColours(): List<Color> {
        return if (isLightMode) {
            val color = Color.White

            listOf(
                color.copy(alpha = 0.3f),
                color.copy(alpha = 0.5f),
                color.copy(alpha = 1.0f),
                color.copy(alpha = 0.5f),
                color.copy(alpha = 0.3f)
            )
        } else {
            val color = Color.Black

            listOf(
                color.copy(alpha = 0.0f),
                color.copy(alpha = 0.3f),
                color.copy(alpha = 0.5f),
                color.copy(alpha = 0.3f),
                color.copy(alpha = 0.0f)
            )
        }
    }
}

fun String.toLlamatikURL(): String {
    return "https://www.llamatik.com/$this"
}

@OptIn(ExperimentalTime::class)
fun String.formatRssPubDateToLocalDate(): String {
    if (this.isBlank()) return ""

    // Example: "Wed, 11 Feb 2026 00:00:00 -0500"
    // Tokens: [Wed, 11, Feb, 2026, 00:00:00, -0500]
    val tokens = this.replace(",", "").trim().split(Regex("\\s+"))
    if (tokens.size < 6) return this

    val day = tokens[1].toIntOrNull() ?: return this
    val monthStr = tokens[2]
    val year = tokens[3].toIntOrNull() ?: return this

    val timeParts = tokens[4].split(":")
    val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
    val second = timeParts.getOrNull(2)?.toIntOrNull() ?: 0

    val offset = parseOffsetToMinutes(tokens[5]) ?: return this

    // Build an Instant from the provided offset datetime.
    // Convert "local in offset" -> UTC instant:
    // epochSeconds = UTC(year-month-day hour:min:sec) - offsetSeconds
    val epochSecondsUtc = epochSecondsAtUtc(year, monthToNumber(monthStr) ?: return this, day, hour, minute, second) -
            (offset * 60L)

    val instant = Instant.fromEpochSeconds(epochSecondsUtc)

    // Convert to device timezone and format date only
    val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

    // "localized" month names without java.text is tricky in common code.
    // Use numeric format which is universally readable: dd/MM/yyyy or yyyy-MM-dd
    // If you prefer, we can provide localized month names via your Localization layer.
    val dd = localDate.dayOfMonth.toString().padStart(2, '0')
    val mm = localDate.monthNumber.toString().padStart(2, '0')
    val yyyy = localDate.year.toString()

    // Example output: 11/02/2026
    return "$dd/$mm/$yyyy"
}

private fun monthToNumber(mon: String): Int? = when (mon.lowercase()) {
    "jan" -> 1
    "feb" -> 2
    "mar" -> 3
    "apr" -> 4
    "may" -> 5
    "jun" -> 6
    "jul" -> 7
    "aug" -> 8
    "sep" -> 9
    "oct" -> 10
    "nov" -> 11
    "dec" -> 12
    else -> null
}

private fun parseOffsetToMinutes(offset: String): Int? {
    // -0500, +0130
    if (offset.length != 5) return null
    val sign = when (offset[0]) {
        '+' -> 1
        '-' -> -1
        else -> return null
    }
    val hh = offset.substring(1, 3).toIntOrNull() ?: return null
    val mm = offset.substring(3, 5).toIntOrNull() ?: return null
    return sign * (hh * 60 + mm)
}

/**
 * Compute epoch seconds for a UTC date-time without java.time.
 * Uses a civil-date to days-since-epoch algorithm (Gregorian calendar).
 */
private fun epochSecondsAtUtc(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int
): Long {
    val days = daysSinceUnixEpoch(year, month, day)
    return days * 86_400L + hour * 3_600L + minute * 60L + second
}

/**
 * Days since 1970-01-01 (Unix epoch), Gregorian proleptic calendar.
 * Based on Howard Hinnant's civil_from_days / days_from_civil.
 */
private fun daysSinceUnixEpoch(year: Int, month: Int, day: Int): Long {
    var y = year
    var m = month
    val d = day

    y -= if (m <= 2) 1 else 0
    val era = floorDiv(y, 400)
    val yoe = y - era * 400
    m += if (m > 2) -3 else 9
    val doy = (153 * m + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return (era * 146097L + doe - 719468L)
}

private fun floorDiv(a: Int, b: Int): Int {
    var r = a / b
    if ((a xor b) < 0 && a % b != 0) r -= 1
    return r
}
