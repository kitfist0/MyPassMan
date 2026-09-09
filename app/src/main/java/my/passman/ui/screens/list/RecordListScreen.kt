package my.passman.ui.screens.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun closeSearch() {
        focusManager.clearFocus()
        viewModel.onCloseSearch()
    }

    BackHandler(enabled = state.isSearchActive) {
        closeSearch()
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = state.isSearchActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                LaunchedEffect(Unit) {
                    searchFocusRequester.requestFocus()
                }
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
                                    .padding(16.dp)
                                    .focusRequester(searchFocusRequester),
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                        )
                        IconButton(
                            onClick = { closeSearch() },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.records.isEmpty()) {
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            // When the search bar is visible, Scaffold's own padding already
                            // reserves its full height (which includes the status bar inset).
                            top = (if (state.isSearchActive) 0.dp else systemBarsPadding.calculateTopPadding()) + 16.dp,
                            bottom = systemBarsPadding.calculateBottomPadding() + 96.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            if (!state.isSearchActive) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            )

            AnimatedVisibility(
                visible = !state.isSearchActive,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onNavigateToSettings,
                                modifier = Modifier.size(56.dp),
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_description))
                            }
                            IconButton(
                                onClick = { viewModel.onSearchClick() },
                                modifier = Modifier.size(56.dp),
                            ) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_description))
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = onAddRecord,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_description))
                    }
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
