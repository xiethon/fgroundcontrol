#!/bin/sh
# 独立验证基线；不启动仿真器、不发送飞行操作，不属于Android构建依赖。
set -eu
root="${FGC_PX4_STABLE_DIR:-/private/tmp/fgc-px4-v1.17.0-stable}"
expected=d6f12ad1c4f70ad3230afd7d86e971421e02fef4
if [ ! -e "$root" ]; then
    git -c http.lowSpeedLimit=1024 -c http.lowSpeedTime=60 clone --filter=blob:none --depth 1 --branch v1.17.0 https://github.com/PX4/PX4-Autopilot.git "$root"
fi
actual=$(git -C "$root" rev-parse HEAD)
[ "$actual" = "$expected" ] || { echo "Unexpected PX4 baseline: $actual" >&2; exit 1; }
[ -z "$(git -C "$root" diff HEAD --name-only --ignore-submodules)" ] || { echo "Tracked source changes present; refusing preparation" >&2; exit 1; }
git -C "$root" submodule update --init --recursive --depth 1 src/modules/mavlink/mavlink src/drivers/gps/devices src/lib/events/libevents src/lib/heatshrink/heatshrink src/modules/uxrce_dds_client/Micro-XRCE-DDS-Client
printf 'Pinned PX4 source: %s\n' "$expected"
printf 'Build-only command: cd "%s" && make px4_sitl_default\n' "$root"
