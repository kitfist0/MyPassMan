package my.passman.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import my.passman.data.TagColor

/** Fill of a tag and the color of its label on top of that fill. */
@Immutable
data class TagColorScheme(
    val container: Color,
    val content: Color,
)

// Light mode: pale fills with dark labels. Dark mode: muted fills that sit well on the navy
// background, with light labels.
private val LightTagColors =
    mapOf(
        TagColor.RED to TagColorScheme(Color(0xFFFFDAD6), Color(0xFF410002)),
        TagColor.ORANGE to TagColorScheme(Color(0xFFFFDCC2), Color(0xFF2E1500)),
        TagColor.YELLOW to TagColorScheme(Color(0xFFFFEFA8), Color(0xFF231B00)),
        TagColor.GREEN to TagColorScheme(Color(0xFFC8F0C4), Color(0xFF002106)),
        TagColor.TEAL to TagColorScheme(Color(0xFFB4F0E5), Color(0xFF00201B)),
        TagColor.BLUE to TagColorScheme(Color(0xFFD6E3FF), Color(0xFF001B3E)),
        TagColor.PURPLE to TagColorScheme(Color(0xFFEBDDFF), Color(0xFF250059)),
        TagColor.PINK to TagColorScheme(Color(0xFFFFD8E8), Color(0xFF3B0726)),
        TagColor.GRAY to TagColorScheme(Color(0xFFE0E3E8), Color(0xFF191C20)),
    )

private val DarkTagColors =
    mapOf(
        TagColor.RED to TagColorScheme(Color(0xFF7A2A26), Color(0xFFFFDAD6)),
        TagColor.ORANGE to TagColorScheme(Color(0xFF74390C), Color(0xFFFFDCC2)),
        TagColor.YELLOW to TagColorScheme(Color(0xFF5C4A00), Color(0xFFFFE17A)),
        TagColor.GREEN to TagColorScheme(Color(0xFF1F5124), Color(0xFFC8F0C4)),
        TagColor.TEAL to TagColorScheme(Color(0xFF005046), Color(0xFF9CF2E2)),
        TagColor.BLUE to TagColorScheme(Color(0xFF284777), Color(0xFFD6E3FF)),
        TagColor.PURPLE to TagColorScheme(Color(0xFF523F83), Color(0xFFEBDDFF)),
        TagColor.PINK to TagColorScheme(Color(0xFF723357), Color(0xFFFFD8E8)),
        TagColor.GRAY to TagColorScheme(Color(0xFF3A4250), Color(0xFFDDE3EC)),
    )

/**
 * Colors of this tag color in the current theme. Light or dark is taken from the active
 * [MaterialTheme] rather than the system, so a theme forced in the app settings is respected.
 *
 * [TagColor.DEFAULT] means "no color": a transparent fill, so callers draw it outlined. Any
 * tinted fill would be mistaken for one of the palette colors.
 */
@Composable
fun TagColor.scheme(): TagColorScheme {
    val colorScheme = MaterialTheme.colorScheme
    if (this == TagColor.DEFAULT) {
        return TagColorScheme(Color.Transparent, colorScheme.onSurfaceVariant)
    }
    val isDark = colorScheme.background.luminance() < 0.5f
    return (if (isDark) DarkTagColors else LightTagColors).getValue(this)
}

// Saturated mid tones for small marks such as color dots, which stay visible on both light and
// dark surfaces where the pale light-theme fills would disappear.
private val TagAccents =
    mapOf(
        TagColor.RED to Color(0xFFE5534B),
        TagColor.ORANGE to Color(0xFFF08A3C),
        TagColor.YELLOW to Color(0xFFD4A900),
        TagColor.GREEN to Color(0xFF4CAF50),
        TagColor.TEAL to Color(0xFF26B5A3),
        TagColor.BLUE to Color(0xFF4C8DF6),
        TagColor.PURPLE to Color(0xFF9B72F2),
        TagColor.PINK to Color(0xFFE26BA3),
        TagColor.GRAY to Color(0xFF8A93A3),
    )

/** Accent of this tag color for small marks; transparent for [TagColor.DEFAULT], drawn outlined. */
val TagColor.accent: Color
    get() = TagAccents[this] ?: Color.Transparent
