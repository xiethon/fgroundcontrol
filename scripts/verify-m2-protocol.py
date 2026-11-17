#!/usr/bin/env python3
"""独立标准MAVLink测试源；不启动UI，不连接真实飞控，不发送飞行操作。"""
import argparse
import io
import math
import os
import select
import socket
import subprocess
import threading
import time
from pathlib import Path
from pymavlink.dialects.v20 import common as mav


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("serial", help="仅允许开发Android模拟器")
    args = parser.parse_args()
    assert args.serial.startswith("emulator-"), "Only development emulators are supported"
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb")
    def call(*parts):
        return subprocess.check_output([adb, "-s", args.serial, *parts], text=True, timeout=120)
    assert call("shell", "getprop", "ro.kernel.qemu").strip() == "1"
    assert "inet 10.0.2.15/" in call("shell", "ip", "-4", "addr", "show", "eth0")
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("127.0.0.1", 14660))
    assert "14560" not in call("emu", "redir", "list"), "Test port already mapped"
    call("emu", "redir", "add", "udp:14560:14560")
    stop = threading.Event()
    failures = []
    phases = []
    def source():
        try:
            stream = io.BytesIO()
            codec = mav.MAVLink(stream, srcSystem=245, srcComponent=1)
            phase = "baseline"
            tick = 0
            queue = []
            boot = time.monotonic()
            while not stop.is_set():
                started = time.monotonic()
                while select.select([sock], [], [], 0)[0]:
                    data, _ = sock.recvfrom(65535)
                    if data.startswith(b"PHASE:"):
                        phase = data.decode().split(":", 1)[1]
                        phases.append(phase)
                        queue.clear()
                        if phase == "restart":
                            boot = time.monotonic()
                            codec.seq = 0
                if phase != "disconnect":
                    ms = int((time.monotonic() - boot) * 1000)
                    codec.heartbeat_send(mav.MAV_TYPE_QUADROTOR, mav.MAV_AUTOPILOT_PX4, 1, (3 << 24) | (4 << 16), mav.MAV_STATE_STANDBY)
                    codec.autopilot_version_send(0, (1 << 24) | (17 << 16), 0, 0, 0, [0]*8, [0]*8, [0]*8, 0, 0, 245)
                    head = stream.getvalue(); stream.seek(0); stream.truncate()
                    sock.sendto(head, ("127.0.0.1", 14560))
                    if phase != "stale":
                        codec.global_position_int_send(ms, 473977420, 85455940, 488125, 12500, 300, 400, -200, 1719)
                        codec.local_position_ned_send(ms, 0, 0, -12.5, 3, 4, -2)
                        r, p, y = 0.1, -0.2, 0.3
                        cr, sr, cp, sp, cy, sy = math.cos(r/2), math.sin(r/2), math.cos(p/2), math.sin(p/2), math.cos(y/2), math.sin(y/2)
                        codec.attitude_send(ms, r, p, y, 0, 0, 0)
                        codec.attitude_quaternion_send(ms, cr*cp*cy+sr*sp*sy, sr*cp*cy-cr*sp*sy, cr*sp*cy+sr*cp*sy, cr*cp*sy-sr*sp*cy, 0, 0, 0)
                        codec.gps_raw_int_send(ms*1000, 3, 473977420, 85455940, 488125, 100, 100, 500, 1719, 12)
                        codec.sys_status_send(mav.MAV_SYS_STATUS_SENSOR_VISION_POSITION, mav.MAV_SYS_STATUS_SENSOR_VISION_POSITION, mav.MAV_SYS_STATUS_SENSOR_VISION_POSITION, 100, 12000, 0, 66 if phase == "restart" else 55, 0, 0, 0, 0, 0, 0)
                        codec.battery_status_send(0, 0, 0, 2500, [12000]+[65535]*9, 0, 0, 0, 66 if phase == "restart" else 55)
                        codec.home_position_send(473977420, 85455940, 475625, 0, 0, 0, [1, 0, 0, 0], 0, 0, 0)
                        codec.statustext_send(mav.MAV_SEVERITY_INFO, b"FGC_PROTOCOL_FIXTURE")
                        packet = stream.getvalue(); stream.seek(0); stream.truncate()
                        if phase == "delay":
                            queue.append((started + 1, packet))
                        elif phase != "loss" or tick % 2 == 0:
                            # 相同值的重复/重排验证传输容错，不冒称拒绝飞控旧时间帧。
                            if phase == "reorder":
                                frames = []
                                decoder = mav.MAVLink(None)
                                for message in decoder.parse_buffer(packet) or []:
                                    frames.append(message.get_msgbuf())
                                for frame in reversed(frames):
                                    sock.sendto(frame, ("127.0.0.1", 14560))
                                    sock.sendto(frame, ("127.0.0.1", 14560))
                            else:
                                sock.sendto(packet, ("127.0.0.1", 14560))
                        while queue and queue[0][0] <= started:
                            sock.sendto(queue.pop(0)[1], ("127.0.0.1", 14560))
                tick += 1
                stop.wait(max(0, .2 - (time.monotonic() - started)))
        except Exception as error:
            failures.append(repr(error))
    thread = threading.Thread(target=source)
    thread.start()
    try:
        for apk in ["app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
            call("install", "-r", apk)
        result = call("shell", "am", "instrument", "-w", "-e", "protocol", "true", "com.fairland.fgroundcontrol.test/com.fairland.fgroundcontrol.SdkProbeInstrumentation")
        print(result)
        print("Fixture phases:", ", ".join(phases))
        assert not failures, failures
        assert "INSTRUMENTATION_CODE: -1" in result
        assert "protocol=PASS" in result
    finally:
        stop.set(); thread.join(3); sock.close()
        call("emu", "redir", "del", "udp:14560")


if __name__ == "__main__":
    main()
