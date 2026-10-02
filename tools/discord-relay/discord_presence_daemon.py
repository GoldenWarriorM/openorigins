#!/usr/bin/env python3
"""OpenOrigins Discord presence daemon (host side).

The game runs inside the Prism Launcher flatpak sandbox where Discord's
IPC socket is unreachable with a matching peer pid, so the game never
talks IPC itself. Instead it writes the desired presence state to
$XDG_RUNTIME_DIR/app/org.prismlauncher.PrismLauncher/openorigins-presence.json
(a path visible on both sides), and this daemon - running on the host with
a stable pid - keeps one persistent Discord IPC connection and applies it.

File format (written by the mod, read here):
  {"details": str, "state": str, "large_image": str|None,
   "large_text": str|None, "start": int|0, "clear": bool}

Install: systemctl --user enable --now openorigins-discord-presence.service
"""
import json
import os
import socket
import struct
import time
import uuid

RUNTIME = os.environ.get("XDG_RUNTIME_DIR", f"/run/user/{os.getuid()}")
STATE_PATH = os.path.join(
    RUNTIME, "app", "org.prismlauncher.PrismLauncher", "openorigins-presence.json"
)
TARGET_PATH = os.path.join(RUNTIME, "discord-ipc-0")

CLIENT_ID = "1555715005708636250"


def frame(op, payload):
    return struct.pack("<II", op, len(payload)) + payload


def read_frame(sock):
    hdr = sock.recv(8)
    if len(hdr) < 8:
        raise ConnectionError("eof")
    op, ln = struct.unpack("<II", hdr)
    body = b""
    while len(body) < ln:
        chunk = sock.recv(ln - len(body))
        if not chunk:
            raise ConnectionError("eof")
        body += chunk
    return op, body


class Discord:
    def __init__(self):
        self.sock = None

    def ensure(self, client_id):
        if self.sock is not None:
            return True
        s = None
        try:
            s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            s.settimeout(5)
            s.connect(TARGET_PATH)
            s.sendall(frame(0, json.dumps({"v": 1, "client_id": client_id}).encode()))
            self.sock = s
            self.read_one()
            print("discord connected", flush=True)
            return True
        except (OSError, ConnectionError) as e:
            print("discord connect failed:", e, flush=True)
            try:
                if s is not None:
                    s.close()
            except OSError:
                pass
            self.sock = None
            return False

    def read_one(self):
        op, body = None, None
        hdr = self.sock.recv(8)
        if len(hdr) < 8:
            raise ConnectionError("eof")
        op, ln = struct.unpack("<II", hdr)
        while len(body or b"") < ln:
            chunk = self.sock.recv(ln - len((body or b"")))
            if not chunk:
                raise ConnectionError("eof")
            body = (body or b"") + chunk
        return op, body

    def send_activity(self, details, state, image, text, start):
        activity = {
            "type": 0,
            "status_display_type": 0,
            "details": details,
            "state": state,
        }
        if start:
            activity["timestamps"] = {"start": start}
        if image:
            activity["assets"] = {"large_image": image, "large_text": text}
        payload = {
            "cmd": "SET_ACTIVITY",
            "args": {"pid": os.getpid(), "activity": activity},
            "nonce": str(uuid.uuid4()),
        }
        raw = json.dumps(payload, ensure_ascii=False).encode()
        self.sock.sendall(frame(1, raw))

    def clear(self):
        payload = {
            "cmd": "SET_ACTIVITY",
            "args": {"pid": os.getpid(), "activity": None},
            "nonce": str(uuid.uuid4()),
        }
        raw = json.dumps(payload).encode()
        self.sock.sendall(frame(1, raw))

    def close(self):
        try:
            if self.sock is not None:
                self.sock.close()
        except OSError:
            pass
        self.sock = None


def read_state():
    try:
        with open(STATE_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def main():
    discord = Discord()
    last_sig = None
    failures = 0
    while True:
        try:
            state = read_state()
            if state is None:
                time.sleep(2)
                continue
            sig = json.dumps(state, sort_keys=True)
            if sig != last_sig:
                client_id = state.get("client_id") or CLIENT_ID
                applied = False
                if state.get("clear"):
                    if discord.ensure(client_id):
                        discord.clear()
                        print("presence cleared", flush=True)
                        applied = True
                else:
                    if discord.ensure(client_id):
                        discord.send_activity(
                            state.get("details", "Playing"),
                            state.get("state", "Origin Realms"),
                            state.get("large_image"),
                            state.get("large_text"),
                            state.get("start", 0),
                        )
                        print("presence applied:", state.get("details"), flush=True)
                        applied = True
                if applied:
                    last_sig = sig
                    failures = 0
            time.sleep(2)
        except (OSError, ConnectionError) as e:
            failures += 1
            print("discord link failed:", e, flush=True)
            discord.close()
            last_sig = None
            time.sleep(min(5 * failures, 30))
        except Exception as e:
            print("daemon error:", type(e).__name__, e, flush=True)
            time.sleep(5)


if __name__ == "__main__":
    main()
