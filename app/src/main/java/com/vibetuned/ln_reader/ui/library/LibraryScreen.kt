package com.vibetuned.ln_reader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.ui.common.appContainer
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onPlayBook: (String) -> Unit = {},
    onViewImages: (String) -> Unit = {},
    onReadBook: (String) -> Unit = {}
) {
    val container = appContainer()
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(container.bookRepository)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedBook by remember { mutableStateOf<Book?>(null) }

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    pickLauncher.launch(
                        arrayOf("audio/mp4", "audio/x-m4b", "application/octet-stream")
                    )
                }
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Import m4b")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CenteredProgress()
                state.books.isEmpty() -> EmptyLibrary()
                else -> BookGrid(
                    books = state.books,
                    onBookClick = { selectedBook = it }
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
    val determinate = phase == com.vibetuned.ln_reader.data.repo.BookRepository.ImportProgress.Phase.Downloading && total > 0

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
        if (total > 0) "Downloading: ${formatBytes(read)} / ${formatBytes(total)}"
        else "Downloading: ${formatBytes(read)}"
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
private fun EmptyLibrary() {
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
            "No books yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + to import an .m4b file from your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    onBookClick: (Book) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(book = book, onClick = { onBookClick(book) })
        }
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
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

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
