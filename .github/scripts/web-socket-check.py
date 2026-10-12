#!/usr/bin/env python3
"""Checks the web map WebSocket: logs in with a session cookie, says hello and expects the game, the map tiles and
the live state with at least the expected number of units, then sends an unknown command and a unit order and
expects both to be refused, since the session is an observer's. Without a cookie the upgrade must be refused.
Standard library only.

Usage: web-socket-check.py <host> <port> <session cookie value> <expected game number> [expected units]
"""
import base64
import json
import os
import socket
import struct
import sys


def connect(host, port, cookie):
    sock = socket.create_connection((host, port), timeout=30)
    key = base64.b64encode(os.urandom(16)).decode()
    request = (
        "GET /ws HTTP/1.1\r\n"
        f"Host: {host}:{port}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        f"Origin: http://{host}:{port}\r\n"
    )
    if cookie:
        request += f"Cookie: wotg_session={cookie}\r\n"
    sock.sendall((request + "\r\n").encode())
    response = b""
    while b"\r\n\r\n" not in response:
        chunk = sock.recv(1)
        if not chunk:
            break
        response += chunk
    status = response.split(b"\r\n", 1)[0].decode()
    return sock, status


def read_exact(sock, count):
    data = b""
    while len(data) < count:
        chunk = sock.recv(count - len(data))
        if not chunk:
            raise RuntimeError("socket closed")
        data += chunk
    return data


def read_frame(sock):
    first, second = read_exact(sock, 2)
    opcode = first & 0x0F
    length = second & 0x7F
    if length == 126:
        (length,) = struct.unpack(">H", read_exact(sock, 2))
    elif length == 127:
        (length,) = struct.unpack(">Q", read_exact(sock, 8))
    return opcode, read_exact(sock, length)


def send_text(sock, text):
    payload = text.encode()
    mask = os.urandom(4)
    header = bytes([0x81])
    if len(payload) < 126:
        header += bytes([0x80 | len(payload)])
    else:
        header += bytes([0x80 | 126]) + struct.pack(">H", len(payload))
    masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
    sock.sendall(header + mask + masked)


def fail(message):
    print(f"::error::web socket: {message}")
    sys.exit(1)


def main():
    host, port, cookie, game_number = sys.argv[1], int(sys.argv[2]), sys.argv[3], int(sys.argv[4])
    expected_units = int(sys.argv[5]) if len(sys.argv) > 5 else 0

    sock, status = connect(host, port, None)
    sock.close()
    if " 403 " not in status + " ":
        fail(f"upgrade without a session was not refused: {status}")
    print("ok: socket without a session refused")

    sock, status = connect(host, port, cookie)
    if " 101 " not in status + " ":
        fail(f"upgrade failed: {status}")
    send_text(sock, json.dumps({"type": "hello"}))
    seen = set()
    unit_id = None
    while not {"game", "tiles", "state"} <= seen:
        opcode, payload = read_frame(sock)
        if opcode != 1:
            continue
        message = json.loads(payload)
        kind = message.get("type")
        if kind == "game":
            game = message.get("game")
            if not game or game.get("number") != game_number:
                fail(f"expected game #{game_number}, got {payload[:200]!r}")
            if not str(game.get("mapId", "")).startswith(f"{game_number}-"):
                fail(f"game message has no map id for game #{game_number}: {game.get('mapId')!r}")
            types = game.get("buildingTypes", {})
            for type_id in ("town_hall", "farm", "barracks"):
                shape = types.get(type_id)
                if not shape or len(shape["heights"]) != shape["width"] * shape["length"]:
                    fail(f"game message has no usable {type_id} building type")
            print(f"ok: game #{game['number']} map {game['minX']},{game['minZ']} to {game['maxX']},{game['maxZ']}")
        elif kind == "tiles":
            if not message["tiles"]:
                continue
            print(f"ok: {len(message['tiles'])} map tiles")
        elif kind == "state":
            names = [team["name"] for team in message["teams"]]
            units = message.get("units", [])
            if len(units) < expected_units:
                # Units may still be loading; wait for the next state.
                print(f"waiting: {len(units)} of {expected_units} units in the live state")
                continue
            for unit in units:
                for key in ("id", "team", "number", "x", "y", "z", "health", "maxHealth", "order"):
                    if key not in unit:
                        fail(f"unit without {key}: {unit}")
            if units:
                unit_id = units[0]["id"]
            halls = [b["team"] for b in message.get("buildings", []) if b["type"] == "town_hall" and b["complete"]]
            if len(halls) < len(names):
                print(f"waiting: town halls for {halls} of {names}")
                continue
            print(f"ok: live state with teams {names}, {len(units)} units, town halls for {halls}")
        seen.add(kind)

    send_text(sock, json.dumps({"type": "no-such-command", "id": 7}))
    while True:
        opcode, payload = read_frame(sock)
        if opcode != 1:
            continue
        message = json.loads(payload)
        if message.get("replyTo") == 7:
            if message.get("type") != "error":
                fail(f"unknown command got {payload[:200]!r}")
            print(f"ok: unknown command answered with: {message['error']}")
            break

    if unit_id:
        send_text(sock, json.dumps({"type": "units.move", "id": 8, "units": [unit_id], "x": 0, "z": 0}))
        while True:
            opcode, payload = read_frame(sock)
            if opcode != 1:
                continue
            message = json.loads(payload)
            if message.get("replyTo") == 8:
                if message.get("type") != "error" or "Observers" not in message.get("error", ""):
                    fail(f"an observer's unit order got {payload[:200]!r}")
                print(f"ok: observer's unit order refused with: {message['error']}")
                break
    sock.close()


main()
