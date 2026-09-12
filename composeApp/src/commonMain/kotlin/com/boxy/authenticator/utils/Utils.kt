package com.boxy.authenticator.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boxy.authenticator.domain.models.TokenEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

object Utils {
    fun String.toColor(): Color {
        val hexString = this.removePrefix("#")
        return when (hexString.length) {
            6 -> Color(
                red = hexString.substring(0, 2).toInt(16) / 255f,
                green = hexString.substring(2, 4).toInt(16) / 255f,
                blue = hexString.substring(4, 6).toInt(16) / 255f,
                alpha = 1f
            )
            8 -> Color(
                alpha = hexString.substring(0, 2).toInt(16) / 255f,
                red = hexString.substring(2, 4).toInt(16) / 255f,
                green = hexString.substring(4, 6).toInt(16) / 255f,
                blue = hexString.substring(6, 8).toInt(16) / 255f
            )
            else -> throw IllegalArgumentException("Invalid hex color: $this")
        }
    }
}

fun String.getInitials(): String {
    if (isEmpty()) return "?"
    val words = trim().split("\\s+".toRegex())
    var initials = ""

    for (i in 0 until minOf(words.size, 2)) {
        initials += words[i][0]
    }

    return initials.uppercase()
}

fun String.cleanSecretKey(): String {
    return this.filterNot { it.isWhitespace() || it == '=' }.uppercase()
}

fun Modifier.moveRight(dp: Dp = 0.dp) = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(
        placeable.width - dp.toPx().toInt(),
        placeable.height
    ) { placeable.placeRelative(0, 0) }
}

val TokenEntry.name: String
    get() {
        if (label.isEmpty()) return issuer
        return "$issuer ($label)"
    }

fun formatMillis(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    return date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " +
            date.day.toString().padStart(2, '0') + ", " +
            date.year +
            " " +
            date.hour.toString().padStart(2, '0') + ":" +
            date.minute.toString().padStart(2, '0') + ":" +
            date.second.toString().padStart(2, '0')
}
