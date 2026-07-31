package com.vibetuned.ln_reader.ui.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.ui.common.appContainer
import java.io.File

private val ROW_HEIGHT = 68.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderScreen(
    collectionId: String? = null,
    onBack: () -> Unit = {}
) {
    if (collectionId == null) return
    val container = appContainer()
    val viewModel: ReorderViewModel = viewModel(
        factory = ReorderViewModel.factory(
            collectionId = collectionId,
            bookRepository = container.bookRepository,
            collectionRepository = container.collectionRepository,
            libraryPreferences = container.libraryPreferences
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Local working copy the drag mutates; seeded once from the loaded order.
    val order = remember { mutableStateListOf<Book>() }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(state.books) {
        if (!seeded && state.books.isNotEmpty()) {
            order.clear()
            order.addAll(state.books)
            seeded = true
        }
    }
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.collectionName?.let { "Reorder · $it" } ?: "Reorder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(order.map { it.id }) },
                        enabled = order.isNotEmpty()
                    ) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                order.isEmpty() -> Text(
                    "No books to reorder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> ReorderableBookList(books = order, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Long-press a row's drag handle and slide it to a new spot. Rows are a fixed height so the target
 * index follows from the accumulated drag offset: once it crosses half a row the item swaps with its
 * neighbour and the residual offset keeps it under the finger; the displaced row animates into place.
 */
@Composable
private fun ReorderableBookList(
    books: SnapshotStateList<Book>,
    modifier: Modifier = Modifier
) {
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LazyColumn(modifier = modifier) {
        itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
            val dragging = index == draggingIndex
            Surface(
                tonalElevation = if (dragging) 6.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffsetY else 0f }
                    .then(if (dragging) Modifier else Modifier.animateItem())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (book.coverPath != null) {
                        AsyncImage(
                            model = File(book.coverPath),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 8.dp)
                                .aspectRatio(2f / 3f)
                                .clip(MaterialTheme.shapes.small)
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        book.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.pointerInput(book.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = books.indexOfFirst { it.id == book.id }
                                    dragOffsetY = 0f
                                },
                                onDragEnd = { draggingIndex = null; dragOffsetY = 0f },
                                onDragCancel = { draggingIndex = null; dragOffsetY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    var cur = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                    while (dragOffsetY > rowHeightPx / 2 && cur < books.lastIndex) {
                                        books.add(cur + 1, books.removeAt(cur))
                                        cur++
                                        dragOffsetY -= rowHeightPx
                                        draggingIndex = cur
                                    }
                                    while (dragOffsetY < -rowHeightPx / 2 && cur > 0) {
                                        books.add(cur - 1, books.removeAt(cur))
                                        cur--
                                        dragOffsetY += rowHeightPx
                                        draggingIndex = cur
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
