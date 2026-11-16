#!/bin/sh
# 仅开发/仿真设备；安装测试APK并运行instrumentation，不启动Activity。
set -eu
adb_bin="${ANDROID_HOME:+$ANDROID_HOME/platform-tools/adb}"
if [ -z "$adb_bin" ]; then adb_bin=adb; fi
serial="${1:?Specify development device serial}"
sitl="${2:-false}"
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=$("$adb_bin" -s "$serial" shell am instrument -w -e lifecycle true -e sitl "$sitl" com.fairland.fgroundcontrol.test/com.fairland.fgroundcontrol.SdkProbeInstrumentation)
printf '%s\n' "$result"
printf '%s' "$result" | grep -q 'INSTRUMENTATION_CODE: -1'
