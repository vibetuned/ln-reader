package com.vibetuned.ln_reader.ui.reader

import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.vibetuned.ln_reader.ui.common.appContainer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String? = null,
    onBack: () -> Unit = {}
) {
    val container = appContainer()
    val appContext = LocalContext.current.applicationContext
    val viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.factory(
            appContext = appContext,
            playerHolder = container.playerHolder,
            bookRepository = container.bookRepository
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(bookId) {
        if (bookId != null) viewModel.open(bookId)
    }

    // Keep the screen on while the audio for this book is actually playing — so the user can
    // read along without the device locking. The moment audio pauses (manually, or because the
    // sleep timer fired), the flag is released and the screen can sleep again. Disposing the
    // reader (back navigation) also releases it.
    val view = LocalView.current
    DisposableEffect(state.isAudioPlaying) {
        view.keepScreenOn = state.isAudioPlaying
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.bookTitle.ifEmpty { "Reader" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Only meaningful when sync is attached and the user has paged away from the
                    // audio. Tapping re-engages auto-follow and jumps to the current beat.
                    if (state.hasSync && !state.autoFollow) {
                        TextButton(onClick = { viewModel.setAutoFollow(true) }) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.spine.isNotEmpty()) {
                ReaderBottomBar(
                    currentIndex = state.currentIndex,
                    pageCount = state.spine.size,
                    onPrev = viewModel::prevPage,
                    onNext = viewModel::nextPage
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Centered { CircularProgressIndicator() }
                state.error != null -> Centered {
                    Text(
                        state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                state.spine.isEmpty() -> Centered { Text("This EPUB has no readable pages.") }
                else -> EpubWebView(state = state, bookId = bookId)
            }
        }
    }
}

@Composable
private fun EpubWebView(state: ReaderUiState, bookId: String?) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    val assetLoader = remember(bookId) {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/epub/",
                WebViewAssetLoader.InternalStoragePathHandler(
                    context,
                    File(context.filesDir, "epubs/$bookId")
                )
            )
            .build()
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.javaScriptEnabled = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        pageLoaded = false
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        pageLoaded = true
                    }
                }
                webView = this
            }
        }
    )

    // Load the current spine page whenever the index changes.
    LaunchedEffect(state.currentIndex, webView, state.spine) {
        val wv = webView ?: return@LaunchedEffect
        state.spine.getOrNull(state.currentIndex)?.let { page ->
            if (wv.url != page.url) wv.loadUrl(page.url)
        }
    }

    // Highlight the active beat once the page is loaded (and on every beat change).
    LaunchedEffect(state.activeBeatId, pageLoaded) {
        val wv = webView ?: return@LaunchedEffect
        if (!pageLoaded) return@LaunchedEffect
        wv.evaluateJavascript(INJECT_STYLE_JS, null)
        state.activeBeatId?.let { beatId ->
            wv.evaluateJavascript(highlightJs(state.dataAttr, beatId), null)
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentIndex: Int,
    pageCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev, enabled = currentIndex > 0) {
            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
        }
        Text(
            "Page ${currentIndex + 1} of $pageCount",
            style = MaterialTheme.typography.labelLarge
        )
        IconButton(onClick = onNext, enabled = currentIndex < pageCount - 1) {
            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private const val INJECT_STYLE_JS = """
(function(){
  if (document.getElementById('lnvox-style')) return;
  var s = document.createElement('style');
  s.id = 'lnvox-style';
  s.textContent = '.lnvox-active{background:rgba(255,213,79,0.45);border-radius:3px;}';
  (document.head || document.documentElement).appendChild(s);
})();
"""

private fun highlightJs(dataAttr: String, beatId: String): String = """
(function(){
  document.querySelectorAll('.lnvox-active').forEach(function(e){ e.classList.remove('lnvox-active'); });
  var el = document.querySelector('[$dataAttr="$beatId"]');
  if (el){ el.classList.add('lnvox-active'); el.scrollIntoView({block:'center'}); }
})();
"""
