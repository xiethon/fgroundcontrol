#!/usr/bin/env python3
"""固定工具和输入哈希后生成Java，不在APP运行时解析XML。"""
from pathlib import Path
import hashlib, importlib.metadata, json, tempfile, shutil
from pymavlink.generator import mavgen
root = Path(__file__).resolve().parents[1]
m = json.loads((root/'protocol/manifest.json').read_text())
assert importlib.metadata.version('pymavlink') == '2.4.49'
for name, digest in m['sha256'].items():
    assert hashlib.sha256((root/'protocol/xml'/name).read_bytes()).hexdigest() == digest, name
with tempfile.TemporaryDirectory() as temp:
    assert mavgen.mavgen(mavgen.Opts(temp, wire_protocol='2.0', language='Java'), [str(root/'protocol/xml/common.xml')])
    shutil.copytree(temp, root/'app/src/main/java/com/MAVLink', dirs_exist_ok=True)
