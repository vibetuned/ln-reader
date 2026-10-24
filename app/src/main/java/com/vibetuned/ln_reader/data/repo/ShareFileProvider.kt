package com.vibetuned.ln_reader.data.repo

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/**
 * Serves shared files to the app on the receiving end of a share. A plain [FileProvider] reports a
 * file under its name on disk; this one can report another, so a book's own .m4b is shared as
 * "Title.m4b" straight from the library — no copy of a gigabyte just to rename it. (A hard link
 * would rename it for free, but SELinux refuses app processes `link` on their own data files.)
 *
 * The name travels as a query parameter on the content URI. File lookup only reads the path, so
 * the parameter changes nothing else, and because it's part of the URI it outlives this process:
 * if Android restarts the app mid-transfer to keep serving the file, the name is still there.
 */
class ShareFileProvider : FileProvider() {

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = super.query(uri, projection, selection, selectionArgs, sortOrder)
        val name = uri.getQueryParameter(DISPLAY_NAME) ?: return cursor
        cursor.use { original ->
            val renamed = MatrixCursor(original.columnNames, original.count)
            while (original.moveToNext()) {
                renamed.addRow(Array<Any?>(original.columnCount) { column ->
                    when {
                        original.getColumnName(column) == OpenableColumns.DISPLAY_NAME -> name
                        original.getType(column) == Cursor.FIELD_TYPE_INTEGER -> original.getLong(column)
                        original.isNull(column) -> null
                        else -> original.getString(column)
                    }
                })
            }
            return renamed
        }
    }

    companion object {
        private const val DISPLAY_NAME = "displayName"

        /** A shareable URI for [file], presented as [displayName] when one is given. */
        fun uriFor(context: Context, file: File, displayName: String? = null): Uri {
            val uri = getUriForFile(context, "${context.packageName}.share", file)
            return if (displayName == null) uri
            else uri.buildUpon().appendQueryParameter(DISPLAY_NAME, displayName).build()
        }
    }
}
