package com.vibetuned.ln_reader.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibetuned.ln_reader.data.prefs.DownloadPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val appContext: Context,
    private val downloadPreferences: DownloadPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            downloadPreferences.downloadFolderUri.collect { uri ->
                val name = uri?.let { resolveName(it) }
                _state.update { it.copy(downloadFolderUri = uri, downloadFolderName = name) }
            }
        }
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            downloadPreferences.setDownloadFolderUri(uri.toString())
        }
    }

    fun resetToInternal() {
        viewModelScope.launch {
            _state.value.downloadFolderUri?.let { raw ->
                runCatching {
                    appContext.contentResolver.releasePersistableUriPermission(
                        Uri.parse(raw),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            downloadPreferences.setDownloadFolderUri(null)
        }
    }

    private suspend fun resolveName(rawUri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            DocumentFile.fromTreeUri(appContext, Uri.parse(rawUri))?.name
        }.getOrNull()
    }

    companion object {
        fun factory(appContext: Context, downloadPreferences: DownloadPreferences) =
            viewModelFactory {
                initializer { SettingsViewModel(appContext, downloadPreferences) }
            }
    }
}
