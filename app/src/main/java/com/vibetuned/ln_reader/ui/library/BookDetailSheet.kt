package com.vibetuned.ln_reader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.ui.common.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailSheet(
    book: Book,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onViewImages: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val detail by container.bookRepository.bookDetail(book.id)
        .collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by remember { mutableStateOf(false) }

    // Use the live book (reflects companion changes) and fall back to the snapshot.
    val liveBook = detail?.book ?: book

    val pickEpub = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { container.bookRepository.attachEpub(book.id, uri) }
    }
    val pickSync = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch { container.bookRepository.attachSync(book.id, uri) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                liveBook.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (!liveBook.author.isNullOrBlank()) {
                Text(
                    liveBook.author!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(label = "Duration", value = formatDuration(liveBook.durationMs))
                Stat(label = "Chapters", value = (detail?.chapters?.size ?: 0).toString())
                Stat(label = "Images", value = (detail?.images?.size ?: 0).toString())
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "Companions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            CompanionRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                label = "EPUB",
                attached = liveBook.hasEpub,
                onAdd = { pickEpub.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                onRemove = { scope.launch { container.bookRepository.detachEpub(book.id) } }
            )
            Spacer(Modifier.height(8.dp))
            CompanionRow(
                icon = Icons.Outlined.Sync,
                label = "Sync manifest",
                attached = liveBook.hasSync,
                onAdd = {
                    pickSync.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
                onRemove = { scope.launch { container.bookRepository.detachSync(book.id) } }
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open")
            }
            Spacer(Modifier.height(8.dp))
            if (liveBook.hasEpub) {
                OutlinedButton(
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Read", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
            }
            val hasImages = (detail?.images?.size ?: 0) > 0
            OutlinedButton(
                onClick = onViewImages,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasImages
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View images", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Remove from library", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove book?") },
            text = {
                Text(
                    "This removes \"${liveBook.title}\" from your library and deletes its cached " +
                        "images and companions. The original file on disk is not touched."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CompanionRow(
    icon: ImageVector,
    label: String,
    attached: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (attached) "Attached" else "Not attached",
                style = MaterialTheme.typography.labelSmall,
                color = if (attached) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (attached) {
            TextButton(onClick = onRemove) { Text("Remove") }
        } else {
            TextButton(onClick = onAdd) { Text("Add") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
