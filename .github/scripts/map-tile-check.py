#!/usr/bin/env python3
"""Checks a web map tile (see MapTiles.java): zlib-compressed, starts with "WOTG" and format 1, holds 64 chunks of
which some have blocks, and every chunk's columns add up to its length. Prints how many blocks it holds.

Usage: map-tile-check.py <tile file>
"""
import struct
import sys
import zlib


def fail(message):
    print(f"::error::map tile: {message}")
    sys.exit(1)


data = zlib.decompress(open(sys.argv[1], "rb").read())
if data[:5] != b"WOTG\x01":
    fail(f"bad header {data[:5]!r}")
pos = 5
chunks = 0
blocks = 0
grass = 0
for _ in range(64):
    (length,) = struct.unpack_from(">I", data, pos)
    pos += 4
    if not length:
        continue
    chunk = data[pos:pos + length]
    pos += length
    counts = sum(chunk[i * 2 + 1] for i in range(256))
    if length != 512 + counts * 2:
        fail(f"chunk length {length} does not match its {counts} blocks")
    values = struct.unpack_from(f">{counts}H", chunk, 512)
    grass += sum(1 for v in values if v >> 4 == 2)
    blocks += counts
    chunks += 1
if pos != len(data):
    fail(f"{len(data) - pos} bytes left over")
if not chunks or not grass:
    fail(f"{chunks} chunks with {grass} grass blocks")
print(f"ok: map tile with {chunks} chunks, {blocks} blocks, {grass} of them grass")
