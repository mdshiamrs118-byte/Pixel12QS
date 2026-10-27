# Pixel 12 QS (LSPosed module)

Restyles the Android 10 quick settings tiles (target: Resurrection Remix 8.6.9) into
Pixel / Android 12 style pills.

## Use
1. Install the APK, enable it in LSPosed, scope: **System UI** only.
2. Open the **Pixel 12 QS** app. Change columns, tile height, roundness, padding,
   text size, colors and the icon circle. A live preview shows the result.
3. Tap **Apply** (needs root) to restart SystemUI, or reboot.

If something looks wrong, tap Reset in the app, or disable the module in LSPosed.
Check the LSPosed log for lines starting with `Pixel12QS`.

## Build
Push to GitHub. `.github/workflows/android.yml` builds the APK.
Push a tag like `v1.1` to also create a GitHub Release.
