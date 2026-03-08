[app]

# (str) Title of your application
title = DissPair

# (str) Package name
package.name = disspair

# (str) Package domain (needed for android/ios packaging)
package.domain = org.rfcomm.auditor

# (str) Source code where the main.py lives
source.dir = .

# (list) Source files to include
source.include_exts = py,png,jpg,kv,atlas

# (str) Application versioning (Matches main.py UI)
version = 1.72

# (list) Application requirements
# pyjnius and android are required for JNI Baseband Reflection
requirements = python3,kivy,pyjnius,android

# (list) Supported orientations
orientation = portrait

#
# Android specific
#

# (bool) Indicate if the application should be fullscreen or not
fullscreen = 0

# (list) Permissions
# Exact permissions required for full Android 12+ BLE and Classic Bluetooth interactions
android.permissions = BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT

# (int) Target Android API
android.api = 33

# (int) Minimum API your APK will support.
android.minapi = 21

# (bool) Enable AndroidX support. Required for modern Android API 33+
android.enable_androidx = True

# (str) Android entry point
android.entrypoint = org.kivy.android.PythonActivity

# (list) The Android archs to build for
# Including both 64-bit and 32-bit ARM for maximum physical device compatibility
android.archs = arm64-v8a, armeabi-v7a

# (bool) enables Android auto backup feature
# Set to False for production security tools to prevent leaking local cache
android.allow_backup = False

# (str) The format used to package the app for release mode
android.release_artifact = apk

# ====================================================
# IMAGES CONFIGURATION
# ====================================================
# (str) Icon of the application
icon.filename = %(source.dir)s/disspair_logo.png

# (str) Presplash of the application (Loading Screen)
presplash.filename = %(source.dir)s/disspair_logo.png

# (str) Presplash background color (Hex Color - Matches Kivy Clearcolor)
presplash.color = #0A0A0F
# ====================================================

#
# Python for android (p4a) specific
#

p4a.branch = master

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug (with command output))
# Standard production log level
log_level = 1

# (int) Display warning if buildozer is run as root (0 = False, 1 = True)
warn_on_root = 1
