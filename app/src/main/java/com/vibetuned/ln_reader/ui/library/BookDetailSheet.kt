package com.vibetuned.ln_reader.ui.library

import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.outlined.Share
import android.content.Intent
import android.content.ClipData
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.Collection
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
    val collections by container.collectionRepository.collections()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var showNewCollectionName by remember { mutableStateOf(false) }

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
                if (liveBook.hasAudio) {
                    Stat(label = "Duration", value = formatDuration(liveBook.durationMs))
                    Stat(label = "Chapters", value = (detail?.chapters?.size ?: 0).toString())
                    Stat(label = "Images", value = (detail?.images?.size ?: 0).toString())
                } else {
                    Stat(label = "Format", value = "EPUB")
                }
            }

            // Companions belong to an audiobook: for an EPUB-only book the EPUB *is* the book, and
            // a sync manifest has no narration to sync to.
            if (liveBook.hasAudio) {
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
            }

            Spacer(Modifier.height(24.dp))
            // Primary action: play an audiobook, read an EPUB-only book.
            Button(
                onClick = if (liveBook.hasAudio) onPlay else onRead,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!liveBook.hasAudio) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (liveBook.hasAudio) "Open" else "Read")
            }
            Spacer(Modifier.height(8.dp))
            if (liveBook.hasAudio && liveBook.hasEpub) {
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
            if (liveBook.hasAudio) {
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
            }
            ShareBookButton(bookId = liveBook.id, title = liveBook.title)
            Spacer(Modifier.height(8.dp))
            if (liveBook.collectionId == null) {
                OutlinedButton(
                    onClick = { showCollectionPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to collection", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            container.collectionRepository.removeBook(book.id)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove from collection", style = MaterialTheme.typography.labelLarge)
                }
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

    if (showCollectionPicker) {
        CollectionPickerDialog(
            collections = collections,
            onDismiss = { showCollectionPicker = false },
            onPick = { collectionId ->
                showCollectionPicker = false
                scope.launch {
                    container.collectionRepository.addBook(book.id, collectionId)
                    onDismiss()
                }
            },
            onNewCollection = {
                showCollectionPicker = false
                showNewCollectionName = true
            }
        )
    }

    if (showNewCollectionName) {
        NewCollectionDialog(
            onDismiss = { showNewCollectionName = false },
            onCreate = { name ->
                showNewCollectionName = false
                scope.launch {
                    val collectionId = container.collectionRepository.create(name)
                    container.collectionRepository.addBook(book.id, collectionId)
                    onDismiss()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPickerDialog(
    collections: List<Collection>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onNewCollection: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to collection") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (collections.isEmpty()) {
                    Text(
                        "No collections yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                collections.forEach { collection ->
                    TextButton(
                        onClick = { onPick(collection.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            collection.name,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
                TextButton(onClick = onNewCollection, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New collection…", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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

/**
 * Sends the book through the system share sheet — Quick Share, Nearby Share, mail, Drive… What
 * goes depends on the book (see [com.vibetuned.ln_reader.data.repo.BookRepository.prepareShare]):
 * an audiobook with its EPUB or sync manifest travels as one zip, so ln-reader on the other end
 * rebuilds it whole. Writing that zip takes a moment for a long book, so the button shows how far
 * along it is.
 */
@Composable
private fun ShareBookButton(bookId: String, title: String) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    OutlinedButton(
        onClick = {
            error = null
            progress = 0f
            scope.launch {
                runCatching { container.bookRepository.prepareShare(bookId) { progress = it } }
                    .onSuccess { share ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = share.mimeType
                            putExtra(Intent.EXTRA_STREAM, share.uri)
                            putExtra(Intent.EXTRA_TITLE, share.fileName)
                            // The clip carries the read grant through the chooser to the target.
                            clipData = ClipData.newRawUri(share.fileName, share.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share \u201c$title\u201d"))
                    }
                    .onFailure { error = it.message ?: "Couldn't prepare the book to share." }
                progress = null
            }
        },
        enabled = progress == null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Outlined.Share, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        val label = progress?.let { "Preparing… ${(it * 100).toInt()}%" } ?: "Share"
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
    progress?.let {
        LinearProgressIndicator(
            progress = { it },
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
    error?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
