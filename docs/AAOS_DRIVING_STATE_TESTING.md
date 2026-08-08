# AAOS Driving-State Testing

How to put the automotive emulator into a driving state so restriction behaviour can be
verified end to end, and how to read back what the platform actually thinks.

Established 2026-08-08 as Task 1 of the A1 slice (PRD open question **Q1**).

**Answer to Q1: yes, but not over adb.** On the Play system image the driving state can only
be injected through the emulator's *Car data* GUI panel. Every adb path is blocked. This
makes the manual checklists in the implementation plans workable, but restriction testing
cannot be scripted or run in CI on this image.

## Environment

| | |
|---|---|
| AVD | `Automotive_Distant_Display_with_Google_Play` |
| API | 33 (Android 13) |
| Build type | `user` — this is the whole problem |
| Serial | `emulator-5554` (pass `-s` if another device is attached) |

Launch:

```bash
$ANDROID_HOME/emulator/emulator -avd Automotive_Distant_Display_with_Google_Play -no-snapshot-load &
adb wait-for-device
adb -s emulator-5554 shell getprop sys.boot_completed   # expect: 1
```

## The recipe

The emulator's *Car data* panel writes VHAL properties over the emulator's gRPC channel
rather than through `car_service`'s shell, which is why it works where adb does not.

1. Click **⋯ (Extended controls)** on the emulator toolbar.
2. Select **Car data** in the left sidebar.
3. **To start driving:** set **Speed** to `40` and apply.
4. **To return to parked:** set **Gear** to **Park** and apply.

### Setting speed back to 0 does NOT park the vehicle

This costs time if you do not know it. The `moving` restriction config declares its speed
range as `0.0 - 5.0`, **inclusive of zero**, so a vehicle in gear Drive at speed 0 is still
classified `MOVING` and stays fully restricted. Only a gear change to Park transitions the
state machine back.

Observed: after applying speed `0`, the state was unchanged and no new event was logged at
all — same restriction flags, same timestamp. Applying gear Park then produced
`2 → 1 → 0` immediately.

## The oracle

Two services. Read both — the first says what the platform decided, the second says what the
app will be told.

```bash
# What driving state the vehicle is in, plus a transition log
adb -s emulator-5554 shell dumpsys car_service --services CarDrivingStateService

# What restrictions that state produces
adb -s emulator-5554 shell dumpsys car_service --services CarUxRestrictionsManagerService | grep '^Port:'
```

`Current Driving State` values: `0` parked · `1` idling · `2` moving.

### Expected output

Parked:

```
Current Driving State: 0
Port: 0x00 UXR: DO: false UxR: 0 time: 1165332839060
```

Driving (speed 40):

```
Current Driving State: 2
Port: 0x00 UXR: DO: true UxR: 255 time: 913009728845
```

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

The transition log is the better signal that something happened, because the `Port:` line
also carries a timestamp — an unchanged timestamp means your input never reached the
platform, which is easy to mistake for "the app ignored it".

### Caps this image reports

From the same dump. Screens must honour these, not invented values:

| Cap | Value |
|---|---|
| Max content depth | 3 |
| Max cumulative content items | 21 |
| Max string length | 120 |

Note the configured `idling` state carries only `0x10` (`NO_VIDEO`) with `DO: true`, so
idling is far less restricted than moving. The app must not assume `isDistractionOptimized`
implies the full flag set.

## What does not work, and why

The AVD is a `user` build. `CarShellCommand` refuses every state-injecting command on
non-`userdebug`/`eng` builds, and `adbd` cannot be rooted to get around it.

| Attempt | Result |
|---|---|
| `cmd car_service inject-vhal-event 0x11600207 40` | `SecurityException: The command 'inject-vhal-event' requires non-user build` |
| `cmd car_service inject-vhal-event PERF_VEHICLE_SPEED 40` | same |
| `cmd car_service inject-vhal-event GEAR_SELECTION 8` | same |
| `cmd car_service set-drivingstate moving` | same |
| `cmd car_service -h` | same — even help is gated |
| `dumpsys car_service inject-vhal-event PERF_VEHICLE_SPEED 40` | **silently ignored.** Exits 0, prints nothing, changes nothing. The most dangerous of the lot: it looks like it worked |
| `adb root` | `adbd cannot run as root in production builds` |
| emulator console `car` command | does not exist — see `adb emu help` |
| emulator gRPC on `127.0.0.1:8554` | up, but token-authenticated and `grpcurl` is not installed |

## Open issue: the launcher is still blocked while driving

**Unresolved as of 2026-08-08.** With the vehicle in motion the platform replaces the app
with its own "You can't use this feature while driving / Close app" screen, so none of the
restriction layer in `:automotive` gets a chance to run. Every driving-state behaviour is
therefore still verified by unit tests only.

`CarPackageManagerService` is the oracle:

```bash
adb -s emulator-5554 shell dumpsys car_service --services CarPackageManagerService \
  | grep -o 'is_root_activity_do=[a-z]*'
```

It reports `is_root_activity_do=false` even though the shipped manifest carries the
declaration. Confirmed against the **installed** APK, not just the build output:

```bash
aapt2 dump xmltree --file AndroidManifest.xml base.apk | grep -A1 '"distractionOptimized"'
```

Two encodings were tried, both rejected:

| `android:value` | How aapt encodes it | Result |
|---|---|---|
| `"true"` (the form in Google's docs) | typed boolean, no `Raw:` string | `is_root_activity_do=false` |
| `"@string/…"` resolving to `true` | string reference | `is_root_activity_do=false` |

The manifest keeps the documented `"true"` form. Things not yet ruled out, roughly in order
of likelihood: `CarPackageManagerService` caching its distraction-optimised activity list
until reboot rather than on package replace; the Play image requiring the app to appear in a
vendor allowlist (`Allowlist string in resource` in that same dump) regardless of metadata;
or a signature requirement on this image. Reboot the emulator and re-check before
investigating further — it is the cheapest of the three.

## If you need scripted injection

Install a non-Play **AOSP automotive** system image, which ships as `userdebug`, and create a
second AVD from it. On that image the `inject-vhal-event` commands above work as documented
and restriction testing becomes scriptable. Not done here — the GUI recipe was sufficient for
A1, and the download is multi-gigabyte.

Note that `$ANDROID_HOME/tools/bin/sdkmanager` on this machine is the legacy copy and crashes
under the current JDK (`NoClassDefFoundError: javax/xml/bind/annotation/XmlSchema`); modern
`cmdline-tools` are not installed. Install those first.
