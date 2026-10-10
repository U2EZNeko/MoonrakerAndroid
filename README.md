# Keep Android Open

**https://keepandroidopen.org/**

Android should stay open. Google's planned developer-verification lockdown threatens sideloading, F-Droid, and hobby apps like this one. Read more and push back at [keepandroidopen.org](https://keepandroidopen.org/).

---

# Moonraker Android

A free, open-source Android client for [Moonraker](https://moonraker.readthedocs.io/) / Klipper printers.

The popular Moonraker app on Google Play locks camera streaming behind payments. **This one does not.** Cameras, dashboards, print controls, and the rest of the UI stay free — no subscriptions, no "supporter" tiers for basic monitoring.

## Features

- Multi-printer support with per-printer API keys
- Live cameras (MJPEG, snapshots, WebRTC where available) — **no paywall**
- Home dashboard as a **6-column grid** — add / move / resize widgets (cameras, toolheads, fans, …)
- Temperatures (live + history charts)
- Print upload / pause / resume / cancel / emergency stop
- Timelapse browser + download
- **G-code preview & live view** — file list, slicer thumbnails, toolpath canvas with live nozzle marker, **layer + time scrubbers** (Fluidd-style)
- Dashboard **G-code preview** card (same scrubbers)
- Home-screen **G-code preview widget** (current job thumbnail + progress)
- Optional **load current G-code on app start** (Settings → Behavior)
- Fan control
- G-code console
- Paxx12 / Snapmaker U1 firmware-config WebView
- Themes, accents, Material You, C/F temperature units
- Hide tabs into **More…** / reorder the bottom bar
- Home-screen camera snapshot widget

## Requirements

- Android 8.0+ (API 26)
- A printer running Moonraker (and usually Klipper)
- Network access to the printer (LAN or VPN)

## Build

```bash
# Debug APK
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release APK
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk  (or -unsigned if no keystore)
```

On Windows use `gradlew.bat`. Set `ANDROID_HOME` (or install Android Studio) so the Android SDK is available.

### Release signing (optional)

Create `keystore.properties` in the project root (gitignored):

```properties
storeFile=path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

If the file is present, `assembleRelease` signs automatically.

## Install

Sideload the APK, or install from a GitHub release when published. Prefer F-Droid-style distribution over Play Store lock-in where you can.

1. Add your printer host (and port, usually `7125`)
2. Optional: Moonraker API key, media port for webcams behind nginx
3. Open **Cameras** — they should just work
4. Optional: enable **Load current G-code on start** in Settings; add the **G-code preview** home-screen widget

## Privacy

- Talks only to the Moonraker hosts you configure
- No analytics SDK, no ads, no account, no cloud relay required

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Moonraker and Klipper are separate projects with their own licenses. This app is an independent client and is not affiliated with the Moonraker or Klipper authors.

## Contributing

Issues and PRs welcome. Please do not open PRs that add tracking, ads, or paywalled features.
