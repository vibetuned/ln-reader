package com.vibetuned.ln_reader.ui.theme

import androidx.compose.ui.graphics.Color

// ── Athenaeum Reading Sanctuary ───────────────────────────────────────────────
// The shared brand palette, mirroring visual-design.md, the :root custom
// properties on ln.vibetuned.com and the iOS asset catalog, so the two apps,
// the product site and the store listings read as one thing.
//
// Deep slate bases and soft parchment type instead of pitch black and pure
// white: heritage forest green anchors controls, sage carries secondary state,
// and burnished amber is reserved for narration highlighting and bookmarks.

// Neutrals — the velvety charcoal slate foundation.
val SlateBase = Color(0xFF121316)        // --surface-slate-base
val SlateRaised = Color(0xFF1A1C20)      // --surface-slate-raised
val SlateOverlay = Color(0xFF23262C)     // --surface-slate-overlay
val SlateLowest = Color(0xFF0D0E11)
val SlateLow = Color(0xFF1B1B1F)
val SlateContainer = Color(0xFF1F1F23)
val SlateHigh = Color(0xFF292A2D)
val SlateHighest = Color(0xFF343538)
val SlateBright = Color(0xFF38393C)

// Type — soft parchment rather than piercing white.
val ParchmentText = Color(0xFFE3E2E6)    // --on-surface
val ParchmentBody = Color(0xFFE8ECE9)    // --parchment-text, the reading canvas
val SageText = Color(0xFFC2C8C0)         // --on-surface-variant
val MutedCaption = Color(0xFF8E959E)
// Deliberately recessive caption text — dimmer than MutedCaption, held at roughly 4:1 against
// each theme's surface so it reads as a watermark without dropping out of legibility.
val CaptionDim = Color(0xFF6E747B)
val CaptionDimLight = Color(0xFF767D77)

// Primary — heritage forest green off historic book bindings.
val ForestLight = Color(0xFFABCFB2)      // --primary (dark scheme)
val ForestDeep = Color(0xFF4A6B53)       // --primary-container, the brand anchor
val ForestDark = Color(0xFF173722)       // --on-primary
val ForestPale = Color(0xFFC5EACC)       // --on-primary-container
val ForestMid = Color(0xFF45664E)        // --inverse-primary

// Secondary — desaturated sage for non-intrusive interactive tokens.
val SageLight = Color(0xFFB2CDB9)
val SageDark = Color(0xFF1E3527)
val SageContainer = Color(0xFF344C3D)
val SageOnContainer = Color(0xFFA1BCA8)
val SageSubtle = Color(0xFF6C8A74)

// Tertiary — warm antique ochre, reserved for narration and bookmarks.
val AmberLight = Color(0xFFE9C349)       // --tertiary
val AmberDeep = Color(0xFFC69234)        // --reading-amber-highlight
val AmberDark = Color(0xFF3C2F00)        // --on-tertiary
val AmberContainer = Color(0xFFCBA72F)
val AmberOnContainer = Color(0xFF4E3D00)
val AmberGlow = Color(0xFF3D2E12)        // --reading-amber-glow

// Outlines — low-contrast perimeter rings carry depth instead of drop shadows.
val OutlineSage = Color(0xFF8C928B)
val OutlineSageDim = Color(0xFF424842)

// Light-scheme counterparts. The palette is dark-first; these are the matching
// tones for daylight reading, holding the same green/sage/ochre relationships.
val ForestOnLight = Color(0xFF3D6349)
val ForestContainerLight = Color(0xFFBFECC9)
val ForestOnContainerLight = Color(0xFF00210F)
val SageOnLight = Color(0xFF4F6353)
val SageContainerLight = Color(0xFFD1E8D5)
val SageOnContainerLight = Color(0xFF0D1F14)
val AmberOnLight = Color(0xFF735B0B)
val AmberContainerLight = Color(0xFFFFE08A)
val AmberOnContainerLight = Color(0xFF241A00)
val PaperBright = Color(0xFFF7FBF4)
val PaperDim = Color(0xFFD7DCD4)
val PaperContainerLow = Color(0xFFF1F6EE)
val PaperContainer = Color(0xFFEBF1E9)
val PaperContainerHigh = Color(0xFFE6EBE3)
val PaperContainerHighest = Color(0xFFE0E6DE)
val InkText = Color(0xFF191C19)
val InkVariant = Color(0xFF414941)
val InkSurfaceVariant = Color(0xFFDDE5DB)
val OutlineInk = Color(0xFF717971)
val OutlineInkDim = Color(0xFFC1C9BF)
