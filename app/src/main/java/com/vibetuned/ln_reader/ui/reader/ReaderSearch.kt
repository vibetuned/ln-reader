package com.vibetuned.ln_reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.vibetuned.ln_reader.companion.EpubSearchMatch
import com.vibetuned.ln_reader.companion.EpubTextSearch
import org.json.JSONObject

/** Replaces the reader's top bar while search is active. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchBar(
    query: String,
    resultCount: Int?,
    selection: Int?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onShowResults: () -> Unit,
    onStep: (Int) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search in book") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSubmit()
                }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        actions = {
            if (selection != null && resultCount != null && resultCount > 0) {
                // Tapping the counter reopens the results list.
                TextButton(onClick = onShowResults) {
                    Text("${selection + 1}/$resultCount")
                }
                IconButton(onClick = { onStep(-1) }, enabled = selection > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                }
                IconButton(onClick = { onStep(1) }, enabled = selection < resultCount - 1) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                }
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search text")
                }
            }
        }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Full-content overlay listing the matches of the submitted query. */
@Composable
fun SearchResultsList(
    results: List<EpubSearchMatch>?,
    isSearching: Boolean,
    onResultClick: (Int) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            isSearching -> CenteredBox { CircularProgressIndicator() }
            results == null -> CenteredBox {
                Text(
                    "Search the whole book for a word or phrase.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
            results.isEmpty() -> CenteredBox {
                Text(
                    "No matches found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn {
                item {
                    val capped = results.size >= EpubTextSearch.MAX_RESULTS
                    val label = if (results.size == 1) "1 match" else "${results.size} matches"
                    Text(
                        if (capped) "First $label" else label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                itemsIndexed(results) { index, result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Page ${result.spineIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            buildAnnotatedString {
                                append(result.snippet)
                                addStyle(
                                    SpanStyle(fontWeight = FontWeight.Bold),
                                    result.matchStart,
                                    (result.matchStart + result.matchLength)
                                        .coerceAtMost(result.snippet.length)
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Removes every search highlight span, restoring the original text nodes. */
const val CLEAR_SEARCH_JS = """
(function(){
  document.querySelectorAll('span.lnvox-search, span.lnvox-search-current').forEach(function(el){
    var p = el.parentNode;
    while (el.firstChild) p.insertBefore(el.firstChild, el);
    p.removeChild(el);
    p.normalize();
  });
})();
"""

/**
 * Highlights every match of [jsPattern] on the loaded page and scrolls to the match with document
 * order index [occurrence].
 *
 * Walks the body's text nodes, searches their concatenation (so matches spanning inline tags are
 * found), then wraps each match's per-node slices in spans — strictly right to left, so earlier
 * offsets stay valid as nodes get split. Occurrence indexes agree with
 * [com.vibetuned.ln_reader.companion.EpubTextSearch], which mirrors this text extraction.
 */
fun searchHighlightJs(jsPattern: String, occurrence: Int): String {
    val quotedPattern = JSONObject.quote(jsPattern)
    return """
(function(){
  var STYLE_ID = 'lnvox-search-style';
  if (!document.getElementById(STYLE_ID)) {
    var st = document.createElement('style');
    st.id = STYLE_ID;
    st.textContent =
      '.lnvox-search{background:rgba(255,213,79,0.45) !important;border-radius:2px;}' +
      '.lnvox-search-current{background:rgba(255,152,0,0.95) !important;border-radius:2px;}' +
      '.lnvox-search-current{color:#1a1a1a !important;}';
    (document.head || document.documentElement).appendChild(st);
  }
  $CLEAR_SEARCH_JS
  var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
  var nodes = [], starts = [], lens = [], text = '';
  var n;
  while ((n = walker.nextNode())) {
    var tag = n.parentNode && n.parentNode.nodeName;
    if (tag === 'SCRIPT' || tag === 'STYLE') continue;
    starts.push(text.length);
    lens.push(n.nodeValue.length);
    nodes.push(n);
    text += n.nodeValue;
  }
  var re = new RegExp($quotedPattern, 'gi');
  var matches = [], m;
  while ((m = re.exec(text))) {
    if (m[0].length === 0) { re.lastIndex++; continue; }
    matches.push([m.index, m.index + m[0].length]);
    if (matches.length > 2000) break;
  }
  var K = $occurrence;
  var j = nodes.length - 1;
  for (var i = matches.length - 1; i >= 0; i--) {
    var ms = matches[i][0], me = matches[i][1];
    var cls = (i === K) ? 'lnvox-search-current' : 'lnvox-search';
    while (j > 0 && starts[j] >= me) j--;
    var firstSpan = null;
    for (var k = j; k >= 0; k--) {
      var ns = starts[k], ne = ns + lens[k];
      if (ne <= ms) break;
      if (ns >= me) continue;
      var s = Math.max(ms, ns) - ns, e = Math.min(me, ne) - ns;
      if (e <= s || nodes[k].nodeValue.length < e) continue;
      var r = document.createRange();
      r.setStart(nodes[k], s);
      r.setEnd(nodes[k], e);
      var span = document.createElement('span');
      span.className = cls;
      try { r.surroundContents(span); firstSpan = span; } catch (ex) {}
    }
    if (i === K && firstSpan) firstSpan.scrollIntoView({block:'center'});
  }
})();
"""
}
