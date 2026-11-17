#!/usr/bin/env python3
"""独立固定PX4 SIH实例重启回归；不改变用户运行目录或现有实例。"""
import argparse
import os
from pathlib import Path
import select
import shutil
import socket
import subprocess
import tempfile
import threading


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("serial")
    parser.add_argument("px4_root", type=Path)
    args = parser.parse_args()
    assert args.serial.startswith("emulator-")
    root = args.px4_root.resolve()
    sha = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    assert sha == "d6f12ad1c4f70ad3230afd7d86e971421e02fef4"
    assert not subprocess.check_output(["git", "-C", str(root), "diff", "HEAD", "--name-only", "--ignore-submodules"], text=True).strip()
    binary = root / "build/px4_sitl_default/bin/px4"
    assert binary.is_file()
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb")
    def call(*parts):
        return subprocess.check_output([adb, "-s", args.serial, *parts], text=True, timeout=120)
    assert call("shell", "getprop", "ro.kernel.qemu").strip() == "1"
    assert "inet 10.0.2.15/" in call("shell", "ip", "-4", "addr", "show", "eth0")
    assert "14561" not in call("emu", "redir", "list")
    run = Path(tempfile.mkdtemp(prefix="fgc-m2-px4-restart-"))
    shutil.copytree(root / "build/px4_sitl_default/etc", run / "etc", symlinks=False)
    mavlink = run / "etc/init.d-posix/px4-rc.mavlink"
    mavlink.write_text(mavlink.read_text().replace("mavlink start -x -u $udp_gcs_port_local -r 4000000 -f", "mavlink start -x -u $udp_gcs_port_local -o 14551 -t 127.0.0.1 -r 4000000 -f"))
    output = (run / "px4-console.log").open("w")
    process = None
    def start():
        nonlocal process
        process = subprocess.Popen([str(binary), "-i", "1", "-w", str(run), str(run / "etc")], stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, env={**os.environ, "PX4_SIM_MODEL": "sihsim_quadx", "PX4_SIMULATOR": "sihsim"}, text=True)
    def shutdown():
        nonlocal process
        if process and process.poll() is None:
            process.stdin.write("shutdown\n"); process.stdin.flush()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.terminate(); process.wait(timeout=10)
    data = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    control = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    data.bind(("127.0.0.1", 14551)); control.bind(("127.0.0.1", 14661))
    call("emu", "redir", "add", "udp:14561:14561")
    stop = threading.Event()
    errors = []
    def relay():
        try:
            while not stop.is_set():
                for sock in select.select([data, control], [], [], .1)[0]:
                    packet, peer = sock.recvfrom(65535)
                    if sock is control and packet == b"PHASE:disconnect":
                        shutdown()
                    elif sock is control and packet == b"PHASE:restart":
                        start()
                    elif sock is data and peer[1] == 18571:
                        data.sendto(packet, ("127.0.0.1", 14561))
                    else:
                        data.sendto(packet, ("127.0.0.1", 18571))
        except Exception as error:
            errors.append(repr(error))
    thread = threading.Thread(target=relay)
    try:
        start(); thread.start()
        result = call("shell", "am", "instrument", "-w", "-e", "stable_restart", "true", "com.fairland.fgroundcontrol.test/com.fairland.fgroundcontrol.SdkProbeInstrumentation")
        print(result)
        print("PX4 baseline:", sha, "Runtime:", run)
        assert not errors, errors
        assert "INSTRUMENTATION_CODE: -1" in result and "stable_restart=PASS" in result
    finally:
        stop.set()
        if thread.ident:
            thread.join(15)
        shutdown(); output.close(); data.close(); control.close()
        call("emu", "redir", "del", "udp:14561")


if __name__ == "__main__":
    main()
