package com.vibetuned.ln_reader.ui.reader

import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
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
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.delay
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
            bookRepository = container.bookRepository,
            readerPreferences = container.readerPreferences,
            readingPositionRepository = container.readingPositionRepository,
            readLogRepository = container.readLogRepository,
            debugSearchQuery = container.pendingReaderSearch.also { container.pendingReaderSearch = null },
            debugStartPage = container.pendingReaderPage.also { container.pendingReaderPage = null }
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

    // Back steps out of search progressively: first the results list, then search mode itself.
    BackHandler(enabled = state.searchActive) {
        if (state.showSearchResults && state.searchResults != null) viewModel.hideSearchResults()
        else viewModel.closeSearch()
    }

    Scaffold(
        topBar = {
            if (state.searchActive) {
                ReaderSearchBar(
                    query = state.searchQuery,
                    resultCount = state.searchResults?.size,
                    selection = state.searchSelection,
                    onQueryChange = viewModel::setSearchQuery,
                    onSubmit = viewModel::submitSearch,
                    onClose = viewModel::closeSearch,
                    onShowResults = viewModel::showSearchResults,
                    onStep = viewModel::stepSearchResult
                )
            } else {
                ReaderTopBar(state = state, viewModel = viewModel, onBack = onBack)
            }
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
                else -> {
                    EpubWebView(
                        state = state,
                        bookId = bookId,
                        onScroll = viewModel::reportScroll,
                        onRestored = viewModel::consumeRestoreScroll
                    )
                    if (state.searchActive && state.showSearchResults) {
                        SearchResultsList(
                            results = state.searchResults,
                            isSearching = state.isSearching,
                            onResultClick = viewModel::openSearchResult
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    state: ReaderUiState,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
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
            if (state.spine.isNotEmpty()) {
                IconButton(onClick = viewModel::openSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search in book")
                }
            }
            IconButton(onClick = viewModel::zoomOut) {
                Icon(Icons.Outlined.TextDecrease, contentDescription = "Smaller text")
            }
            IconButton(onClick = viewModel::zoomIn) {
                Icon(Icons.Outlined.TextIncrease, contentDescription = "Larger text")
            }
            IconButton(onClick = viewModel::toggleDarkMode) {
                Icon(
                    imageVector = if (state.isDark) Icons.Outlined.LightMode
                    else Icons.Outlined.DarkMode,
                    contentDescription = if (state.isDark) "Light mode" else "Dark mode"
                )
            }
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
}

@Composable
private fun EpubWebView(
    state: ReaderUiState,
    bookId: String?,
    onScroll: (spineIndex: Int, fraction: Float) -> Unit,
    onRestored: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    /**
     * Identity of the document currently in the WebView: a monotonically increasing count of
     * finished loads, deliberately not a loaded/not-loaded flag.
     *
     * These pages are local files and can finish loading inside a single Compose frame, so a
     * flag's true -> false -> true toggle coalesces into no observed change at all: every effect
     * keyed on it then skips the new document and keeps whatever it injected into the old one.
     * That silently dropped the reader theme whenever audio sync turned the page the moment the
     * first page appeared. A counter gives each document a distinct key, so the effects always
     * re-run against the document that is actually on screen.
     */
    var pageLoads by remember { mutableIntStateOf(0) }
    var loadedUrl by remember { mutableStateOf<String?>(null) }
    /** True when the loaded document is the page the state currently points at. */
    val pageReady = pageLoads > 0 && loadedUrl == state.spine.getOrNull(state.currentIndex)?.url

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
                setBackgroundColor(if (state.isDark) READER_DARK_BG else android.graphics.Color.WHITE)
                settings.javaScriptEnabled = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.textZoom = state.textZoom
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        loadedUrl = url
                        pageLoads++
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
    LaunchedEffect(state.activeBeatId, pageLoads) {
        val wv = webView ?: return@LaunchedEffect
        if (pageLoads == 0) return@LaunchedEffect
        wv.evaluateJavascript(INJECT_STYLE_JS, null)
        state.activeBeatId?.let { beatId ->
            wv.evaluateJavascript(highlightJs(state.dataAttr, beatId), null)
        }
    }

    // Search: once the target page is loaded, highlight every match and scroll to the selected
    // occurrence. Clearing the target (new search, search closed) strips the highlight spans. A
    // page that isn't the target needs no cleanup — navigating gave it a fresh document.
    LaunchedEffect(state.searchTarget, pageLoads) {
        val wv = webView ?: return@LaunchedEffect
        if (pageLoads == 0) return@LaunchedEffect
        val target = state.searchTarget
        when {
            target == null -> wv.evaluateJavascript(CLEAR_SEARCH_JS, null)
            target.spineIndex == state.currentIndex ->
                wv.evaluateJavascript(searchHighlightJs(target.jsPattern, target.occurrence), null)
        }
    }

    // Live text-size zoom (font scaling, not pinch).
    LaunchedEffect(state.textZoom, webView) {
        webView?.settings?.textZoom = state.textZoom
    }

    // Reading position — restore. Once the page is loaded, scroll to the saved offset; done twice
    // because a page with images grows after `onPageFinished`, and a single early scroll would
    // land short. Then hand back so it's applied exactly once.
    LaunchedEffect(state.restoreScrollFraction, pageLoads, state.currentIndex) {
        val wv = webView ?: return@LaunchedEffect
        val fraction = state.restoreScrollFraction ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        delay(RESTORE_FIRST_DELAY_MS)
        wv.evaluateJavascript(restoreScrollJs(fraction), null)
        delay(RESTORE_SECOND_DELAY_MS)
        wv.evaluateJavascript(restoreScrollJs(fraction), null)
        onRestored()
    }

    // Reading position — save. While a page is up, sample its scroll offset and report it; the
    // ViewModel only writes when it actually moved. Paused until a pending restore has landed,
    // so the fresh page's offset of 0 can't overwrite the position being restored.
    LaunchedEffect(webView, pageLoads, state.currentIndex, state.restoreScrollFraction) {
        val wv = webView ?: return@LaunchedEffect
        if (!pageReady || state.restoreScrollFraction != null) return@LaunchedEffect
        val index = state.currentIndex
        while (true) {
            delay(SCROLL_SAMPLE_MS)
            wv.evaluateJavascript(SCROLL_FRACTION_JS) { result ->
                result?.toFloatOrNull()?.let { onScroll(index, it) }
            }
        }
    }

    // Reader theme: paint the WebView background immediately and (re)inject the dark stylesheet on
    // every page load so navigating pages keeps the theme.
    LaunchedEffect(state.isDark, pageLoads, webView) {
        val wv = webView ?: return@LaunchedEffect
        wv.setBackgroundColor(if (state.isDark) READER_DARK_BG else android.graphics.Color.WHITE)
        if (pageLoads > 0) wv.evaluateJavascript(themeJs(state.isDark), null)
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

// ARGB for the dark reader background — the brand slate base, matching ui/theme/Color.kt so the
// page and the surrounding chrome are the same surface.
private const val READER_DARK_BG = 0xFF121316.toInt()

/**
 * Applies (dark) or clears (light) a reader theme by managing a single injected `<style>` element
 * and the document background. Forces a dark surface with light text while leaving images and the
 * active-beat highlight intact; light mode just removes the override.
 */
private fun themeJs(dark: Boolean): String {
    val css = if (dark) {
        "html,body{background:#121316 !important;}" +
            "body, body *:not(a){color:#e8ece9 !important;}" +
            "body *{background-color:transparent !important;}" +
            "a, a *{color:#abcfb2 !important;}" +
            // Keep the active-beat highlight visible: the transparent-background rule above uses
            // !important, so the highlight needs its own !important rule (class beats `body *` on
            // specificity). A translucent amber wash carries warm golden type rather than
            // inverting to dark-on-solid — glare-free at night, and the same treatment as iOS.
            ".lnvox-active{background:rgba(198,146,52,0.18) !important;border-radius:3px !important;}" +
            ".lnvox-active,.lnvox-active *{color:#f1c465 !important;}"
    } else {
        ""
    }
    val bg = if (dark) "#121316" else "#ffffff"
    return """
(function(){
  var id='lnvox-theme';
  var e=document.getElementById(id);
  if(e) e.remove();
  var css='$css';
  if(css){var s=document.createElement('style');s.id=id;s.textContent=css;(document.head||document.documentElement).appendChild(s);}
  document.documentElement.style.backgroundColor='$bg';
  if(document.body) document.body.style.backgroundColor='$bg';
})();
"""
}

private const val RESTORE_FIRST_DELAY_MS = 120L
private const val RESTORE_SECOND_DELAY_MS = 400L
private const val SCROLL_SAMPLE_MS = 1_500L

/** Fraction (0..1) of the page's scrollable height the viewport is currently at; 0 when it all fits. */
private const val SCROLL_FRACTION_JS = """
(function(){
  var d=document.documentElement, b=document.body;
  var h=Math.max(d.scrollHeight, b?b.scrollHeight:0) - window.innerHeight;
  return h>0 ? Math.min(1, Math.max(0, window.scrollY/h)) : 0;
})();
"""

private fun restoreScrollJs(fraction: Float): String = """
(function(f){
  var d=document.documentElement, b=document.body;
  var h=Math.max(d.scrollHeight, b?b.scrollHeight:0) - window.innerHeight;
  if (h>0) window.scrollTo(0, f*h);
})(${fraction.coerceIn(0f, 1f)});
"""

private const val INJECT_STYLE_JS = """
(function(){
  if (document.getElementById('lnvox-style')) return;
  var s = document.createElement('style');
  s.id = 'lnvox-style';
  s.textContent = '.lnvox-active{background:rgba(198,146,52,0.40);border-radius:3px;}';
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
