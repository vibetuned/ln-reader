---
name: Athenaeum Reading Sanctuary
colors:
  surface: '#121316'
  surface-dim: '#121316'
  surface-bright: '#38393c'
  surface-container-lowest: '#0d0e11'
  surface-container-low: '#1b1b1f'
  surface-container: '#1f1f23'
  surface-container-high: '#292a2d'
  surface-container-highest: '#343538'
  on-surface: '#e3e2e6'
  on-surface-variant: '#c2c8c0'
  inverse-surface: '#e3e2e6'
  inverse-on-surface: '#2f3034'
  outline: '#8c928b'
  outline-variant: '#424842'
  surface-tint: '#abcfb2'
  primary: '#abcfb2'
  on-primary: '#173722'
  primary-container: '#4a6b53'
  on-primary-container: '#c5eacc'
  inverse-primary: '#45664e'
  secondary: '#b2cdb9'
  on-secondary: '#1e3527'
  secondary-container: '#344c3d'
  on-secondary-container: '#a1bca8'
  tertiary: '#e9c349'
  on-tertiary: '#3c2f00'
  tertiary-container: '#cba72f'
  on-tertiary-container: '#4e3d00'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#c7ecce'
  primary-fixed-dim: '#abcfb2'
  on-primary-fixed: '#01210f'
  on-primary-fixed-variant: '#2e4e37'
  secondary-fixed: '#cee9d4'
  secondary-fixed-dim: '#b2cdb9'
  on-secondary-fixed: '#092013'
  on-secondary-fixed-variant: '#344c3d'
  tertiary-fixed: '#ffe088'
  tertiary-fixed-dim: '#e9c349'
  on-tertiary-fixed: '#241a00'
  on-tertiary-fixed-variant: '#574500'
  background: '#121316'
  on-background: '#e3e2e6'
  surface-variant: '#343538'
  surface-slate-base: '#121316'
  surface-slate-raised: '#1A1C20'
  surface-slate-overlay: '#23262C'
  reading-amber-highlight: '#C69234'
  reading-amber-glow: '#3D2E12'
  sage-subtle: '#6C8A74'
  parchment-text: '#E8ECE9'
  muted-caption: '#8E959E'
typography:
  display-lg:
    fontFamily: Newsreader
    fontSize: 2.5rem
    fontWeight: '400'
    lineHeight: 3rem
    letterSpacing: -0.015em
  headline-lg:
    fontFamily: Newsreader
    fontSize: 2rem
    fontWeight: '500'
    lineHeight: 2.5rem
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Newsreader
    fontSize: 1.625rem
    fontWeight: '500'
    lineHeight: 2.125rem
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Newsreader
    fontSize: 1.5rem
    fontWeight: '500'
    lineHeight: 2rem
  headline-sm:
    fontFamily: Newsreader
    fontSize: 1.25rem
    fontWeight: '600'
    lineHeight: 1.75rem
  body-reading:
    fontFamily: Newsreader
    fontSize: 1.1875rem
    fontWeight: '400'
    lineHeight: 1.95rem
    letterSpacing: 0.01em
  body-reading-mobile:
    fontFamily: Newsreader
    fontSize: 1.0625rem
    fontWeight: '400'
    lineHeight: 1.75rem
    letterSpacing: 0.01em
  body-ui-lg:
    fontFamily: Inter
    fontSize: 1rem
    fontWeight: '400'
    lineHeight: 1.5rem
  body-ui-md:
    fontFamily: Inter
    fontSize: 0.875rem
    fontWeight: '400'
    lineHeight: 1.25rem
  label-md:
    fontFamily: Inter
    fontSize: 0.8125rem
    fontWeight: '500'
    lineHeight: 1.125rem
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 0.6875rem
    fontWeight: '600'
    lineHeight: 0.875rem
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-desktop: 1.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2.5rem
  space-2xs: 0.25rem
  space-xs: 0.5rem
  space-sm: 0.75rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
  space-2xl: 3rem
---

## Brand & Style

The design system establishes a quiet, intellectual sanctuary engineered for long-form immersion, classic public-domain literature, and spoken audio narration. Rather than adopting cold, high-contrast digital interfaces or saturated neon accents, it channels the tactile solemnity of leather-bound volumes, historic libraries, and dark oak study rooms. 

The target audience includes devoted readers, audiobook listeners, scholars, and night-time bibliophiles who demand visual restfulness without sacrificing typography fidelity. The emotional tone is meditative, scholarly, grounded, and comforting.

Drawing from a synthesis of modern editorial minimalism and muted tactile surfaces, the interface balances graceful literary serifs with sharp utilitarian UI chrome. Glass and frosted surfaces are disciplined and dark, reducing visual fatigue during prolonged reading sessions.

## Colors

The palette abandons harsh, clinical blacks (`#000000`) and piercing white text (`#FFFFFF`) in favor of deep slate bases and soft parchment typography. 

- **Primary (`#4A6B53`)**: A heritage forest green derived from historic book bindings. It serves as the steady anchor for active playback controls, key progress trackers, and focused navigation states.
- **Secondary (`#8FA996`)**: A desaturated sage tone that handles secondary highlights, inactive audio track fills, and non-intrusive interactive tokens.
- **Tertiary (`#D4AF37` / `#C69234`)**: Warm antique ochre and burnished amber. This color is reserved strictly for active narration text highlighting, bookmark glyphs, and audio sleep timer indicators.
- **Neutral (`#121316`)**: A velvety charcoal slate foundation. Layered cards use `#1A1C20` and floating overlays leverage `#23262C`, avoiding the contrast shock of pitch black while providing separation from artwork and illustrations.

## Typography

The typography architecture maintains a strict separation between **literary engagement** and **system chrome**:

1. **The Editorial Serif (`Newsreader`)**: Expresses book titles, chapter designations, author bylines, and the reading canvas itself. Its optical sizing and proportional rhythm mimic physical typesetting, encouraging eye-flow and calm continuity. In the reader mode, ample line-height (`1.75rem` to `1.95rem`) ensures effortless tracking across pages.
2. **The Modern Utilitarian (`Inter`)**: Handles timers, scrubber timestamps, settings lists, playback badges, and search inputs. It provides clarity at diminutive scales and guarantees that functional elements remain discreet.

## Layout & Spacing

The layout model adopts a fixed column structure with responsive gutters. On mobile viewports, the interface adheres to a 4-column framework with a default margin of `1rem`. On desktop and tablet, it transitions into an 8-column and 12-column grid respectively.

The reading viewport enforces a max-width reading measure of `65ch` to guarantee ergonomic cadence. Catalog views organize book covers into balanced 2-column grids on mobile and 4-to-6 column arrangements on larger screens. Mini-players and bottom sheets stay pegged to bottom screen bounds with floating safe-area offsets (`space-md`), preventing layout crowding.

## Elevation & Depth

Rather than high-altitude, diffuse black drop shadows, depth is achieved via **tonal layering and low-contrast perimeter rings**:

- **Ground (Level 0)**: `#121316` — The foundational slate canvas.
- **Surface (Level 1)**: `#1A1C20` bordered by `rgba(255, 255, 255, 0.05)` — Library cards, inactive list rows, and secondary sheets.
- **Overlay & Chrome (Level 2)**: `#23262C` combined with a soft ambient shadow (`0 8px 24px rgba(0, 0, 0, 0.35)`) and a top inner highlight (`1px solid rgba(255, 255, 255, 0.08)`) — The persistent audio mini-player, modal menus, and quick-setting sheets.
- **Glass Accent**: Translucent backgrounds (`rgba(26, 28, 32, 0.82)`) with a `backdrop-filter: blur(16px)` applied to floating bottom bars and top navigation app bars to allow artwork to glide softly beneath.

## Shapes

The design system uses a standard roundedness level of `2` (base `0.5rem` / `8px`), offering a calm, tailored silhouette that avoids both razor-sharp edges and playful pill shapes for structural cards. 

- **Containers & Book Jackets**: Formed with `rounded-md` (`0.5rem`) to maintain the formal contour of physical book bindings.
- **Interactive Controls**: Playback pills and sleep timer chips adopt `rounded-lg` (`1rem`) to provide tactile affordance under the thumb.
- **Text Selection**: Highlighting boxes feature micro-radii (`0.25rem`) that follow individual sentence segments gracefully.

## Components

### Buttons & Scrubbers
- **Primary Play Action**: Circular or soft-rounded (`rounded-lg`) container filled with `#4A6B53`, containing parchment-toned glyphs. Under active touch, it scales smoothly (`0.96`) with an amber edge glow (`rgba(212, 175, 55, 0.25)`).
- **Secondary Actions (Skip 10/30s, Sleep, Cast)**: Icon-only buttons with subtle slate hover states (`#23262C`) and low-opacity foreground icons (`rgba(232, 236, 233, 0.75)`).
- **Seek Slider**: Slender track (`3px`) with a soft `#23262C` background, an active fill in `#4A6B53`, and a tactile ivory circular knob (`14px`) with an ambient dark halo.

### Reading Text & Audio Sync
- **Active Spoken Segment**: Text highlighted with a translucent amber background (`rgba(198, 146, 52, 0.18)`) and warm golden typography (`#F1C465`). As audio plays, the highlight transitions smoothly with a `200ms` ease-out.
- **Standard Reading Body**: Rendered in `Newsreader` regular, set in soft parchment (`#E8ECE9`), calibrated against `#121316` for glare-free reading in low-light environments.

### Cards & Book Tiles
- **Library Cover Card**: Aspect ratio 2:3 with subtle spine curvature rendering (`inset 2px 0 3px rgba(0,0,0,0.3)`). Progress is communicated via a discreet bottom bar in `#4A6B53` rather than obtrusive percent badges.
- **Audiobook Mini-Player**: Floats 8px above the bottom tab bar. Backed by frosted slate (`rgba(35, 38, 44, 0.94)` with `16px` blur), bordered by a `1px` stroke of `rgba(255, 255, 255, 0.06)`, housing thumbnail, serif book title, chapter status, and immediate play/scrub controls.

### Chips & Controls
- **Timer Chips**: Outlined with `1px solid rgba(255, 255, 255, 0.08)` on `#1A1C20`. Selected chips fill with `#4A6B53` and display crisp `Inter` label tokens.
- **Navigation Chrome**: Bottom tab icons use refined stroke icons accompanied by `label-sm` text. Active tabs illuminate in `#8FA996` with a tiny amber pip indicator underneath.