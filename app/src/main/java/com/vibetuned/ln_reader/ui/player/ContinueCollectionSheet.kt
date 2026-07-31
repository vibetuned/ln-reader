package com.vibetuned.ln_reader.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vibetuned.ln_reader.player.CollectionEndBook
import com.vibetuned.ln_reader.player.CollectionEndPrompt
import com.vibetuned.ln_reader.ui.common.appContainer
import java.io.File

/**
 * Global host for the end-of-book "continue collection" sheet. Rendered once high in the tree so it
 * can appear over any screen when a book inside a collection finishes.
 */
@Composable
fun ContinueCollectionHost() {
    val controller = appContainer().collectionAdvanceController
    val prompt by controller.prompt.collectAsStateWithLifecycle()
    prompt?.let { current ->
        ContinueCollectionSheet(
            prompt = current,
            onContinue = { bookId -> controller.continueTo(bookId) },
            onDismiss = { controller.dismiss() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueCollectionSheet(
    prompt: CollectionEndPrompt,
    onContinue: (bookId: String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Finished",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "“${prompt.finishedTitle}”",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Continue the collection",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                prompt.previous?.let { book ->
                    NeighborButton(
                        label = "Previous",
                        book = book,
                        emphasized = false,
                        onClick = { onContinue(book.id) }
                    )
                }
                prompt.next?.let { book ->
                    NeighborButton(
                        label = "Up next",
                        book = book,
                        emphasized = true,
                        onClick = { onContinue(book.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NeighborButton(
    label: String,
    book: CollectionEndBook,
    emphasized: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = File(book.coverPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
