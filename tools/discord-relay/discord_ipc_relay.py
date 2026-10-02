#!/usr/bin/env python3
"""Discord IPC relay for flatpak apps.

Native Discord listens on $XDG_RUNTIME_DIR/discord-ipc-N, which games
running inside the Prism Launcher flatpak sandbox cannot reach (stale
symlinks). This relay listens inside Prism's app-private runtime dir
(visible on both sides) and forwards to the real socket.

Install: systemctl --user enable --now openorigins-discord-relay.service
"""
import os
import socket
import threading

RUNTIME = os.environ.get("XDG_RUNTIME_DIR", f"/run/user/{os.getuid()}")
LISTEN_PATH = os.path.join(
    RUNTIME, "app", "org.prismlauncher.PrismLauncher", "discord-ipc-0")
TARGET_PATH = os.path.join(RUNTIME, "discord-ipc-0")


def pipe(src, dst, rewrite_pid):
    try:
        buf = b""
        while True:
            chunk = src.recv(65536)
            if not chunk:
                break
            if rewrite_pid:
                buf += chunk
                buf = forward_frames(buf, dst)
            else:
                dst.sendall(chunk)
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            try:
                s.close()
            except OSError:
                pass


def forward_frames(buf, dst):
    import struct as _struct
    import json as _json
    while len(buf) >= 8:
        op, ln = _struct.unpack('<II', buf[:8])
        if len(buf) < 8 + ln:
            break
        body = buf[8:8 + ln]
        buf = buf[8 + ln:]
        if op == 1:
            try:
                obj = _json.loads(body.decode('utf-8'))
                if isinstance(obj, dict) and obj.get('cmd') == 'SET_ACTIVITY':
                    obj['args']['pid'] = os.getpid()
                    body = _json.dumps(obj).encode('utf-8')
                    ln = len(body)
            except (ValueError, KeyError, TypeError):
                pass
        dst.sendall(_struct.pack('<II', op, ln) + body)
    return buf


def handle(client):
    try:
        upstream = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        upstream.settimeout(5)
        upstream.connect(TARGET_PATH)
    except OSError:
        try:
            client.close()
        except OSError:
            pass
        return
    threading.Thread(target=pipe, args=(client, upstream, True), daemon=True).start()
    threading.Thread(target=pipe, args=(upstream, client, False), daemon=True).start()


def main():
    try:
        os.unlink(LISTEN_PATH)
    except OSError:
        pass
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(LISTEN_PATH)
    server.listen(16)
    print("relay listening on", LISTEN_PATH, "->", TARGET_PATH, flush=True)
    while True:
        client, _ = server.accept()
        threading.Thread(target=handle, args=(client,), daemon=True).start()


if __name__ == "__main__":
    main()
