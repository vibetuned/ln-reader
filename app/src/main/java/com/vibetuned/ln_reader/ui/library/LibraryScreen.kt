package com.vibetuned.ln_reader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.model.Collection
import com.vibetuned.ln_reader.data.prefs.SortDirection
import com.vibetuned.ln_reader.data.prefs.SortField
import com.vibetuned.ln_reader.ui.common.appContainer
import java.io.File
import java.util.concurrent.TimeUnit

private val M4B_MIME_TYPES = arrayOf("audio/mp4", "audio/x-m4b", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    collectionId: String? = null,
    onPlayBook: (String) -> Unit = {},
    onViewImages: (String) -> Unit = {},
    onReadBook: (String) -> Unit = {},
    onOpenCollection: (String) -> Unit = {},
    onReorder: (collectionId: String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val container = appContainer()
    val viewModel: LibraryViewModel = viewModel(
        key = "library:${collectionId ?: "root"}",
        factory = LibraryViewModel.factory(
            collectionId = collectionId,
            bookRepository = container.bookRepository,
            positionRepository = container.positionRepository,
            collectionRepository = container.collectionRepository,
            libraryPreferences = container.libraryPreferences
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showNewCollection by remember { mutableStateOf(false) }
    var showDeleteCollection by remember { mutableStateOf(false) }

    val isCollectionView = collectionId != null

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    // When the open collection is deleted, leave the (now-gone) screen.
    LaunchedEffect(state.collectionDeleted) {
        if (state.collectionDeleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isCollectionView) (state.collectionName ?: "Collection") else "Library")
                },
                navigationIcon = {
                    if (isCollectionView) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortField.entries.forEach { field ->
                                val selected = !state.manualSort && state.sortField == field
                                DropdownMenuItem(
                                    text = { Text(sortFieldLabel(field)) },
                                    trailingIcon = {
                                        if (selected) {
                                            val asc = state.sortDirection == SortDirection.Asc
                                            Icon(
                                                imageVector = if (asc) Icons.Filled.ArrowUpward
                                                else Icons.Filled.ArrowDownward,
                                                contentDescription = if (asc) "Ascending" else "Descending"
                                            )
                                        }
                                    },
                                    onClick = {
                                        // Re-tapping the active field flips its direction; picking a
                                        // different field starts from that field's natural default.
                                        val direction = if (selected) {
                                            if (state.sortDirection == SortDirection.Asc) SortDirection.Desc
                                            else SortDirection.Asc
                                        } else {
                                            defaultDirection(field)
                                        }
                                        viewModel.setSort(field, direction)
                                        showSortMenu = false
                                    }
                                )
                            }
                            // Manual arrangement is a per-collection mode; only offered inside one.
                            // Tapping it switches to manual order and opens the reorder list.
                            if (isCollectionView) {
                                DropdownMenuItem(
                                    text = { Text("Manual") },
                                    trailingIcon = {
                                        if (state.manualSort) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected")
                                        }
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        viewModel.enableManual()
                                        collectionId?.let(onReorder)
                                    }
                                )
                            }
                        }
                    }
                    if (isCollectionView) {
                        IconButton(onClick = { showDeleteCollection = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete collection")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = {
                        if (isCollectionView) pickLauncher.launch(M4B_MIME_TYPES)
                        else showAddMenu = true
                    }
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = if (isCollectionView) "Import m4b" else "Add"
                    )
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Book") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        },
                        onClick = {
                            showAddMenu = false
                            pickLauncher.launch(M4B_MIME_TYPES)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Collection") },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            showNewCollection = true
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CenteredProgress()
                state.entries.isEmpty() -> EmptyLibrary(inCollection = isCollectionView)
                else -> LibraryGrid(
                    entries = state.entries,
                    onBookClick = { selectedBook = it },
                    onCollectionClick = { onOpenCollection(it.id) }
                )
            }
            if (state.isImporting) {
                ImportProgressBanner(
                    progress = state.importProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    selectedBook?.let { book ->
        BookDetailSheet(
            book = book,
            onDismiss = { selectedBook = null },
            onPlay = {
                selectedBook = null
                onPlayBook(book.id)
            },
            onViewImages = {
                selectedBook = null
                onViewImages(book.id)
            },
            onRead = {
                selectedBook = null
                onReadBook(book.id)
            },
            onDelete = {
                viewModel.delete(book.id)
                selectedBook = null
            }
        )
    }

    if (showNewCollection) {
        NewCollectionDialog(
            onDismiss = { showNewCollection = false },
            onCreate = { name ->
                viewModel.createCollection(name)
                showNewCollection = false
            }
        )
    }

    if (showDeleteCollection) {
        DeleteCollectionDialog(
            collectionName = state.collectionName ?: "this collection",
            bookCount = state.entries.size,
            onDismiss = { showDeleteCollection = false },
            onKeepBooks = {
                showDeleteCollection = false
                viewModel.deleteCollection(deleteBooks = false)
            },
            onDeleteBooks = {
                showDeleteCollection = false
                viewModel.deleteCollection(deleteBooks = true)
            }
        )
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ImportProgressBanner(
    progress: com.vibetuned.ln_reader.data.repo.BookRepository.ImportProgress?,
    modifier: Modifier = Modifier
) {
    val phase = progress?.phase
    val total = progress?.totalBytes ?: -1L
    val read = progress?.bytesRead ?: 0L
    // Stay indeterminate until the first bytes actually arrive. Cloud providers can take a while to
    // open the stream (preparing the file server-side) and emit no progress meanwhile; a
    // determinate bar pinned at 0% reads as "frozen", whereas a spinning bar says "starting…".
    val determinate = phase == com.vibetuned.ln_reader.data.repo.BookRepository.ImportProgress.Phase.Downloading &&
        total > 0 && read > 0

    androidx.compose.material3.Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (determinate) {
                LinearProgressIndicator(
                    progress = { (read.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = phaseLabel(phase, read, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun phaseLabel(
    phase: com.vibetuned.ln_reader.data.repo.BookRepository.ImportProgress.Phase?,
    read: Long,
    total: Long
): String = when (phase) {
    com.vibetuned.ln_reader.data.repo.BookRepository.ImportProgress.Phase.Parsing -> "Parsing m4b…"
    com.vibetuned.ln_reader.data.repo.BookRepository.ImportProgress.Phase.Downloading ->
        when {
            read <= 0L -> "Starting download…"
            total > 0 -> "Downloading: ${formatBytes(read)} / ${formatBytes(total)}"
            else -> "Downloading: ${formatBytes(read)}"
        }
    com.vibetuned.ln_reader.data.repo.BookRepository.ImportProgress.Phase.Finalizing -> "Finalizing…"
    null -> "Importing…"
}

private fun formatBytes(b: Long): String {
    if (b <= 0) return "0 B"
    val mb = b / (1024.0 * 1024.0)
    if (mb >= 1.0) return "%.1f MB".format(mb)
    val kb = b / 1024.0
    if (kb >= 1.0) return "%.0f KB".format(kb)
    return "$b B"
}

@Composable
private fun EmptyLibrary(inCollection: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            modifier = Modifier.height(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (inCollection) "No books here yet" else "No books yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (inCollection) "Tap + to add an .m4b file to this collection."
            else "Tap + to import an .m4b file, or create a collection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryGrid(
    entries: List<LibraryEntry>,
    onBookClick: (Book) -> Unit,
    onCollectionClick: (Collection) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            entries,
            key = { entry ->
                when (entry) {
                    is LibraryEntry.CollectionEntry -> "c:${entry.collection.id}"
                    is LibraryEntry.BookEntry -> "b:${entry.book.id}"
                }
            }
        ) { entry ->
            when (entry) {
                is LibraryEntry.CollectionEntry -> CollectionCard(
                    collection = entry.collection,
                    onClick = { onCollectionClick(entry.collection) }
                )
                is LibraryEntry.BookEntry -> BookCard(
                    book = entry.book,
                    progress = entry.progress,
                    onClick = { onBookClick(entry.book) }
                )
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: Collection, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        Column {
            CollectionCoverArt(
                coverPaths = collection.coverPaths,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${collection.bookCount} ${if (collection.bookCount == 1) "book" else "books"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tile art for a collection: up to three shelves of three covers, each shelf's covers overlapping
 * toward the left (leftmost on top) so they read as a stack of books standing on a shelf. Falls
 * back to a folder glyph when the collection has no books with covers yet.
 */
@Composable
private fun CollectionCoverArt(coverPaths: List<String>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (coverPaths.isEmpty()) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val rowGap = 4.dp
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                // Size every cover for a full 3-row grid so a shelf of 1-2 covers matches a full
                // one; otherwise fewer rows would each get a taller share and blow the covers up.
                val rowHeight = (maxHeight - rowGap * 2) / 3
                val coverWidth = rowHeight * 2f / 3f
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(rowGap, Alignment.CenterVertically)
                ) {
                    coverPaths.take(9).chunked(3).forEach { shelf ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                            // Negative spacing overlaps the covers like books on a shelf; centering
                            // keeps a short stack in the middle of the tile.
                            horizontalArrangement =
                                Arrangement.spacedBy((-10).dp, Alignment.CenterHorizontally)
                        ) {
                            shelf.forEachIndexed { index, path ->
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        // Leftmost cover draws on top of the ones tucked behind it.
                                        .zIndex((shelf.size - index).toFloat())
                                        .height(rowHeight)
                                        .width(coverWidth)
                                        .clip(RoundedCornerShape(2.dp))
                                        .border(
                                            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                            RoundedCornerShape(2.dp)
                                        )
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
private fun BookCard(book: Book, progress: Float, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = File(book.coverPath),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
            // Playback progress through the book, sitting flush between the cover and the title.
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!book.author.isNullOrBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    formatDuration(book.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Prompt for a collection name. Reused by the book detail sheet's "New collection…" flow. */
@Composable
internal fun NewCollectionDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New collection") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DeleteCollectionDialog(
    collectionName: String,
    bookCount: Int,
    onDismiss: () -> Unit,
    onKeepBooks: () -> Unit,
    onDeleteBooks: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$collectionName\"?") },
        text = {
            Text(
                if (bookCount == 0) "This collection is empty."
                else "Choose what to do with the $bookCount " +
                    "${if (bookCount == 1) "book" else "books"} inside."
            )
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onKeepBooks,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (bookCount == 0) "Delete collection" else "Move books to library")
                }
                if (bookCount > 0) {
                    TextButton(
                        onClick = onDeleteBooks,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete books too")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

private fun sortFieldLabel(field: SortField): String = when (field) {
    SortField.DateAdded -> "Date added"
    SortField.Name -> "Name"
}

/** Sensible starting direction when switching to a field: newest-first for dates, A→Z for names. */
private fun defaultDirection(field: SortField): SortDirection = when (field) {
    SortField.DateAdded -> SortDirection.Desc
    SortField.Name -> SortDirection.Asc
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
