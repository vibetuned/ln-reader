package com.vibetuned.ln_reader.data.repo

import com.vibetuned.ln_reader.data.model.Book
import com.vibetuned.ln_reader.data.prefs.CollectionSort
import com.vibetuned.ln_reader.data.prefs.LibrarySort
import com.vibetuned.ln_reader.data.prefs.SortDirection
import com.vibetuned.ln_reader.data.prefs.SortField

/** Order [books] by the chosen field + direction (Name / Date added). */
fun sortBooksByField(books: List<Book>, sort: LibrarySort): List<Book> {
    val ascending = when (sort.field) {
        SortField.Name -> books.sortedBy { it.title.lowercase() }
        SortField.DateAdded -> books.sortedBy { it.importedAt }
    }
    return if (sort.direction == SortDirection.Desc) ascending.reversed() else ascending
}

/** Order [books] by the arranged [order] of ids; unplaced books fall to the end (newest first). */
fun applyManualOrder(books: List<Book>, order: List<String>): List<Book> {
    val rank = order.withIndex().associate { (i, id) -> id to i }
    return books.sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { -it.importedAt }))
}

/**
 * A collection's books in their displayed order: the hand-arranged order when the collection is in
 * manual mode, otherwise the field sort. This is the single source of truth for collection ordering,
 * shared by the library grid, the reorder screen, and end-of-book collection advancing.
 */
fun orderCollectionBooks(
    books: List<Book>,
    sort: LibrarySort,
    collectionSort: CollectionSort
): List<Book> =
    if (collectionSort.manual) applyManualOrder(books, collectionSort.order)
    else sortBooksByField(books, sort)
