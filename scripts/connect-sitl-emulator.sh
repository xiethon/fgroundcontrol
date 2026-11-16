#!/bin/sh
# 仅建立 UDP 网络映射，不启动 App、不操作飞控。
set -eu
adb_bin="${ANDROID_HOME:+$ANDROID_HOME/platform-tools/adb}"
if [ -z "$adb_bin" ]; then adb_bin=adb; fi
serial="${1:-emulator-5554}"
case "$serial" in emulator-*) ;; *) echo "Only Android emulator targets are supported" >&2; exit 1 ;; esac
if [ "$("$adb_bin" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]; then
    echo "Target is not an Android emulator" >&2; exit 1
fi
address=$("$adb_bin" -s "$serial" shell ip -4 addr show eth0)
if ! printf '%s' "$address" | grep -q 'inet 10.0.2.15/'; then
    if [ "${FGC_REPAIR_EMULATOR_NETWORK:-0}" = "1" ]; then
        "$adb_bin" -s "$serial" shell 'su 0 ip link set eth0 up && su 0 ip addr add 10.0.2.15/24 dev eth0'
    else
        echo "Emulator eth0/10.0.2.15 unavailable; UDP redir cannot deliver packets." >&2
        echo "For a rooted development emulator, rerun with FGC_REPAIR_EMULATOR_NETWORK=1." >&2
        exit 1
    fi
fi
existing=$("$adb_bin" -s "$serial" emu redir list)
if printf '%s' "$existing" | tr -d '\r' | grep -Eq 'udp:14550[[:space:]]+=>[[:space:]]+14540'; then
    echo "SITL UDP mapping already present"
else
    "$adb_bin" -s "$serial" emu redir add udp:14550:14540
fi
