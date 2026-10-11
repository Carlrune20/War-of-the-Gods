#!/usr/bin/env python3
"""Checks the web map WebSocket: logs in with a session cookie, says hello and expects the game, the map tiles and
the live state, then sends an unknown command and expects an error reply. Without a cookie the upgrade must be
refused. Standard library only.

Usage: web-socket-check.py <host> <port> <session cookie value> <expected game number>
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
            print(f"ok: game #{game['number']} map {game['minX']},{game['minZ']} to {game['maxX']},{game['maxZ']}")
        elif kind == "tiles":
            if not message["tiles"]:
                continue
            print(f"ok: {len(message['tiles'])} map tiles")
        elif kind == "state":
            names = [team["name"] for team in message["teams"]]
            print(f"ok: live state with teams {names}")
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
    sock.close()


main()
