package my.passman.ui.screens.list

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.passman.R
import my.passman.data.Record
import my.passman.util.ClipboardUtils

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecordListScreen(
    viewModel: RecordListViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAddRecord: () -> Unit,
    onEditRecord: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(16.dp),
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_description))
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.imePadding(),
                onClick = onAddRecord,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_description))
            }
        },
    ) { padding ->
        if (state.records.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text =
                            if (state.searchQuery.isEmpty()) {
                                stringResource(
                                    R.string.no_records,
                                )
                            } else {
                                stringResource(R.string.no_matches)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.records, key = { it.record.id }) { item ->
                    RecordCard(
                        record = item.record,
                        tagName = item.tag?.name,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = { onEditRecord(item.record.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecordCard(
    record: Record,
    tagName: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val passwordLabel = stringResource(R.string.clipboard_password_label)
    val passwordCopiedToast = stringResource(R.string.password_copied)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            ClipboardUtils.copyToClipboard(
                                context = context,
                                text = record.secret,
                                label = passwordLabel,
                                isSensitive = true,
                                toastMessage = passwordCopiedToast,
                            )
                        },
                    ).padding(16.dp)
                    .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                with(sharedTransitionScope) {
                    Text(
                        text = record.name,
                        modifier =
                            Modifier
                                .fillMaxWidth(0.7f)
                                .sharedElement(
                                    rememberSharedContentState(key = "name-${record.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (record.login.isNotBlank()) {
                        Text(
                            text = record.login,
                            modifier =
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "login-${record.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "*".repeat(record.secret.length),
                        modifier =
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "secret-${record.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                with(sharedTransitionScope) {
                    Text(
                        text = record.comment,
                        modifier =
                            Modifier.sharedElement(
                                rememberSharedContentState(key = "comment-${record.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (tagName != null) {
                SuggestionChip(
                    onClick = onClick,
                    label = {
                        Text(
                            text = tagName,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}
