"""
GyroCursor — Raw UDP Server (works with the Flutter Android app)
No TLS, no WebSocket, no overhead. Just plain UDP packets.

Usage:
    pip install pynput
    python server_udp.py
"""

import asyncio
import json
import logging
import socket
import struct
import threading
from pathlib import Path
import math

from pynput.mouse import Button, Controller as MouseCtrl
from pynput.keyboard import Controller as KeyCtrl

# ─── Config ────────────────────────────────────────────────────────────────────
UDP_PORT        = 8766
SENSITIVITY     = 600   # pixels per rad/s  (tune this)
DEAD_ZONE_RADS  = 0.015  # rad/s — ignore tiny sensor noise
SMOOTHING       = 0.80   # EMA (0=none, 1=fully smooth)
MAX_RADS        = 8.0    # clamp crazy jumps

logging.basicConfig(level=logging.INFO, format="%(asctime)s  %(message)s")
log = logging.getLogger("gyrocursor")

_mouse = MouseCtrl()
_kbd   = KeyCtrl()

# ─── Mouse controller ──────────────────────────────────────────────────────────
class MouseController:
    def __init__(self):
        self.smooth_dx = 0.0
        self.smooth_dy = 0.0
        self.last_time = None

    def move_gyro(self, gx: float, gy: float, gz: float, dt: float):
        """
        gx = angular velocity around X axis (pitch) → vertical
        gz = angular velocity around Z axis (yaw)   → horizontal
        dt = seconds since last packet
        """
        # Clamp
        gx = max(-MAX_RADS, min(MAX_RADS, gx))
        gz = max(-MAX_RADS, min(MAX_RADS, gz))

        # Dead zone
        if abs(gz) < DEAD_ZONE_RADS: gz = 0.0
        if abs(gx) < DEAD_ZONE_RADS: gx = 0.0

        # Convert rad/s → pixels (scale by dt for frame-rate independence)
        raw_dx = -gz * SENSITIVITY * dt   # negate: CW rotation → right
        raw_dy =  gx * SENSITIVITY * dt   # tilt top away → up (positive gx)

        # Smooth
        self.smooth_dx = SMOOTHING * self.smooth_dx + (1 - SMOOTHING) * raw_dx
        self.smooth_dy = SMOOTHING * self.smooth_dy + (1 - SMOOTHING) * raw_dy

        dx, dy = int(self.smooth_dx), int(self.smooth_dy)
        if dx or dy:
            cur = _mouse.position
            _mouse.position = (cur[0] + dx, cur[1] + dy)

    def click(self, button="left"):
        _mouse.click(Button.right if button == "right" else Button.left)

    def double_click(self): _mouse.click(Button.left, 2)
    def right_click(self):  _mouse.click(Button.right)
    def scroll(self, dy: int): _mouse.scroll(0, dy)


mctl = MouseController()

# ─── Packet parsing ────────────────────────────────────────────────────────────
LAST_RECV: dict[str, float] = {}   # addr → timestamp

def handle_packet(data: bytes, addr: tuple, now: float):
    key = f"{addr[0]}:{addr[1]}"

    if len(data) == 12:
        # Move packet: float32 gx, gy, gz  (big-endian)
        gx, gy, gz = struct.unpack("!fff", data)
        dt = now - LAST_RECV.get(key, now - 0.016)
        dt = max(0.001, min(0.1, dt))   # clamp between 1ms and 100ms
        LAST_RECV[key] = now
        mctl.move_gyro(gx, gy, gz, dt)

    elif len(data) > 12:
        # Control packet: JSON string
        LAST_RECV[key] = now
        try:
            msg = json.loads(data.decode("utf-8"))
            t = msg.get("type")
            if   t == "click":
                btn = msg.get("button", "left")
                if btn == "double": mctl.double_click()
                elif btn == "right": mctl.right_click()
                else: mctl.click()
            elif t == "scroll": mctl.scroll(int(msg.get("dy", 0)))
            elif t == "sensitivity":
                global SENSITIVITY
                SENSITIVITY = max(50.0, min(3000.0, float(msg.get("value", SENSITIVITY))))
                log.info(f"Sensitivity set to {SENSITIVITY}")
        except (json.JSONDecodeError, UnicodeDecodeError):
            pass


# ─── UDP server ────────────────────────────────────────────────────────────────
def get_lan_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80)); ip = s.getsockname()[0]; s.close(); return ip
    except Exception: return "127.0.0.1"


def run_server():
    ip = get_lan_ip()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 65536)
    sock.bind(("0.0.0.0", UDP_PORT))

    print()
    print("╔══════════════════════════════════════════════════════════╗")
    print("║       🚀  GyroCursor  Raw UDP (Flutter App)  🚀          ║")
    print("╠══════════════════════════════════════════════════════════╣")
    print(f"║  Enter this IP in the Flutter app on your phone:         ║")
    print(f"║  📱  IP:    {ip:<47}║")
    print(f"║  🔌  Port:  {UDP_PORT:<47}║")
    print("╠══════════════════════════════════════════════════════════╣")
    print("║  No TLS, no cert, no browser — just open the app!       ║")
    print("╚══════════════════════════════════════════════════════════╝")
    print()
    log.info(f"Listening on UDP 0.0.0.0:{UDP_PORT} … (Ctrl+C to quit)")

    import time
    try:
        while True:
            data, addr = sock.recvfrom(1024)
            now = time.monotonic()
            handle_packet(data, addr, now)
    except KeyboardInterrupt:
        log.info("Server stopped.")
    finally:
        sock.close()


if __name__ == "__main__":
    run_server()
