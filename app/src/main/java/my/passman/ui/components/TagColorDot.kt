package my.passman.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import my.passman.data.TagColor
import my.passman.ui.theme.accent

/** A small dot in the tag's accent; the uncolored [TagColor.DEFAULT] is drawn as an empty ring. */
@Composable
fun TagColorDot(
    color: TagColor,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val borderColor = if (color == TagColor.DEFAULT) MaterialTheme.colorScheme.outline else color.accent
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(color.accent)
                .border(1.dp, borderColor, CircleShape),
    )
}
