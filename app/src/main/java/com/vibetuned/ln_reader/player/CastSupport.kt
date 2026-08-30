package com.vibetuned.ln_reader.player

import android.content.Context
import androidx.annotation.MainThread
import com.google.android.gms.cast.framework.CastContext

/**
 * Safe accessor for the Cast framework. [CastContext] initialization throws on devices without
 * Google Play Services (or with a broken Cast module); in that case the whole feature quietly
 * disappears — no Cast button, no cast player — and local playback is untouched.
 */
object CastSupport {

    private var initialized = false
    private var castContext: CastContext? = null

    @MainThread
    @Suppress("DEPRECATION") // The synchronous getter is the pragmatic choice here; the async
    // replacement only adds a Task wrapper around the same initialization.
    fun castContextOrNull(context: Context): CastContext? {
        if (!initialized) {
            initialized = true
            castContext = runCatching {
                CastContext.getSharedInstance(context.applicationContext)
            }.getOrNull()
        }
        return castContext
    }
}
