# New App — Technical Specification & Shared Conventions

**Purpose.** This document captures every architectural and tooling decision made
building **ln-reader** (an Android audiobook player) so a *second* app can be
started from the same foundation. The two apps should stay **easy to maintain
together**: same stack, same versions, same patterns, same file layout. Follow
this document as house style — deviate only with a documented reason.

> Reference implementation: the `ln-reader` codebase. When in doubt about a
> pattern, open the equivalent file there and mirror it. This spec is the
> distilled "why + how"; ln-reader is the "see it in action".

Throughout, replace the placeholders:
- `<AppName>` → PascalCase app name (e.g. `NoteVox`)
- `<app>` → lowercase module/id segment (e.g. `notevox`)
- `com.vibetuned.<app>` → your package (ln-reader uses `com.vibetuned.ln_reader`)

---

## 0. TL;DR decisions (the non-negotiables)

| Area | Decision |
|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3), single-Activity |
| Min / Target / Compile SDK | 33 / 36 / 36 |
| DI | **Manual `AppContainer`** (lazy process-scoped singletons). **No Hilt** (see §2). |
| Async / state | Coroutines + `StateFlow`; unidirectional data flow |
| Persistence | **Room** (structured data) + **DataStore Preferences** (settings/flags) |
| Images | **Coil 3** |
| Navigation | **navigation-compose**, single `NavHost`, typed route objects |
| Media (if needed) | **Media3** `MediaSessionService` + process-scoped `PlayerHolder` |
| Build | AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.3.0 (AGP built-in) / KSP / version catalog |
| Layer shape | `data/` (db, prefs, repo, model) → `ui/` (screen, viewmodel, uistate). No use-case layer. |
| Fast check | `./gradlew :app:compileDebugKotlin` after every change (runs KSP → validates Room SQL) |

---

## 1. Tech stack & exact versions

Use these versions verbatim in a `gradle/libs.versions.toml` so both apps stay in
lockstep. Bump both apps together.

```toml
[versions]
agp = "9.2.1"
kotlin = "2.1.20"          # KSP toolchain ref; AGP 9 supplies the built-in Kotlin (2.3.0) for compilation
ksp = "2.1.20-1.0.32"

coreKtx = "1.15.0"
appcompat = "1.7.0"
activityCompose = "1.10.0"
lifecycle = "2.8.7"
composeBom = "2025.10.00"
navigationCompose = "2.8.5"

room = "2.7.0"
datastore = "1.1.1"
documentfile = "1.0.1"     # SAF DocumentFile — include only if you use Storage Access Framework
webkit = "1.12.1"          # WebView helpers — include only if you render HTML/EPUB

media3 = "1.5.1"           # include only if the app plays audio/video
coil = "3.0.4"

junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-documentfile = { group = "androidx.documentfile", name = "documentfile", version.ref = "documentfile" }
androidx-webkit = { group = "androidx.webkit", name = "webkit", version.ref = "webkit" }
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Drop libraries the new app doesn't need (media3 / webkit / documentfile are
domain-specific). Coil, Room, DataStore, Navigation, Compose are the baseline.

---

## 2. Build & tooling — AGP 9 gotchas (READ THIS FIRST)

AGP 9 reorganized how Kotlin is wired in. These four rules are mandatory or the
build won't configure:

1. **Do NOT apply `org.jetbrains.kotlin.android`.** AGP 9 has built-in Kotlin and
   registers its own `kotlin` extension; adding the JetBrains plugin fails with
   *"Cannot add extension with name 'kotlin'…"*. Only apply
   `com.android.application`, `kotlin-compose`, and `ksp`.
2. **No Hilt.** As of this writing Hilt targets AGP 8 APIs (`BaseExtension`) and
   fails on AGP 9. → We use the **manual `AppContainer`** pattern (§4). Revisit
   only when Google ships an AGP-9-compatible Hilt.
3. **No `kotlinOptions { jvmTarget = … }`.** Removed in AGP 9. Use
   `kotlin { jvmToolchain(17) }` at the top level of `app/build.gradle.kts`.
4. **KSP needs an opt-in flag.** Without it: *"Using kotlin.sourceSets DSL to add
   Kotlin sources is not allowed with built-in Kotlin."* Add to `gradle.properties`:
   `android.disallowKotlinSourceSets=false` (experimental transition flag).

**`gradle.properties`:**
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
kotlin.code.style=official
android.disallowKotlinSourceSets=false
```

**`settings.gradle.kts`:** standard; `RepositoriesMode.FAIL_ON_PROJECT_REPOS`,
`google()` + `mavenCentral()`, foojay toolchain resolver, `include(":app")`.

**Root `build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

**`app/build.gradle.kts`:**
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.vibetuned.<app>"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.vibetuned.<app>"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            ndk { debugSymbolLevel = "FULL" }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { jvmToolchain(17) }

dependencies {
    // core + compose + lifecycle + navigation + room(ksp) + datastore + coil …
    // (mirror the version-catalog aliases from §1; add media3/webkit/documentfile only if used)
}
```

**Fast feedback loop.** After each change run:
```
./gradlew :app:compileDebugKotlin
```
This runs `kspDebugKotlin` first, so it also **validates all Room `@Query` SQL and
entity mappings** at compile time — the cheapest way to catch DB mistakes.

---

## 3. Project structure (package layout)

One Gradle module (`:app`), package `com.vibetuned.<app>`. Feature-first UI,
layered data. Mirror this tree:

```
<AppName>Application.kt        # Application; owns AppContainer
MainActivity.kt               # single Activity; sets Compose content + root Scaffold + NavHost

di/
  AppContainer.kt             # manual DI: all singletons as `by lazy`

data/
  db/
    Entities.kt               # all @Entity classes (+ small query POJOs)
    <App>Dao.kt               # all @Dao interfaces (grouped in one file is fine)
    <App>Database.kt          # @Database + migrations + build()
  model/                      # domain data classes (Entity-free)
  prefs/                      # one DataStore class per concern (each its OWN file name)
  repo/
    Mappers.kt                # Entity <-> domain extension fns
    *Repository.kt            # one per aggregate

ui/
  common/AppContainerProvider.kt   # `appContainer()` composable accessor
  navigation/
    Destinations.kt           # bottom-nav enum
    <App>NavGraph.kt          # NavHost + route objects
  theme/                      # Color.kt, Theme.kt, Type.kt (Compose Material 3)
  <feature>/                  # per feature: <Feature>Screen.kt, <Feature>ViewModel.kt, <Feature>UiState.kt, sheets/rows

player/                       # OPTIONAL: Media3 service + PlayerHolder + notifications (only if media app)
```

---

## 4. Dependency injection — the `AppContainer` pattern

**Decision:** manual DI. One process-scoped container of `by lazy` singletons,
owned by the `Application`, read in Compose via a tiny accessor. No frameworks.

**Application:**
```kotlin
class <AppName>Application : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

**Container** — every dependency is a `by lazy val`; construct DAOs/prefs/repos
here and nowhere else. Process-scoped mutable flags are allowed (e.g. a
"restored last item once this launch" guard).
```kotlin
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: <App>Database by lazy { <App>Database.build(appContext) }

    val somePreferences: SomePreferences by lazy { SomePreferences(appContext) }

    val someRepository: SomeRepository by lazy {
        SomeRepository(appContext, database.someDao())
    }

    // Process-scoped one-shot flag example (survives config changes, resets per process):
    var didRestoreOnce = false
}
```

**Compose accessor** (`ui/common/AppContainerProvider.kt`):
```kotlin
@Composable
@ReadOnlyComposable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as <AppName>Application).container
```

Screens/VM-factories pull dependencies via `appContainer()`. Non-Compose
process-scoped classes (services) read `(application as <AppName>Application).container`.

---

## 5. Data layer

### 5.1 Room

- **One `@Database` class** with `exportSchema = false`.
- **Incremental, non-destructive migrations.** Every schema change = a new
  `Migration(n, n+1)` + version bump. `fallbackToDestructiveMigration(dropAllTables = true)`
  stays registered **only as a last-resort safety net** — never rely on it to
  ship a schema change (it wipes user data). Real users are on the Play Store.
- **Migration SQL must match the entity-derived schema** (column types,
  nullability, no stray `DEFAULT`). Room validates at runtime and throws on
  mismatch. Test both a fresh install and an upgrade-from-previous-version.
- **Reactive reads → `Flow<…>`**, one-shot reads → `suspend`.
- **Joins/aggregates → dedicated query POJOs** (Room maps by column name).

```kotlin
@Database(
    entities = [ThingEntity::class, /* … */],
    version = 1,
    exportSchema = false
)
abstract class <App>Database : RoomDatabase() {
    abstract fun thingDao(): ThingDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE things ADD COLUMN note TEXT")
            }
        }
        fun build(context: Context): <App>Database =
            Room.databaseBuilder(context.applicationContext, <App>Database::class.java, "<app>.db")
                .addMigrations(MIGRATION_1_2 /*, … */)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
```

```kotlin
@Dao
interface ThingDao {
    @Query("SELECT * FROM things ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ThingEntity>>

    @Query("SELECT * FROM things WHERE id = :id")
    suspend fun byId(id: String): ThingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thing: ThingEntity)

    @Query("DELETE FROM things WHERE id = :id")
    suspend fun delete(id: String)
}
```

### 5.2 Mappers (`data/repo/Mappers.kt`)

Keep entities and domain models separate; convert with `internal` extension fns.
```kotlin
internal fun ThingEntity.toDomain() = Thing(id = id, name = name /* … */)
```

### 5.3 Repositories

Constructor-inject DAOs / `Context` / other repos. Blocking work on
`Dispatchers.IO`. Reactive via `Flow.map`; cross-source via `combine`. Return
`Result<T>` for fallible operations (imports, parsing) instead of throwing.

```kotlin
class ThingRepository(
    private val context: Context,
    private val thingDao: ThingDao
) {
    fun things(): Flow<List<Thing>> = thingDao.observeAll().map { it.map(ThingEntity::toDomain) }

    suspend fun get(id: String): Thing? = withContext(Dispatchers.IO) { thingDao.byId(id)?.toDomain() }

    suspend fun import(uri: Uri): Result<Thing> = withContext(Dispatchers.IO) {
        runCatching { /* parse + persist + return domain */ }
    }
}
```

### 5.4 DataStore Preferences

**Decision:** one small preferences class per concern, and **each class gets its
own DataStore file name** (a top-level `preferencesDataStore(name = "…")`
delegate). Two classes must never share a file → "multiple DataStores active for
the same file" crash.

- `Flow` getters with a default, `suspend` setters, keys in a `companion object`.
- Typed options via `enum`: persist `.name`, read back with
  `runCatching { Enum.valueOf(it) }.getOrNull() ?: DEFAULT`.
- Persist anything the user would expect to survive a restart (sort order, theme,
  zoom, last-used folder). In-memory VM state is fine only for truly ephemeral UI.

```kotlin
private val Context.thingDataStore by preferencesDataStore(name = "<app>_thing_prefs")

class ThingPreferences(private val context: Context) {
    val sort: Flow<SortOrder> = context.thingDataStore.data.map {
        it[KEY_SORT]?.let { s -> runCatching { SortOrder.valueOf(s) }.getOrNull() } ?: SortOrder.Recent
    }
    suspend fun setSort(order: SortOrder) {
        context.thingDataStore.edit { it[KEY_SORT] = order.name }
    }
    companion object { private val KEY_SORT = stringPreferencesKey("thing_sort") }
}
```

---

## 6. Domain models (`data/model/`)

Plain immutable `data class`es, no Room/Android imports. Computed conveniences as
`val … get()`. Example:
```kotlin
data class Thing(
    val id: String,
    val name: String,
    val attachmentPath: String? = null,
) {
    val hasAttachment: Boolean get() = attachmentPath != null
}
```

---

## 7. ViewModels & UI state

**UiState** = one immutable `data class` per screen, all fields defaulted. Use
`sealed interface` for heterogeneous list items and `enum` for modes.

```kotlin
data class ThingListUiState(
    val items: List<Thing> = emptyList(),
    val sort: SortOrder = SortOrder.Recent,
    val isLoading: Boolean = true,
    val error: String? = null,
)
```

**ViewModel** conventions:
- Plain `ViewModel` (no Hilt, no `SavedStateHandle` unless genuinely needed).
- `private val _state = MutableStateFlow(UiState())` + `val state = _state.asStateFlow()`.
- Update with `_state.update { it.copy(...) }`.
- In `init`, `combine(...)` the reactive sources and `collect { … }` into state.
- Prefs are the single source of truth for persisted settings: the setter writes
  to DataStore; the same prefs `Flow` (in `combine`) feeds state back.
- Actions are functions that `viewModelScope.launch { … }`.
- One-shot "navigate away" signals: a boolean flag in UiState the screen observes
  (`LaunchedEffect(state.done) { if (state.done) onBack() }`) — keep the launching
  work on `viewModelScope` so it survives the pop.
- Construction via a **companion `factory(...)`** using `viewModelFactory`.

```kotlin
class ThingListViewModel(
    private val repo: ThingRepository,
    private val prefs: ThingPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(ThingListUiState())
    val state: StateFlow<ThingListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repo.things(), prefs.sort) { items, sort -> sortItems(items, sort) to sort }
                .collect { (items, sort) -> _state.update { it.copy(items = items, sort = sort, isLoading = false) } }
        }
    }

    fun setSort(order: SortOrder) { viewModelScope.launch { prefs.setSort(order) } }

    companion object {
        fun factory(repo: ThingRepository, prefs: ThingPreferences) = viewModelFactory {
            initializer { ThingListViewModel(repo, prefs) }
        }
    }
}
```

> Note: `combine` supports up to 5 flows in one lambda. Need more? Nest a `combine`
> or split into a second collector that updates a different slice of state.

---

## 8. Compose UI conventions

- Each screen: a top-level `@Composable fun <Feature>Screen(...)` taking
  **navigation callbacks** (`onOpenX: (id) -> Unit`) — never a `NavController`.
- Get the VM with `viewModel(factory = <VM>.factory(appContainer().…))`. Pass a
  `key` when one composable hosts parameterized variants on distinct back-stack
  entries.
- Collect state with **`collectAsStateWithLifecycle()`** (from
  `androidx.lifecycle.compose`).
- Each screen owns a `Scaffold` (its own `TopAppBar`, FAB, snackbar). The root
  Scaffold in `MainActivity` owns only the bottom nav (+ any global chrome).
- Material 3 components + `material-icons-extended` for icons.
- **`rememberSaveable` gotcha:** it survives *process death* (restored from the
  saved-state bundle). For "do this once per process launch" logic (e.g. auto-open
  something on cold start), use a **process-scoped flag on `AppContainer`**, not
  `rememberSaveable` — otherwise the action is wrongly skipped after the OS
  recreates your process.
- Images: `AsyncImage(model = File(path) | uri, …)` (Coil 3). Overlapping/stacked
  layouts: negative `Arrangement.spacedBy((-N).dp, Alignment.Center*)` + `zIndex`.
- Discrete non-linear sliders: index-based `Slider(value = idx, valueRange =
  0f..lastIndex, steps = size - 2)`, map index → value list.

### Theming (`ui/theme/Theme.kt`)

Material 3 with dynamic color on Android 12+ and light/dark from the system:
```kotlin
@Composable
fun <AppName>Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
```
`MainActivity` calls `enableEdgeToEdge()` and sets `contentWindowInsets =
WindowInsets(0)` on the root Scaffold (each screen/nav-bar consumes its own
insets) to avoid double-applied insets on Android 15+.

---

## 9. Navigation

Single `NavHost`. **Typed route objects** with a `PATTERN` and `forX(...)`
builders; optional args as query params with `navArgument` defaults. Bottom-nav
destinations from an `enum`. ViewModels are scoped to back-stack entries, so
distinct routes/args get distinct VM instances.

```kotlin
object ThingRoute {
    const val PATTERN = "thing?id={id}&edit={edit}"
    fun forThing(id: String, edit: Boolean = false) = "thing?id=$id&edit=$edit"
}

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Settings("settings", "Settings", Icons.Outlined.Settings);
    companion object { val Start = Home }
}

@Composable
fun <App>NavGraph(navController: NavHostController, startDestination: String = TopLevelDestination.Start.route) {
    NavHost(navController, startDestination) {
        composable(TopLevelDestination.Home.route) {
            HomeScreen(onOpenThing = { id -> navController.navigate(ThingRoute.forThing(id)) })
        }
        composable(
            route = ThingRoute.PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("edit") { type = NavType.BoolType; defaultValue = false },
            )
        ) { entry ->
            ThingScreen(
                id = entry.arguments?.getString("id"),
                edit = entry.arguments?.getBoolean("edit") ?: false,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

**Reuse a screen for parameterized variants.** ln-reader renders the same
`LibraryScreen` for the top-level library (`collectionId = null`) and for an
opened collection (`collection?collectionId=…`). Prefer one parameterized screen
over duplicated screens.

---

## 10. Media playback module (copy only if the app plays audio/video)

This is ln-reader-specific but reusable wholesale. Pattern:

- **`PlaybackService : MediaSessionService`** owns the `ExoPlayer` and a
  `MediaSession`, runs as a foreground service (`foregroundServiceType=
  "mediaPlayback"`), and persists playback position (throttled every ~5s while
  playing and on every pause/stop) via a repository.
- **`PlayerHolder`** (process-scoped, in `AppContainer`) binds a
  `MediaController` and exposes it as `StateFlow<MediaController?>` — null while
  connecting. UI observes this; a `loadBook(item, startMs, playWhenReady)` helper
  sets the media item and prepares.
- A **mini-player** reads "now playing" straight off the `MediaController`
  (`currentMediaItem.mediaMetadata` for title/artwork, `isPlaying`, position) via
  a `produceState` that adds a `Player.Listener` + polls ~1s; no extra global
  state class needed.
- Manifest: `INTERNET` (if streaming), `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`; declare
  the `<service>` with the media-session intent filters.

If the new app has a different "engine" (e.g. a sync engine, a camera pipeline),
apply the same shape: a **process-scoped holder in `AppContainer` exposing a
`StateFlow`**, with the heavy component living in a service if it must outlive the
UI.

---

## 11. Coding style & documentation

- **Match the surrounding code.** Kotlin idioms, expression bodies where natural,
  `data class` for state, sealed hierarchies for variants.
- **Comment the *why*, generously.** ln-reader's files carry substantial doc
  comments explaining rationale, edge cases, and platform quirks (e.g. why a
  fade rule needs `!important`, why insets are zeroed). New code should read the
  same. Favor a short paragraph over a terse one-liner when the reasoning is
  non-obvious.
- KDoc on public/shared functions and non-trivial composables.
- No premature abstraction: no use-case layer, no interface-per-repo unless a
  second implementation actually exists. Prefer a stubbed interface over
  speculative implementation when scoping new work.
- Keep DAOs/prefs/entities grouped sensibly (ln-reader keeps all DAOs in one
  file, all entities in one file — fine at this size).

---

## 12. Versioning & release notes

- **`versionName`** = semver (`1.3`), **`versionCode`** = monotonically
  increasing integer. Bump **both together** for every Play Store upload.
- **`CHANGELOG.md`** at repo root. Newest first. Each release:
  1. `## vX.Y — YYYY-MM-DD`
  2. a one-line summary,
  3. a fenced **`### Play Store release notes (≤ 500 chars)`** block (verify the
     char count — Google's limit),
  4. sectioned details grouped by feature area.

```markdown
## v1.3 — 2026-06-29

Short summary of the release.

### Play Store release notes (≤ 500 chars)

​```
• Bullet users actually read.
• Another one.
​```

### <Feature area>
- **Thing** — what changed and why it matters.
```

Char-count check:
```
awk '/## vX.Y/{f=1} f&&/^```$/{c++; if(c==1){inblock=1;next} if(c==2)exit} inblock{print}' CHANGELOG.md | wc -c
```

---

## 13. Testing & verification

- Unit tests with JUnit4 (`src/test`), instrumented with androidx.test/espresso
  (`src/androidText`) — scaffolding present but keep the compile-check as the
  primary fast gate.
- **Always** run `./gradlew :app:compileDebugKotlin` after edits (compiles + KSP
  validates Room).
- For UI/behavior changes, verify on an emulator/device — especially: Room
  migrations (fresh install **and** upgrade), foreground playback, and anything
  visual. The compile check can't catch runtime schema mismatches or layout.

---

## 14. Bootstrap checklist for the new app

1. `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`,
   `gradle.properties` (incl. `android.disallowKotlinSourceSets=false`), Gradle
   wrapper `9.4.1`.
2. `app/build.gradle.kts` per §2 (only the plugins in §2; drop unused libs).
3. `<AppName>Application` + `AppContainer` + `appContainer()` accessor.
4. `ui/theme/` (Color/Theme/Type) + `MainActivity` (edge-to-edge, root Scaffold +
   bottom nav + NavHost).
5. Room: `Entities.kt`, `<App>Dao.kt`, `<App>Database.kt` (version 1), `Mappers.kt`.
6. First feature vertical slice: `model` → `repo` → `prefs` (if any) →
   `UiState` + `ViewModel` (+ `factory`) → `Screen` → route in the nav graph.
7. `AndroidManifest.xml`: permissions + `<service>`/`<receiver>` only as the
   domain needs.
8. `CHANGELOG.md` with a `v1.0` entry.
9. `./gradlew :app:compileDebugKotlin` → green.

---

## 15. New-app domain — FILL THIS IN

Everything above is shared house style. Capture the new app's specifics here so
the two apps diverge only where they must:

- **What the app does / primary user goal:** _…_
- **Core entities & their relationships (Room tables):** _…_
- **Screens & navigation graph:** _…_
- **Persisted settings (DataStore):** _…_
- **Any "engine" (service / background work) and its holder:** _…_
- **External inputs (SAF import, network, sensors, WebView…):** _…_
- **Permissions required:** _…_

> Reuse ln-reader modules where the domain overlaps (media playback, SAF import,
> mini-player, sleep timer, WebView reader with dark-mode CSS injection + text
> zoom). Lift the file, rename the package, keep the pattern.
```
