package my.passman.ui.screens.pin

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.R

@Composable
fun PinScreen(
    viewModel: PinViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.mode == PinMode.SET || state.mode == PinMode.CONFIRM) {
        onBack()
    }

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onSuccess()
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold { padding ->
        if (isLandscape) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PinHeader(state = state)
                PinKeypad(state = state, viewModel = viewModel, buttonSize = 64.dp)
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                PinHeader(state = state)
                PinKeypad(state = state, viewModel = viewModel, buttonSize = 80.dp)
            }
        }
    }
}

@Composable
private fun PinHeader(state: PinScreenState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text =
                when (state.mode) {
                    PinMode.SET -> stringResource(R.string.pin_title_set)
                    PinMode.CONFIRM -> stringResource(R.string.pin_title_confirm)
                    PinMode.UNLOCK -> stringResource(R.string.pin_title_unlock)
                },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotCount =
                when (state.mode) {
                    PinMode.SET -> 6
                    PinMode.CONFIRM -> state.expectedLength
                    PinMode.UNLOCK -> state.pin.length
                }
            repeat(dotCount) { index ->
                val isVisible = state.mode != PinMode.SET || index < state.pin.length.coerceAtLeast(4)
                if (isVisible) {
                    val isFilled = index < state.pin.length
                    PinDot(isFilled = isFilled, isError = state.error != null)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val errorMessage =
                when (state.error) {
                    "mismatch" -> stringResource(R.string.pin_error_mismatch)
                    "invalid" -> stringResource(R.string.pin_error_invalid)
                    else -> state.error ?: ""
                }
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PinKeypad(
    state: PinScreenState,
    viewModel: PinViewModel,
    buttonSize: Dp,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val digits =
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "delete"),
            )

        digits.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { item ->
                    when (item) {
                        "" -> {
                            if (state.mode == PinMode.SET && state.pin.length >= 4) {
                                IconButton(
                                    onClick = viewModel::onConfirmClick,
                                    modifier = Modifier.size(buttonSize),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.save),
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(buttonSize))
                            }
                        }
                        "delete" -> {
                            IconButton(
                                onClick = viewModel::onDeleteClick,
                                modifier = Modifier.size(buttonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = { viewModel.onDigitClick(item) },
                                modifier = Modifier.size(buttonSize),
                                shape = MaterialTheme.shapes.extraLarge,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDot(
    isFilled: Boolean,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier.size(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color =
            when {
                isError -> MaterialTheme.colorScheme.error
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
    ) {}
}
