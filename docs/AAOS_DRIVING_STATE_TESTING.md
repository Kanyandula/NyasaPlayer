# AAOS Driving-State Testing

How to put the automotive emulator into a driving state so restriction behaviour can be
verified end to end, and how to read back what the platform actually thinks.

Established 2026-08-08 in the A1 slice (PRD open question **Q1**).

**Answer to Q1: yes, and it is scriptable — but only on a `userdebug` image.** Use the
`AAOS_AOSP_33_userdebug` AVD. The Play image cannot do it, and cannot honour
`distractionOptimized` either; see "Why not the Play image" below.

## The AVDs

| AVD | Build type | Display | Driving state | `distractionOptimized` |
|---|---|---|---|---|
| `AAOS_AOSP_33_userdebug` | `userdebug` | 1024 x 768px at 160dpi (1024 x 768dp) | scriptable over adb | **honoured** |
| `Automotive_Distant_Display_with_Google_Play` | `user` | 1080 x 600px at 120dpi (1440 x 800dp) | GUI panel only | **ignored** |

Use the first for anything touching driving state. The second is the layout reference head unit
(`aaos-DESIGN.md` §Units) and the place to check behaviour against a Play-like image.

`AAOS_AOSP_33_userdebug` was created with:

```bash
sdkmanager "system-images;android-33;android-automotive;x86_64"
avdmanager create avd -n AAOS_AOSP_33_userdebug \
  -k "system-images;android-33;android-automotive;x86_64" \
  -d automotive_1024p_landscape
```

Note this machine is Intel — `x86_64`, not `arm64-v8a`. The image is
`sdk_gcar_x86_64-userdebug`, ~5.7 GB. Modern `cmdline-tools` are installed at
`$ANDROID_HOME/cmdline-tools/latest`; the legacy `$ANDROID_HOME/tools/bin/sdkmanager` is
broken under the current JDK and should not be used.

Launch:

```bash
$ANDROID_HOME/emulator/emulator -avd AAOS_AOSP_33_userdebug -no-snapshot-load &
adb -s emulator-5554 wait-for-device
```

Always pass `-s emulator-5554`. A second device is often attached over the network, and a
bare `adb shell` then fails with "more than one device".

## Driving the vehicle state

```bash
# One-shot: gear into DRIVE, then a speed sample
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 8    # GEAR_SELECTION = DRIVE
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11600207 40   # PERF_VEHICLE_SPEED

# Hold MOVING for 60s at 5Hz - a single speed event decays back to IDLING
adb -s emulator-5554 shell cmd car_service inject-continuous-events 0x11600207 40 -s 5 -d 60

# Back to parked
adb -s emulator-5554 shell cmd car_service inject-vhal-event 0x11400400 4    # GEAR_SELECTION = PARK
```

A single speed injection moves the state to `MOVING` and then straight back to `IDLING`,
because nothing sustains the value. Use `inject-continuous-events` for anything that needs
the vehicle to stay moving while you interact.

`cmd car_service -h` lists the rest — `enable-uxr`, `day-night-mode`, `get-property-value`,
`garage-mode`.

## The oracles

```bash
# What driving state the vehicle is in, plus a transition log
adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService

# What restrictions that state produces
adb -s emulator-5554 shell dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:'

# Whether an activity is registered as distraction optimised
adb -s emulator-5554 shell cmd car_service get-do-activities com.example.nyasaplayer
```

`get-do-activities` is the direct answer for `distractionOptimized` — it does not require
provoking a block. Expected:

```
DO Activities for com.example.nyasaplayer
com.example.nyasaplayer.auto.ui.AutomotiveActivity
```

`Current Driving State` values: `0` parked · `1` idling · `2` moving.

### Expected output

| State | `CarDrivingStateService` | `CarUxRestrictionsManagerService` |
|---|---|---|
| Parked | `0` | `DO: false UxR: 0` |
| Idling | `1` | `DO: true UxR: 16` |
| Moving | `2` | `DO: true UxR: 255` |

`UxR: 255` is `0xff` — every modelled restriction at once:

| Flag | Value |
|---|---|
| `NO_DIALPAD` | 1 |
| `NO_FILTERING` | 2 |
| `LIMIT_STRING_LENGTH` | 4 |
| `NO_KEYBOARD` | 8 |
| `NO_VIDEO` | 16 |
| `LIMIT_CONTENT` | 32 |
| `NO_SETUP` | 64 |
| `NO_TEXT_MESSAGE` | 128 |

**Idling is not moving.** It reports `DO: true` with only `NO_VIDEO` set. Code must gate on
the individual flags, never on `isDistractionOptimized` alone — otherwise an idling vehicle
wrongly refuses settings and search. `gate()` (`auto/ui/navigation/GateResult.kt`) does this
correctly.

The `Port:` line carries a timestamp. An unchanged timestamp means your input never reached
the platform, which is easy to mistake for "the app ignored it".

### Caps this image reports

Screens must honour these, not invented values:

| Cap | Value |
|---|---|
| Max content depth | 3 |
| Max cumulative content items | 21 |
| Max string length | 120 |

Nothing in `cmd car_service` lowers these, so with a short list parked and driving render
identically and a cap check proves nothing. To see a list truncate, add `.coerceAtMost(3)` inside
`toUxState()` (`viewmodel/UxFlags.kt`) — the one function every cap passes through — in a local
build only, and revert it. Do not pad the real account with synthetic data instead.

## Why not the Play image

`Automotive_Distant_Display_with_Google_Play` is `ro.build.type=user`, and
`CarShellCommand` refuses every state-injecting command on non-`userdebug` builds. `adbd`
cannot be rooted around it.

| Attempt | Result |
|---|---|
| `cmd car_service inject-vhal-event …` | `SecurityException: requires non-user build` |
| `cmd car_service set-drivingstate moving` | same |
| `cmd car_service -h` | same — even help is gated |
| `dumpsys car_service inject-vhal-event …` | **silently ignored.** Exits 0, prints nothing, changes nothing |
| `adb root` | `adbd cannot run as root in production builds` |
| emulator console `car` command | does not exist — see `adb emu help` |
| emulator gRPC on `127.0.0.1:8554` | up, but token-authenticated |

Its only route is the GUI: **⋯ Extended controls → Car data**, set Speed and Gear. Gear
dominates in both directions — setting speed to 0 while still in Drive leaves the vehicle
`MOVING`, because the `moving` config's speed range starts at `0.0` inclusive.

### The Play image ignores `distractionOptimized`

The same `oem` APK behaves differently on the two images:

| Image | `get-do-activities` / block behaviour |
|---|---|
| `AAOS_AOSP_33_userdebug` | activity listed; app renders normally while moving |
| Play image | `is_root_activity_do=false`; platform shows "You can't use this feature while driving" |

On the Play image this was checked exhaustively and is **not** a manifest bug: the
declaration is present in the installed APK (`aapt2 dump xmltree` against
`/data/app/.../base.apk`), both `android:value="true"` (typed boolean) and a string-resource
reference were rejected identically, and a cold emulator restart with the app installed
before boot — so `car_service` parsed it fresh — still reported false. Every entry in that
image's `CarPackageManagerService` allowlist is a platform-signed system or Google package,
so a debug-signed third-party app appears unable to be distraction optimised there at all.

**Practical consequence:** verify all driving-state behaviour on the userdebug AVD. A block
screen on the Play image is expected and is not a regression.

### Do not use `adb reboot`

It restarts Android but leaves the emulator's VHAL bridge wedged: afterwards the *Car data*
panel silently stops reaching the platform. Symptom is the `Port:` timestamp never changing
and `CarDrivingStateService` logging only its boot entry. Reopening Extended Controls does
not fix it. Kill and relaunch the emulator process instead:

```bash
adb -s emulator-5554 emu kill
$ANDROID_HOME/emulator/emulator -avd AAOS_AOSP_33_userdebug -no-snapshot-load &
```

## Emulator traps

Each of these produced a false finding at least once.

- **Run one emulator at a time.** Two AAOS AVDs starve the guest on this host: frames drop in
  batches, `dumpsys window` reports `mCurrentFocus=null`, and taps are silently swallowed, so the
  app looks like it has a state bug. Before believing a UI-state finding, check `mCurrentFocus` and
  guest load. Below ~200 MB `MemAvailable`, ANR and performance findings stop being credible.
- **The driver is user 10.** `pm clear` targets user 0 and changes nothing observable; the app's
  data is under `/data/user/10/`, reached with `run-as com.example.nyasaplayer --user 10`.
- **Compare pids, not presence, when killing the app.** `am kill` is refused while playback holds
  the foreground service or a media browser is bound — the pid does not change and the test passes
  for the wrong reason. Stop playback and leave the template first. The process can also restart on
  its own within seconds, which is a new pid, not a refused kill.
- **`am force-stop` is not process death.** It takes the Activity with it and puts the package in
  the stopped state, so the media template cannot bind the service afterwards — a testing artefact,
  not product behaviour. `am stopservice` reports "Service stopped" while the session stays bound.
- **Backing out does not finish `AutomotiveActivity`** on this image: `dumpsys activity activities`
  still lists it, so `onCleared()` never runs. Anything triggered by a ViewModel clearing cannot be
  reproduced by hand here.
- **Go offline with `adb shell cmd connectivity airplane-mode enable`.** `svc wifi disable` and
  `svc data disable` crashed `car_service`, `CarLauncher` and `audioserver` during A8.
- **`dumpsys media_session` lists more than one session.** Read the block whose `ownerPid` is the
  app's current pid, and read enough lines: `queueTitle` sits eleven lines below `ownerPid`, and
  cutting it off looks exactly like an empty queue.
- **`uiautomator dump` fails silently while playback animates** and leaves the previous dump in
  place. Use screenshots and `dumpsys media_session` instead.
- **The catalogue has no albums**, and the download action lives only on the album screen. To
  exercise downloads, add a Firestore `albums` document — name, `artistId`/`artistName` matching an
  existing artist, `imageUrl`, `songIds` holding song `mediaId`s, `popularity`, `releaseDate` (the
  doc ID becomes the album ID) — with the owner's approval, and delete it afterwards.
