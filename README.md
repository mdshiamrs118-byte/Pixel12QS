# Pixel 12 QS (LSPosed module)

Restyles the Android 10 quick settings tiles (tested target: Resurrection Remix 8.6.9) into
Pixel / Android 12 style pills: 2 columns, icon on the left, label and status on the right,
rounded tiles, blue active / dark gray inactive.

## Install
1. Download `Pixel12QS.apk` from the Actions artifact or the Releases page.
2. Install it, enable it in LSPosed, scope: **System UI** only.
3. Restart SystemUI (or reboot).

If something looks wrong, disable the module in LSPosed and restart SystemUI.
Check the LSPosed log for lines starting with `Pixel12QS`.

## Build
Push to GitHub. The workflow in `.github/workflows/android.yml` builds the APK.
Push a tag like `v1.0` to also create a GitHub Release.
