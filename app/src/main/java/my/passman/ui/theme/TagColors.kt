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

// One mid tone per color, shared by both themes. Each has a relative luminance of ~0.175, which
// sits halfway between the light (#F6F8FC) and the navy (#0F1B2D) backgrounds and still gives
// white labels a 4.67:1 contrast (WCAG AA).
private val TagFills =
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

/**
 * Colors of this tag color. They are the same in light and dark themes.
 *
 * [TagColor.DEFAULT] means "no color": a transparent fill, so callers draw it outlined. Any
 * tinted fill would be mistaken for one of the palette colors.
 */
@Composable
fun TagColor.scheme(): TagColorScheme =
    if (this == TagColor.DEFAULT) {
        TagColorScheme(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        TagColorScheme(TagFills.getValue(this), Color.White)
    }

/** Color for small marks such as dots; transparent for [TagColor.DEFAULT], drawn outlined. */
val TagColor.accent: Color
    get() = TagFills[this] ?: Color.Transparent
