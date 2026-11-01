package my.passman.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import my.passman.data.TagColor

/** Fill of a tag and the color of its label on top of that fill. */
@Immutable
data class TagColorScheme(
    val container: Color,
    val content: Color,
)

// One mid tone per color, shared by both themes, for small marks such as dots. Each has a relative
// luminance of ~0.175, halfway between the light (#F6F8FC) and the navy (#0F1B2D) backgrounds,
// so it stands out on either.
private val TagAccents =
    mapOf(
        TagColor.RED to Color(0xFFC54C46),
        TagColor.ORANGE to Color(0xFFB35D1C),
        TagColor.YELLOW to Color(0xFF8D7104),
        TagColor.GREEN to Color(0xFF418243),
        TagColor.TEAL to Color(0xFF228276),
        TagColor.BLUE to Color(0xFF3B73CC),
        TagColor.PURPLE to Color(0xFF835DD4),
        TagColor.PINK to Color(0xFFBE4B81),
        TagColor.GRAY to Color(0xFF6A7587),
    )

// Containers in the accent's hue, picked by CIELAB lightness: a light fill (L* 80) with a near-black
// label (L* 10) in the light theme, a medium fill (L* 42) with an almost white label (L* 95) in the
// dark one. Labels have at least 9.5:1 and 5.3:1 contrast respectively (WCAG AA).
private val LightTagSchemes =
    mapOf(
        TagColor.RED to TagColorScheme(Color(0xFFFFB4AA), Color(0xFF3D0600)),
        TagColor.ORANGE to TagColorScheme(Color(0xFFFAB88E), Color(0xFF2D1601)),
        TagColor.YELLOW to TagColorScheme(Color(0xFFDFC483), Color(0xFF231B01)),
        TagColor.GREEN to TagColorScheme(Color(0xFF9ED49B), Color(0xFF052101)),
        TagColor.TEAL to TagColorScheme(Color(0xFF69D9C8), Color(0xFF00201C)),
        TagColor.BLUE to TagColorScheme(Color(0xFFB3C5FE), Color(0xFF011B3D)),
        TagColor.PURPLE to TagColorScheme(Color(0xFFD5BBFC), Color(0xFF20143C)),
        TagColor.PINK to TagColorScheme(Color(0xFFFFB0CF), Color(0xFF3C0221)),
        TagColor.GRAY to TagColorScheme(Color(0xFFB7C2D3), Color(0xFF171C23)),
    )

private val DarkTagSchemes =
    mapOf(
        TagColor.RED to TagColorScheme(Color(0xFF9B4B45), Color(0xFFFFEDEA)),
        TagColor.ORANGE to TagColorScheme(Color(0xFF8E552E), Color(0xFFFFEDE4)),
        TagColor.YELLOW to TagColorScheme(Color(0xFF766121), Color(0xFFFBF0DA)),
        TagColor.GREEN to TagColorScheme(Color(0xFF396F3A), Color(0xFFE4F6E2)),
        TagColor.TEAL to TagColorScheme(Color(0xFF057065), Color(0xFFD6F8F1)),
        TagColor.BLUE to TagColorScheme(Color(0xFF4462A0), Color(0xFFEDF0FF)),
        TagColor.PURPLE to TagColorScheme(Color(0xFF6E5895), Color(0xFFF5EEFF)),
        TagColor.PINK to TagColorScheme(Color(0xFF99486C), Color(0xFFFFECF3)),
        TagColor.GRAY to TagColorScheme(Color(0xFF575F6B), Color(0xFFEDF1F8)),
    )

/**
 * Colors of this tag color for the current light or dark theme.
 *
 * [TagColor.DEFAULT] means "no color": a transparent fill, so callers draw it outlined. Any
 * tinted fill would be mistaken for one of the palette colors.
 */
@Composable
fun TagColor.scheme(): TagColorScheme =
    if (this == TagColor.DEFAULT) {
        TagColorScheme(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        (if (LocalDarkTheme.current) DarkTagSchemes else LightTagSchemes).getValue(this)
    }

/** Color for small marks such as dots; transparent for [TagColor.DEFAULT], drawn outlined. */
val TagColor.accent: Color
    get() = TagAccents[this] ?: Color.Transparent
