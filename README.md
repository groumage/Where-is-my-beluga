# Where is my Beluga 🐳

Android app (Kotlin, Jetpack Compose) that shows the live position of the
Airbus **Beluga** on a map: the huge cargo aircraft (A330-743L BelugaXL) that
carry aircraft parts between Airbus plants.

## How it works

- The tracked fleet (`data/BelugaFleet.kt`) holds the 6 BelugaXL in service,
  identified by their ICAO24 transponder address (hex), looked up in the
  [OpenSky Network](https://opensky-network.org/) aircraft database. The
  original BelugaST, now retired, are not tracked.
- Every minute (and on demand with the ⟳ button), the app queries the public
  [adsb.lol](https://www.adsb.lol/) API for the position, altitude, speed and
  heading of each aircraft that is transmitting.
- At the top of the screen, the **Beluga in flight closest** to your position
  is highlighted. When no Beluga is flying, the card says so.
- The bottom panel lists the whole fleet: tapping an aircraft centres the map
  on it and opens its details, and the "All" card frames the whole fleet.
  Aircraft without signal are grouped into a single marker at their home base
  (Toulouse-Blagnac).
- The bell in the panel turns on **pass alerts**: about every 15 minutes, even
  with the app closed, the app extends the track of each Beluga in flight and
  sends a notification if one should pass within 5 km in the next 20 minutes.
  It checks again 2 minutes before the predicted pass.
- The app is in French when that is the phone's language, in English
  otherwise, and follows the system light/dark theme.
- The map is drawn by [MapLibre](https://maplibre.org) with
  [OpenFreeMap](https://openfreemap.org) tiles (OpenStreetMap data): no API
  key, no Google services.

## Building

You need the Android SDK and JDK 25 (the one bundled with Android Studio
works). The simplest way is to open the folder in **Android Studio** and click
▶.

From the command line, with a phone plugged in over USB and debugging enabled:

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
export JAVA_HOME=/opt/android-studio/jbr
./gradlew :app:installDebug        # build and install on the phone
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:lintDebug           # Android lint
./gradlew :app:installDebug -PfakeAdsbLol=off   # debug build with the real adsb.lol API
```

The debug build shows fake aircraft (`app/src/debug/assets/fake-adsblol/`) so
that testing does not use up the free adsb.lol quota (500 requests a day).
Scenarios: `-PfakeAdsbLol=flying` (default), `ground`, `approaching`,
`invalid`, `rate-limited`, `offline`, or `off` for the real API. Release
builds always use adsb.lol.

## Code layout

Everything is in `app/src/main/kotlin/com/whereismybeluga/`:

| Folder | Role |
|---|---|
| `data/` | Fleet, adsb.lol client (every received field is validated), request schedule, distances, pass prediction |
| `location/` | Approximate user location without Google services, and positions kept on the device |
| `alerts/` | Pass alerts: background check (WorkManager) and notifications |
| `map/` | MapLibre map and marker drawing |
| `ui/` | Compose screen, panels, theme, `FleetViewModel` |

## Known limitations

- Without a key, the adsb.lol API accepts 500 requests a day per IP address.
  At one request a minute, that is more than 8 hours of use a day; beyond
  that, the app shows when it will try again.
- adsb.lol data is licensed under ODbL 1.0: commercial use is allowed
  (advertising included) as long as the source is credited, which the app does
  at the bottom of the screen.
- A Beluga flying through an area without ADS-B coverage may briefly
  disappear from the map.
- Pass alerts cannot see a Beluga still on the ground: a take-off between two
  checks (about every 15 minutes, sometimes longer when the phone is idle) is
  not announced. A turn also throws the prediction off. And the position used
  is the last one found while the app was open, blurred to about 2 km by
  Android.

## Logo

The logo (a Beluga seen from the side) is drawn in `assets/source/beluga.svg`.
The same paths are used for the app icon
(`app/src/main/res/drawable/ic_launcher_foreground.xml`) and the map markers
(`ui/BelugaIcon.kt`). `assets/source/build-icons.sh` regenerates the Play
Store artwork (needs `rsvg-convert`).
