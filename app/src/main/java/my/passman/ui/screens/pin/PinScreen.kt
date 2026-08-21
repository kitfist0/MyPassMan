package my.passman.ui.screens.pin

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.R

@Composable
fun PinScreen(
    viewModel: PinViewModel,
    onSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onSuccess()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Title and Indicators
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (state.mode) {
                        PinMode.SET -> stringResource(R.string.pin_title_set)
                        PinMode.CONFIRM -> stringResource(R.string.pin_title_confirm)
                        PinMode.UNLOCK -> stringResource(R.string.pin_title_unlock)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < state.pin.length
                        PinDot(isFilled = isFilled, isError = state.error != null)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val errorMessage = when (state.error) {
                        "mismatch" -> stringResource(R.string.pin_error_mismatch)
                        "invalid" -> stringResource(R.string.pin_error_invalid)
                        else -> state.error ?: ""
                    }
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val digits = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "delete")
                )

                digits.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { item ->
                            when (item) {
                                "" -> Spacer(modifier = Modifier.size(80.dp))
                                "delete" -> {
                                    IconButton(
                                        onClick = viewModel::onDeleteClick,
                                        modifier = Modifier.size(80.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = stringResource(R.string.delete),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                else -> {
                                    FilledTonalButton(
                                        onClick = { viewModel.onDigitClick(item) },
                                        modifier = Modifier.size(80.dp),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = item,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDot(isFilled: Boolean, isError: Boolean) {
    Surface(
        modifier = Modifier.size(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = when {
            isError -> MaterialTheme.colorScheme.error
            isFilled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {}
}
