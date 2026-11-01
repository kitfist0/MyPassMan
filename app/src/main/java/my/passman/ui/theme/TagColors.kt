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

// Material 3 style containers in the accent's hue, picked by CIELAB lightness: a pale fill (L* 88)
// with a near-black label (L* 10) in the light theme, a deep fill (L* 30) with a pale label (L* 90)
// in the dark one. Labels have at least 11.9:1 and 7.2:1 contrast respectively (WCAG AAA).
private val LightTagSchemes =
    mapOf(
        TagColor.RED to TagColorScheme(Color(0xFFFFD3CD), Color(0xFF3D0600)),
        TagColor.ORANGE to TagColorScheme(Color(0xFFFED4BB), Color(0xFF2D1601)),
        TagColor.YELLOW to TagColorScheme(Color(0xFFF2DAA8), Color(0xFF231B01)),
        TagColor.GREEN to TagColorScheme(Color(0xFFBEE8BB), Color(0xFF052101)),
        TagColor.TEAL to TagColorScheme(Color(0xFF9AECDE), Color(0xFF00201C)),
        TagColor.BLUE to TagColorScheme(Color(0xFFD3DCFF), Color(0xFF011B3D)),
        TagColor.PURPLE to TagColorScheme(Color(0xFFE6D6FE), Color(0xFF20143C)),
        TagColor.PINK to TagColorScheme(Color(0xFFFFD1E2), Color(0xFF3C0221)),
        TagColor.GRAY to TagColorScheme(Color(0xFFCED8EA), Color(0xFF171C23)),
    )

private val DarkTagSchemes =
    mapOf(
        TagColor.RED to TagColorScheme(Color(0xFF73332E), Color(0xFFFEDAD6)),
        TagColor.ORANGE to TagColorScheme(Color(0xFF683C1C), Color(0xFFFEDCC7)),
        TagColor.YELLOW to TagColorScheme(Color(0xFF554512), Color(0xFFF4E0B9)),
        TagColor.GREEN to TagColorScheme(Color(0xFF255026), Color(0xFFCBEBC8)),
        TagColor.TEAL to TagColorScheme(Color(0xFF025048), Color(0xFFB0EFE4)),
        TagColor.BLUE to TagColorScheme(Color(0xFF2C4677), Color(0xFFDBE2FE)),
        TagColor.PURPLE to TagColorScheme(Color(0xFF4F3E6F), Color(0xFFEBDDFF)),
        TagColor.PINK to TagColorScheme(Color(0xFF71314E), Color(0xFFFED9E7)),
        TagColor.GRAY to TagColorScheme(Color(0xFF404753), Color(0xFFDBE3F1)),
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
