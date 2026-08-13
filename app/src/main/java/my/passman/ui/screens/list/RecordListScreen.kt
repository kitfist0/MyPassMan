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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp),
                        placeholder = { Text("Search by name...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.imePadding(),
                onClick = onAddRecord
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        if (state.records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (state.searchQuery.isEmpty()) "No records yet" else "No matches found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = { onEditRecord(record.id) }
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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        ClipboardUtils.copyToClipboard(
                            context = context,
                            text = record.secret,
                            label = "password",
                            isSensitive = true,
                            toastMessage = "Password copied to clipboard"
                        )
                    }
                )
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            with(sharedTransitionScope) {
                Text(
                    text = record.name,
                    modifier = Modifier.sharedElement(
                        rememberSharedContentState(key = "name-${record.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "*".repeat(record.secret.length),
                    modifier = Modifier.sharedElement(
                        rememberSharedContentState(key = "secret-${record.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            with(sharedTransitionScope) {
                Text(
                    text = record.comment,
                    modifier = Modifier.sharedElement(
                        rememberSharedContentState(key = "comment-${record.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
