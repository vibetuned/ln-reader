package com.vibetuned.ln_reader.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.vibetuned.ln_reader.LnReaderApplication
import com.vibetuned.ln_reader.di.AppContainer

@Composable
@ReadOnlyComposable
fun appContainer(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as LnReaderApplication).container
}
