#!/usr/bin/env python3
"""检查APK中64位ELF的PT_LOAD对齐；ZIP对齐另用zipalign -c -P 16。"""
import struct
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as archive:
    count = 0
    for name in archive.namelist():
        if not name.endswith('.so') or not any('/' + abi + '/' in name for abi in ('arm64-v8a', 'x86_64')):
            continue
        blob = archive.read(name)
        assert blob[:6] == b'\x7fELF\x02\x01', name
        offset = struct.unpack_from('<Q', blob, 32)[0]
        stride, length = struct.unpack_from('<HH', blob, 54)
        alignments = [struct.unpack_from('<Q', blob, offset + i * stride + 48)[0]
                      for i in range(length) if struct.unpack_from('<I', blob, offset + i * stride)[0] == 1]
        assert alignments and min(alignments) >= 16384, (name, alignments)
        print(name, 'PASS', min(alignments))
        count += 1
    assert count, 'No 64-bit ELF libraries found'
