package my.passman.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import my.passman.R

@Composable
fun HintBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Info,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Without a dismiss button the end padding matches the start, not the button's slot;
                    // with an action button below, the button's own padding provides the bottom gap.
                    .padding(
                        start = 16.dp,
                        top = 12.dp,
                        bottom = if (actionLabel != null) 0.dp else 12.dp,
                        end = if (onDismiss != null) 4.dp else 16.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = textStyle,
                color = contentColor,
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = contentColor,
                    )
                }
            }
        }
        if (actionLabel != null) {
            TextButton(
                onClick = onAction,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .padding(end = 8.dp, bottom = 4.dp),
            ) {
                Text(text = actionLabel, color = contentColor)
            }
        }
    }
}
