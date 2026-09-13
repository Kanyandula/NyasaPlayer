# AAOS A8 — Playback error, no connection, loading: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the car a fail-fast offline rule, a Skip next on its error overlay with proper touch targets, and record the Loading and NoConnection contract rows as delivered — without touching `:app`.

**Architecture:** One pure predicate (`Song.isPlayableNow`) and one snapshot field (`PlaybackSnapshot.playWhenReady`) are added to `:core:playback`; both are additive, so mobile cannot change. `AutomotivePlayerViewModel` calls them from three guards. `CarErrorOverlay` moves onto the house `CarPillButton` and gains an optional Skip next.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Media3 1.5.1, JUnit4, Robolectric, Compose UI test.

**Spec:** `docs/superpowers/specs/2026-09-13-aaos-a8-playback-states-design.md` — read it first. Where this plan and the spec disagree, the spec wins; where this plan and an existing call site disagree, **the call site wins** — copy it, and note the discrepancy in your report.

## Global Constraints

- **No file under `app/` changes.** Decision 4 of the spec; T28 owns mobile.
- No `isMobileApp` / `isAaosApp`-style checks in shared code. Surfaces differ by module (D71).
- Detekt `maxIssues: 0`; max line length 120; trailing commas on call and declaration sites; no wildcard imports.
- `BasePlayerStateCollector` sits exactly at detekt's 16-function class ceiling: **add no functions to it.** Listener overrides inside its `controllerListener` object do not count.
- Offline wording, verbatim: title `No Connection`, message `Check your vehicle's internet connection`.
- The overlay's retry button keeps the label `Retry`.
- Commit messages: subject ≤72 chars stating the what; body wrapped at 72 explaining the why; **no AI attribution, no `Co-Authored-By` trailer**.
- Never commit to `main`. Work on `ek/aaos-a8-spec` (already exists, spec committed) or a branch cut from it.
- Unit tests run on the **debug** variant only (T23): `:automotive:testOemDebugUnitTest`, `:core:playback:testDebugUnitTest`.

## File map

| File | Change | Responsibility |
|---|---|---|
| `core/playback/src/main/java/com/example/nyasaplayer/core/playback/OfflinePlayback.kt` | create | `Song.isPlayableNow` |
| `core/playback/src/test/java/com/example/nyasaplayer/core/playback/OfflinePlaybackTest.kt` | create | the rule's four cases |
| `core/playback/src/main/java/com/example/nyasaplayer/core/playback/PlaybackSnapshot.kt` | modify | `playWhenReady` field |
| `core/playback/src/main/java/com/example/nyasaplayer/core/playback/BasePlayerStateCollector.kt` | modify | keep `playWhenReady` current |
| `core/playback/src/test/java/com/example/nyasaplayer/core/playback/PlayWhenReadySnapshotTest.kt` | create | the field follows the player |
| `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotivePlayerViewModel.kt` | modify | three guards, Skip next after error |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarErrorOverlay.kt` | modify | `CarPillButton` actions, `onSkipNext` |
| `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt` | modify | pass `onSkipNext` |
| `automotive/src/test/java/com/example/nyasaplayer/auto/ui/components/CarErrorOverlayTest.kt` | create | which buttons show |
| `docs/AAOS_PRD.md`, `docs/AAOS_SCREEN_CONTRACT.md`, `docs/aaos-DESIGN.md` | modify | rows 15/16/18/19, §9, A9, D71 |
| `docs/AAOS_A8_VERIFICATION.md` | create | the dated device record |

---

### Task 1: The offline rule

**Files:**
- Create: `core/playback/src/main/java/com/example/nyasaplayer/core/playback/OfflinePlayback.kt`
- Test: `core/playback/src/test/java/com/example/nyasaplayer/core/playback/OfflinePlaybackTest.kt`

**Interfaces:**
- Consumes: `Song` (`core/common/.../models/Song.kt`) — `audioUrl`, `songUrl`, `resolvedAudioUrl` (`audioUrl.ifBlank { songUrl }`).
- Produces: `fun Song.isPlayableNow(isOnline: Boolean): Boolean`, package `com.example.nyasaplayer.core.playback`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one offline rule both surfaces will share (A8, D71). Plain JVM: it reads two strings.
 *
 * `file:/` with one slash is deliberate — mobile builds local URIs with `File.toURI()`, which is the
 * form that produces.
 */
class OfflinePlaybackTest {

    private val streamed = Song(mediaId = "s", audioUrl = "https://cdn.example/s.mp3")
    private val local = Song(mediaId = "l", audioUrl = "file:/data/user/10/pkg/files/l.audio")

    @Test
    fun online_anythingPlays() {
        assertTrue(streamed.isPlayableNow(isOnline = true))
    }

    @Test
    fun offline_aStreamedSongDoesNot() {
        assertFalse(streamed.isPlayableNow(isOnline = false))
    }

    @Test
    fun offline_aLocalFilePlays() {
        assertTrue(local.isPlayableNow(isOnline = false))
    }

    @Test
    fun offline_readsTheSameFieldThePlayerStreamsFrom() {
        // audioUrl blank, so resolvedAudioUrl falls back to songUrl — which is what SongMediaItemMapper
        // hands the player.
        val fallback = Song(mediaId = "f", audioUrl = "", songUrl = "file:/data/user/10/pkg/files/f.audio")
        assertTrue(fallback.isPlayableNow(isOnline = false))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*OfflinePlaybackTest*'`
Expected: compilation fails — `Unresolved reference: isPlayableNow`.

- [ ] **Step 3: Write the rule**

```kotlin
package com.example.nyasaplayer.core.playback

import com.example.nyasaplayer.core.common.models.Song

/**
 * Can start right now: the network is up, or the file is already on this device.
 *
 * Reads [Song.resolvedAudioUrl], the field `SongMediaItemMapper` streams from. Knows nothing about
 * downloads — a song is local because a caller rewrote its URI to a `file:` one, which mobile does
 * today and A9 will do on the car.
 */
fun Song.isPlayableNow(isOnline: Boolean): Boolean =
    isOnline || resolvedAudioUrl.startsWith("file:")
```

- [ ] **Step 4: Run it to see it pass**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*OfflinePlaybackTest*'`
Expected: 4 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add core/playback/src/main/java/com/example/nyasaplayer/core/playback/OfflinePlayback.kt \
        core/playback/src/test/java/com/example/nyasaplayer/core/playback/OfflinePlaybackTest.kt
git commit -m "A8: one offline rule, Song.isPlayableNow" -m "Online, or already a local file. Reads resolvedAudioUrl, the field the
player streams from, so it cannot disagree with what actually plays."
```

---

### Task 2: `playWhenReady` in the snapshot

Why: a restore calls `prepare()` and then pauses, so a restored-but-paused session buffers. The car's buffering guard (Task 3) must tell "buffering because the driver pressed play" from "buffering because a restore prepared the player", and `isPlaying` cannot — it is false *while* buffering.

**Files:**
- Modify: `core/playback/src/main/java/com/example/nyasaplayer/core/playback/PlaybackSnapshot.kt`
- Modify: `core/playback/src/main/java/com/example/nyasaplayer/core/playback/BasePlayerStateCollector.kt` — `controllerListener` (after `onIsPlayingChanged`), `syncSnapshotFromPlayer`, `applyRestored`
- Test: `core/playback/src/test/java/com/example/nyasaplayer/core/playback/PlayWhenReadySnapshotTest.kt`

**Interfaces:**
- Consumes: `BasePlayerStateCollector(connection, collectorScope)`, `ControllerConnection(context, sessionToken)`, `RestoredPlayback(queue, index, song, positionMs, repeatMode)`, `collector.transport.play()` / `.pause()`, `collector.playbackState: StateFlow<PlaybackSnapshot>`.
- Produces: `PlaybackSnapshot.playWhenReady: Boolean` (default `false`).

- [ ] **Step 1: Write the failing test**

The harness is `ReconnectingCollectorTest`'s — a real `MediaSession` over a `SimpleBasePlayer`, a real `ControllerConnection`, and the main looper pumped with `idle()` (D64). Its fake player must **report** `playWhenReady` in `getState()`, or the controller never sees it change.

```kotlin
package com.example.nyasaplayer.core.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.example.nyasaplayer.core.common.models.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The snapshot knows whether the player is *trying* to play (A8).
 *
 * `isPlaying` is false while buffering, so it cannot tell a driver's play attempt from a restore that
 * prepared the player and paused it. The car's offline guard needs exactly that difference.
 */
@RunWith(RobolectricTestRunner::class)
class PlayWhenReadySnapshotTest {

    private lateinit var session: MediaSession
    private lateinit var collector: SnapshotCollector

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        session = MediaSession.Builder(context, PlayWhenReadyPlayer()).setId("a8-play-when-ready").build()
        collector = SnapshotCollector(ControllerConnection(context, session.token))
        collector.connectController()
        idle()
    }

    @After
    fun tearDown() {
        collector.releaseController()
        session.release()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun playWhenReady_followsPlayAndPause() {
        assertFalse(
            "precondition: a fresh player is not trying to play",
            collector.playbackState.value.playWhenReady,
        )

        collector.transport.play()
        idle()
        assertTrue(collector.playbackState.value.playWhenReady)

        collector.transport.pause()
        idle()
        assertFalse(collector.playbackState.value.playWhenReady)
    }

    @Test
    fun applyRestored_publishesANotPlayingSession() {
        collector.transport.play()
        idle()

        val song = Song(mediaId = "a", title = "A")
        collector.applyRestored(
            RestoredPlayback(
                queue = listOf(song),
                index = 0,
                song = song,
                positionMs = 0L,
                repeatMode = RepeatMode.Off,
            ),
        )

        assertFalse(collector.playbackState.value.playWhenReady)
    }
}

private class SnapshotCollector(connection: ControllerConnection) :
    BasePlayerStateCollector(connection, TestScope()) {
    override val positionPollIntervalMs: Long = 1_000L
}

/** Reports its own playWhenReady, so the session tells the controller when it changes. */
private class PlayWhenReadyPlayer : SimpleBasePlayer(Looper.getMainLooper()) {

    private var playWhenReady = false

    override fun getState(): State =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(MediaItemData.Builder("a").setMediaItem(MediaItem.EMPTY).build()))
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
```

Wrap any line over 120 characters before running detekt.

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :core:playback:testDebugUnitTest --tests '*PlayWhenReadySnapshotTest*'`
Expected: compilation fails — `Unresolved reference: playWhenReady`.

- [ ] **Step 3: Add the field**

In `PlaybackSnapshot.kt`, append after `currentQueueIndex`:

```kotlin
    val currentQueueIndex: Int = -1,
    /** Trying to play. True through buffering, unlike [isPlaying]; false for a restored-but-paused session. */
    val playWhenReady: Boolean = false,
)
```

- [ ] **Step 4: Keep it current in the collector**

In `BasePlayerStateCollector.kt`, inside `controllerListener`, directly after the existing `onIsPlayingChanged` override:

```kotlin
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _playbackState.update { it.copy(playWhenReady = playWhenReady) }
        }
```

In `syncSnapshotFromPlayer`, add to the `it.copy(...)` argument list, after `isPlaying = mc.isPlaying,`:

```kotlin
                playWhenReady = mc.playWhenReady,
```

In `applyRestored`, add after `isPlaying = false,`:

```kotlin
                playWhenReady = false,
```

- [ ] **Step 5: Run the module's tests**

Run: `./gradlew :core:playback:testDebugUnitTest`
Expected: all pass, including the 2 new ones. If `playWhenReady_followsPlayAndPause` fails on the `play()` assertion, check that `handlePrepare` exists on the fake: a controller's `play()` on an idle player goes through `Util.handlePlayButtonAction`, which calls `prepare()` first.

- [ ] **Step 6: Run detekt**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL. If `TooManyFunctions` fires on `BasePlayerStateCollector`, a function was added to the class rather than to the listener object — move it.

- [ ] **Step 7: Commit**

```bash
git add core/playback/src/main/java/com/example/nyasaplayer/core/playback/PlaybackSnapshot.kt \
        core/playback/src/main/java/com/example/nyasaplayer/core/playback/BasePlayerStateCollector.kt \
        core/playback/src/test/java/com/example/nyasaplayer/core/playback/PlayWhenReadySnapshotTest.kt
git commit -m "A8: the snapshot knows when the player is trying to play" -m "isPlaying is false while buffering, so it cannot tell a play attempt
from a restore that prepared the player and paused it. The car's offline
guard needs that difference. Additive: mobile never reads the field."
```

---

### Task 3: The car's guards

There is no JVM harness for `AutomotivePlayerViewModel` (its constructor needs `CarUxRestrictionsHandler`, persistence and repositories), and building one is out of scope. The logic that can be wrong in a way a unit test catches lives in Tasks 1 and 2; this task is wiring, verified on the emulator in Task 6. Read the whole file before editing — every snippet below must fit the code as it stands.

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotivePlayerViewModel.kt`

**Interfaces:**
- Consumes: `Song.isPlayableNow(isOnline)` (Task 1), `PlaybackSnapshot.playWhenReady` (Task 2), `stateCollector.transport` (`isPlaying(): Boolean?`, `play()`, `pause()`, `togglePlayPause()`, `skipNext()`, `setQueue(...)`, `shufflePlay(...)` — each command returns `Boolean`), `networkMonitor.isOnline: StateFlow<Boolean>`.
- Produces: `fun skipNextAfterError()` on `AutomotivePlayerViewModel` (Task 4 calls it).

- [ ] **Step 1: One source for the offline wording**

Add, next to the existing file-level `private const val TAG`:

```kotlin
private const val NoConnectionTitle = "No Connection"
private const val NoConnectionMessage = "Check your vehicle's internet connection"

/**
 * The offline error, one shape for the slow path and the fast one (A8). `isPlaybackError = false`
 * selects the overlay's Wi-Fi-off icon. Retry only when the failing thing is the current item —
 * see [PlayerError.isRetryable].
 */
private fun noConnectionError(isRetryable: Boolean) = PlayerError(
    title = NoConnectionTitle,
    message = NoConnectionMessage,
    isPlaybackError = false,
    isRetryable = isRetryable,
)
```

Then make `onPlaybackError`'s network branch use it. Replace the whole body of the existing override with:

```kotlin
        override fun onPlaybackError(error: PlaybackException) {
            val isNetwork = error.cause is IOException
            _uiState.update {
                it.copy(
                    // The current item is what failed, so Retry re-attempting it via
                    // togglePlayPause() acts on the thing the error is actually about.
                    error = if (isNetwork) {
                        noConnectionError(isRetryable = true)
                    } else {
                        PlayerError(
                            title = "Playback Error",
                            message = error.message ?: "Playback error",
                            isRetryable = true,
                        )
                    },
                )
            }
        }
```

This changes one pixel-level thing on purpose: a network playback error now shows the Wi-Fi-off icon instead of the warning icon, matching the fast path.

- [ ] **Step 2: The live network answer**

Mobile reads `networkMonitor.isOnline.value` directly rather than the UI state, which lags a collection behind. Do the same. Add below `private var likeObserverJob: Job? = null`:

```kotlin
    private val isOnline: Boolean get() = networkMonitor.isOnline.value
```

- [ ] **Step 3: Guard `playSong` and `shufflePlay`**

In `playSong(songs, song)`, as the first statement:

```kotlin
        // The tapped song is what starts. It was never queued, so Retry would act on someone else's
        // queue — hence no Retry (PlayerError.isRetryable).
        if (!song.isPlayableNow(isOnline)) {
            _uiState.update { it.copy(error = noConnectionError(isRetryable = false)) }
            return
        }
```

In `shufflePlay(songs)`, directly after the existing `if (songs.isEmpty()) return`:

```kotlin
        if (songs.none { it.isPlayableNow(isOnline) }) {
            _uiState.update { it.copy(error = noConnectionError(isRetryable = false)) }
            return
        }
```

- [ ] **Step 4: Restructure `togglePlayPause`**

Replace the one-line body. The `null` branch is load-bearing: a query cannot trigger a controller rebuild and the toggle can (T14, D65) — this is mobile's structure, copied.

```kotlin
    fun togglePlayPause() {
        val transport = stateCollector.transport
        // Reads the live player: the snapshot's isPlaying lags a listener callback behind it.
        val isPlaying = transport.isPlaying() ?: run {
            // Silent query, no rebuild. Ask for the toggle and let it fail, so play reconnects like
            // every other control (T14).
            transport.togglePlayPause()
            return
        }
        if (isPlaying) {
            transport.pause()
            return
        }
        val current = _uiState.value.playback.currentSong
        if (current != null && !current.isPlayableNow(isOnline)) {
            _uiState.update { it.copy(error = noConnectionError(isRetryable = true)) }
            return
        }
        transport.play()
    }
```

With no current song there is nothing to refuse, so the call goes through; the transport's own availability rules apply.

- [ ] **Step 5: Stop a stream that loses the network**

Add this private function beside `observeNetworkState()`:

```kotlin
    /**
     * Buffering, trying to play, offline, and not a local file: the stream cannot recover, so stop
     * it now rather than after ExoPlayer's timeout (A8). `playWhenReady` is what keeps a
     * restored-but-paused session — which buffers too — from raising anything (T3, D-T3.5).
     */
    private fun stopIfStreamingOffline(snapshot: PlaybackSnapshot) {
        if (isOnline || !snapshot.isBuffering || !snapshot.playWhenReady) return
        if (snapshot.currentSong?.isPlayableNow(isOnline = false) == true) return
        stateCollector.transport.pause()
        _uiState.update { it.copy(error = noConnectionError(isRetryable = true)) }
    }
```

Call it from both places the answer can change. In `observePlaybackSnapshot()`, after the existing `_uiState.update { it.copy(playback = snapshot) }`:

```kotlin
            stopIfStreamingOffline(snapshot)
```

In `observeNetworkState()`, after the existing `_uiState.update { it.copy(isOffline = !online) }`:

```kotlin
            if (!online) stopIfStreamingOffline(_uiState.value.playback)
```

The second call covers the order the first misses: buffering begins online, *then* the network drops, and no new snapshot arrives to trigger the check.

- [ ] **Step 6: Skip next after an error**

Add under `skipNext()`:

```kotlin
    /**
     * The overlay's Skip next. An errored player is idle, so the skip is followed by a play, which
     * Media3 turns into prepare-then-play on an idle player (`Util.handlePlayButtonAction`).
     */
    fun skipNextAfterError() {
        clearError()
        val transport = stateCollector.transport
        if (transport.skipNext()) transport.play()
    }
```

- [ ] **Step 7: Add the import and build**

Add `import com.example.nyasaplayer.core.playback.isPlayableNow` in sorted position. Then:

Run: `./gradlew :automotive:compileOemDebugKotlin :automotive:testOemDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL. The class already carries `@Suppress("TooManyFunctions")`.

- [ ] **Step 8: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/viewmodel/AutomotivePlayerViewModel.kt
git commit -m "A8: the car refuses to stream offline instead of timing out" -m "Three guards on the shared rule: a tapped song or shuffle that cannot
play, a play toggle on a streamed current song, and a stream that starts
buffering or loses the network mid-track. togglePlayPause keeps mobile's
null branch so play still rebuilds a lost controller (T14)."
```

---

### Task 4: The overlay

**Files:**
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarErrorOverlay.kt`
- Modify: `automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt` — the `CarErrorOverlay(...)` call under `val error = playerState.error`
- Test: `automotive/src/test/java/com/example/nyasaplayer/auto/ui/components/CarErrorOverlayTest.kt`

**Interfaces:**
- Consumes: `CarPillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true)` (`CarControls.kt`), `AutomotivePlayerViewModel.skipNextAfterError()` (Task 3), `PlayerError(title, message, isPlaybackError, isRetryable)`.
- Produces: `CarErrorOverlay(error, onDismiss, onRetry, modifier, onSkipNext: (() -> Unit)? = null)`.

- [ ] **Step 1: Write the failing test**

Conventions from `CarModalTest`: `@RunWith(RobolectricTestRunner::class)`, `createComposeRule()`, back-ticked names.

```kotlin
package com.example.nyasaplayer.auto.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nyasaplayer.core.playback.PlayerError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which actions the error overlay offers (A8). Skip next only when the caller has somewhere to skip
 * to; Retry only when retrying acts on the thing that failed.
 */
@RunWith(RobolectricTestRunner::class)
class CarErrorOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val retryable = PlayerError(title = "No Connection", message = "m", isRetryable = true)
    private val notRetryable = PlayerError(title = "No Connection", message = "m", isRetryable = false)

    @Test
    fun `skip next is offered only when the caller passes it`() {
        composeRule.setContent {
            CarErrorOverlay(error = retryable, onDismiss = {}, onRetry = {}, onSkipNext = null)
        }

        composeRule.onNodeWithText("Skip next").assertDoesNotExist()
    }

    @Test
    fun `skip next calls back when tapped`() {
        var skips = 0
        composeRule.setContent {
            CarErrorOverlay(error = retryable, onDismiss = {}, onRetry = {}, onSkipNext = { skips++ })
        }

        composeRule.onNodeWithText("Skip next").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, skips) }
    }

    @Test
    fun `retry is offered only for a retryable error`() {
        composeRule.setContent {
            CarErrorOverlay(error = notRetryable, onDismiss = {}, onRetry = {})
        }

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithText("Dismiss").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :automotive:testOemDebugUnitTest --tests '*CarErrorOverlayTest*'`
Expected: compilation fails — no parameter named `onSkipNext`.

- [ ] **Step 3: Add the parameter and thread it through**

In `CarErrorOverlay`, add the parameter **last**, so existing callers compile unchanged:

```kotlin
@Composable
fun CarErrorOverlay(
    error: PlayerError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onSkipNext: (() -> Unit)? = null,
) {
```

and pass it to the actions:

```kotlin
            ErrorActions(
                isRetryable = error.isRetryable,
                onDismiss = onDismiss,
                onRetry = onRetry,
                onSkipNext = onSkipNext,
            )
```

- [ ] **Step 4: Rebuild the actions on `CarPillButton`**

Replace the whole `private fun ErrorActions(...)` with:

```kotlin
/**
 * On [CarPillButton], which carries the 76dp touch target and the gold-label contrast rule. The old
 * hand-rolled boxes had neither (A8).
 */
@Composable
private fun ErrorActions(
    isRetryable: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSkipNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CarPillButton(label = "Dismiss", onClick = onDismiss, modifier = Modifier.weight(1f), filled = false)
        if (onSkipNext != null) {
            CarPillButton(label = "Skip next", onClick = onSkipNext, modifier = Modifier.weight(1f), filled = false)
        }
        if (isRetryable) {
            CarPillButton(label = "Retry", onClick = onRetry, modifier = Modifier.weight(1f))
        }
    }
}
```

The Retry refresh icon goes with the hand-rolled box; `CarPillButton` is label-only. Accepted: the label carries the meaning.

- [ ] **Step 5: Drop the imports that no longer have users**

Remove exactly these from `CarErrorOverlay.kt` (each had users only in the old `ErrorActions`):

```
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.nyasaplayer.core.common.ui.icons.RefreshIcon
import com.example.nyasaplayer.core.common.ui.theme.NyasaGold
import com.example.nyasaplayer.core.common.ui.theme.NyasaOnGold
```

`background`, `clip`, `Box`, `Icon`, `FontWeight` stay — `ErrorIcon` and `ErrorText` still use them. If the compiler flags another as unused, remove it too and say so in your report.

- [ ] **Step 6: Pass Skip next from the shell**

In `AutomotiveApp.kt`, the existing call is:

```kotlin
            CarErrorOverlay(
                error = error,
                onDismiss = playerViewModel::clearError,
                onRetry = {
                    playerViewModel.clearError()
                    playerViewModel.togglePlayPause()
                },
            )
```

Add one argument after `onRetry`:

```kotlin
                // Only when there is somewhere to go. Offline, skipping just raises the next error.
                onSkipNext = if (playerState.playback.hasNext && !playerState.isOffline) {
                    playerViewModel::skipNextAfterError
                } else {
                    null
                },
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :automotive:testOemDebugUnitTest detekt`
Expected: BUILD SUCCESSFUL; the 3 new tests pass with the rest.

- [ ] **Step 8: Commit**

```bash
git add automotive/src/main/java/com/example/nyasaplayer/auto/ui/components/CarErrorOverlay.kt \
        automotive/src/main/java/com/example/nyasaplayer/auto/ui/AutomotiveApp.kt \
        automotive/src/test/java/com/example/nyasaplayer/auto/ui/components/CarErrorOverlayTest.kt
git commit -m "A8: Skip next on the error overlay, on real touch targets" -m "The overlay's actions move onto CarPillButton, which has the 76dp
target and contrast rule the hand-rolled boxes lacked. Skip next shows
only when there is a next track and the car is online."
```

---

### Task 5: The documents

**Files:**
- Modify: `docs/AAOS_PRD.md` — §6.3 rows 15, 16, 18, 19 (currently lines 252–256); §9 A8 row (line 427); a new A9 row after it; glossary rows `A1–A8` (lines 537–538)
- Modify: `docs/AAOS_SCREEN_CONTRACT.md` — rows 15, 16, 18, 19 (lines 110–114)
- Modify: `docs/aaos-DESIGN.md` — D71 after the D70 block (D70 ends at line 799; `## Components` follows)

Line numbers are as of `93b5bee`; match on content, not numbers. Edit by exact row text — never splice on a repeating heading.

- [ ] **Step 1: PRD §6.3**

Keep each row's column count. Replace the four rows' contents:

- **15** `CarDownloadsScreen` — phase column `A8` → `A9`; nothing else.
- **16** `CarNoConnectionScreen` — Primary actions `Retry, go to downloads` → `none — a behaviour, not a screen: offline play fails fast with the error overlay; the offline banner stays (D71)`. Keep the phase `A8`.
- **18** `CarLoadingScreen` — Primary actions `none` → `none — satisfied by the per-screen skeletons in Home, Browse and Library (D71)`.
- **19** `CarPlaybackErrorOverlay` — Primary actions `Try again, skip to next` → `Retry, Skip next (only with a next track, online), Dismiss`.

- [ ] **Step 2: PRD §9 and glossary**

A8 row becomes:

```
| **A8** | PlaybackError, NoConnection (behaviour), Loading (satisfied) — Downloads moved to A9 | A2 | Merged and device-verified — PR #<n>; `docs/AAOS_A8_VERIFICATION.md` |
```

Fill `<n>` once the PR exists (Task 7). Add directly below it:

```
| **A9** | Car downloads: `SongDownloadManager` into a shared module, local-URI resolution in shared code (restore included), parked-only download actions, Downloads screen, Library row | A8 | Not started |
```

Glossary: `| **A1–A8** | Implementation phases |` → `A1–A9`; `independent of A1–A8` → `A1–A9`. §1's "nine phases" sentence names A1–A8 plus Project B — make it `ten phases`, `Phases A2–A9 deliver the AAOS screens`.

- [ ] **Step 3: Screen contract rows**

In `AAOS_SCREEN_CONTRACT.md`:

- **15** — last column `A8` → `A9`.
- **16** — Content `Offline illustration/state, Retry, Browse Downloads` → `No screen: offline play fails fast into the error overlay; offline banner stays`; States `no network, retrying` → `offline`; Driving `Allowed; Browse Downloads remains available` → `Allowed`.
- **18** — Content → `Satisfied by per-screen skeletons (Home, Browse, Library); parked-only shimmer not built`.
- **19** — Content `Error message, Try again, Skip next, Dismiss` → `Error message, Retry, Skip next (next track exists and online), Dismiss`.

- [ ] **Step 4: D71**

Insert after D70's last line, before the blank line and `## Components`, copying the text from the spec's "Decisions to record" section verbatim as a `- **D71 — …**` bullet in the house format (two-space continuation indent, wrapped at 100).

- [ ] **Step 5: Commit**

```bash
git add docs/AAOS_PRD.md docs/AAOS_SCREEN_CONTRACT.md docs/aaos-DESIGN.md
git commit -m "A8: contract rows, the A9 phase, and D71" -m "NoConnection becomes a behaviour, Loading is recorded as satisfied, the
overlay row gains Skip next, and car downloads move to a new A9 phase."
```

---

### Task 6: Gate and device verification

**Files:**
- Create: `docs/AAOS_A8_VERIFICATION.md` — dated, in the shape of `docs/AAOS_A6_VERIFICATION.md` (read it first)

- [ ] **Step 1: Full gate**

Run: `./gradlew test detekt :app:assembleDebug :automotive:assembleOemDebug`
Expected: BUILD SUCCESSFUL. Record the test count. `git diff --stat main...HEAD -- app/` must print nothing.

- [ ] **Step 2: Emulator setup**

Read `docs/AAOS_DRIVING_STATE_TESTING.md`. Use the **`AAOS_AOSP_33_userdebug`** AVD (driving injection works there), **one emulator at a time**, driver is **user 10**. Install `oemDebug`. Wait for Compose to lay out before tapping — taps within a second of focus are swallowed.

- [ ] **Step 3: Establish an offline method that leaves the car stack alive**

**Do not use `svc wifi disable` / `svc data disable`** — on this emulator they crashed `car_service`, `CarLauncher` and `audioserver`.

Record `adb shell pidof car_service com.android.car.carlauncher audioserver`, then try:

```bash
adb shell cmd connectivity airplane-mode enable
```

Record the PIDs again. Pass: all three unchanged, and the app's offline banner appears. Restore with `airplane-mode disable`. If the stack crashes, try one alternative, then **stop and report** rather than trying a third — record what failed.

- [ ] **Step 4: Offline passes**

Each with a screenshot and the PIDs in the record:

1. Offline, tap a song → overlay at once, titled `No Connection`, Wi-Fi-off icon, **no Retry**, nothing starts.
2. Offline, shuffle a list → same.
3. Online, start a track; go offline mid-track → buffering, then the overlay **with** Retry. Back online, Retry → plays.
4. With a session saved, go offline, kill and relaunch (stop playback first, compare PIDs — see the emulator notes) → the restored session shows **no** overlay until play is pressed; pressing play offline → overlay with Retry.

- [ ] **Step 5: Skip next**

This is the spec's open item 1: does an errored player stay idle after a seek? Produce a non-network error with a next track queued. Suggested method — decide at execution time, and record which you used:

- `run-as com.example.nyasaplayer --user 10` into the app's Room database, inspect the schema with `sqlite3 <db> .schema` (do not assume table or column names), and point one cached song's audio URL at a URL that serves non-audio. Play it inside a list so a next track exists.
- Note in the record how the car **classified** that error: `onPlaybackError` treats `error.cause is IOException` as network, and Media3's parser exceptions are `IOException`s — so the overlay may say `No Connection` for a bad file. Record it; do not fix it here.

Pass: Skip next is visible, and tapping it plays the next track. If the next track does not start, the extra `play()` in `skipNextAfterError` is not reaching an idle player — report it.

Restore the edited row (or clear app data for user 10) afterwards.

- [ ] **Step 6: Driving pass**

Inject driving per `AAOS_DRIVING_STATE_TESTING.md` (`inject-vhal-event 0x11400400 8` plus `inject-continuous-events 0x11600207 40 -s 5 -d 60`; oracle `Current Driving State: 2` / `DO: true UxR: 255`). With the overlay up: Dismiss, Retry and Skip next all work; the overlay is not evicted.

- [ ] **Step 7: Contrast**

Read the measured-contrast table in `docs/aaos-DESIGN.md`. Sample the outlined pills' label and border on the overlay card from a screenshot and record the ratios. A gold label on gold has been shipped invisible in this repo before.

- [ ] **Step 8: Write and commit the record**

Everything observed goes in `docs/AAOS_A8_VERIFICATION.md`: date, AVD, user, PIDs, method used for offline and for the non-network error, pass/fail per step, screenshots path, and any finding. A step that could not be run is written down as not run, with why.

```bash
git add docs/AAOS_A8_VERIFICATION.md
git commit -m "A8: device verification record"
```

---

### Task 7: Review and PR

- [ ] **Step 1: Simplify before anything else**

Dispatch `pr-review-toolkit:code-simplifier` on `git diff main...HEAD`. Apply what holds; a declined suggestion gets a reason in the PR body or a `ponytail:` comment.

- [ ] **Step 2: Review**

Dispatch `correctness-reviewer` and `quality-reviewer` in parallel on the diff. Verify each finding against the code before acting. Re-run `./gradlew test detekt` after changes.

- [ ] **Step 3: Open the PR**

`gh pr create --base main`, title ≤72 chars, body: what changed per task, the verification record's link and summary, the `:app`-untouched statement with the empty `git diff --stat main...HEAD -- app/`, T28 as the mobile follow-up, and any declined review findings with reasons. No AI attribution.

- [ ] **Step 4: Fill the PR number**

Set `<n>` in PRD §9's A8 row to the new PR number, commit (`A8: link the PR from the phase table`), push.
