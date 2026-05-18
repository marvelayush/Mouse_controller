"""
AirMouse Server — Run this on your laptop.
Phone connects via browser → tilts phone → laptop cursor moves.

Usage:
    pip install -r requirements.txt
    python server.py
"""

import asyncio
import http.server
import json
import logging
import os
import socket
import ssl
import struct
import sys
import threading
import time
import webbrowser
from pathlib import Path

try:
    import pyautogui
    PYAUTOGUI_AVAILABLE = True
except:
    PYAUTOGUI_AVAILABLE = False
try:
    from pynput.mouse import Button, Controller as MouseCtrl
    from pynput.keyboard import Key, Controller as KeyCtrl

    _mouse = MouseCtrl()
    _kbd   = KeyCtrl()

    DISPLAY_AVAILABLE = True

except:
    DISPLAY_AVAILABLE = False

    class DummyMouse:
        position = (0, 0)

        def click(self, *args, **kwargs):
            pass

        def scroll(self, *args, **kwargs):
            pass

    class DummyKeyboard:
        def press(self, *args, **kwargs):
            pass

        def release(self, *args, **kwargs):
            pass

    class DummyButton:
        left = "left"
        right = "right"

    Button = DummyButton
    _mouse = DummyMouse()
    _kbd = DummyKeyboard()
import websockets
from websockets.server import serve


# ─── Configuration ────────────────────────────────────────────────────────────
HTTP_PORT = int(os.environ.get("PORT", 8443))
WS_PORT = HTTP_PORT   # WebSocket port
CERT_FILE  = Path(__file__).parent / "cert.pem"
KEY_FILE   = Path(__file__).parent / "key.pem"
STATIC_DIR = Path(__file__).parent / "static"

# Mouse movement tuning
SENSITIVITY      = 18.0   # pixels per degree
DEAD_ZONE        = 0.15   # degrees — ignore tiny shakes
SMOOTHING_ALPHA  = 0.85   # exponential moving average (0=laggy, 1=raw/instant)
MAX_DELTA_DEG    = 25.0   # clamp huge jumps (phone picked up / put down)

# ─── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(level=logging.INFO, format="%(asctime)s  %(message)s")
log = logging.getLogger("gyrocursor")

if PYAUTOGUI_AVAILABLE:
    pyautogui.FAILSAFE = False
    pyautogui.PAUSE = 0

# ─── TLS Certificate (self-signed) ────────────────────────────────────────────
def generate_cert():
    """Generate a self-signed cert so Chrome allows gyroscope access."""
    from cryptography import x509 
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import NameOID
    import datetime, ipaddress

    log.info("Generating self-signed TLS certificate …")
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)

    lan_ip = get_lan_ip()
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, u"AirMouse"),
    ])
    san = x509.SubjectAlternativeName([
        x509.DNSName(u"localhost"),
        x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
        x509.IPAddress(ipaddress.ip_address(lan_ip)),
    ])
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(datetime.datetime.utcnow())
        .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=3650))
        .add_extension(san, critical=False)
        .sign(key, hashes.SHA256())
    )

    CERT_FILE.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    KEY_FILE.write_bytes(key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.TraditionalOpenSSL,
        serialization.NoEncryption(),
    ))
    log.info(f"Certificate saved → {CERT_FILE}")


def ensure_cert():
    if not CERT_FILE.exists() or not KEY_FILE.exists():
        generate_cert()


# ─── Network helpers ──────────────────────────────────────────────────────────
def get_lan_ip() -> str:
    """Best-effort LAN IP detection."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def qr_ascii(text: str) -> str:
    """Generate a tiny ASCII QR code using qrcode if available, else skip."""
    try:
        import qrcode
        qr = qrcode.QRCode(border=1)
        qr.add_data(text)
        qr.make(fit=True)
        f = qr.make_image(fill_color="black", back_color="white")
        # Print as ASCII
        lines = []
        matrix = qr.get_matrix()
        for row in matrix:
            lines.append("".join("██" if cell else "  " for cell in row))
        return "\n".join(lines)
    except ImportError:
        return ""


# ─── HTTPS file server ────────────────────────────────────────────────────────
class HTTPSHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(STATIC_DIR), **kwargs)

    def log_message(self, format, *args):
        pass  # silence per-request logs

    def end_headers(self):
        # Inject the WS port so the HTML knows where to connect
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def do_GET(self):
        if self.path == "/" or self.path == "/index.html":
            # Serve with WS_PORT injected
            html_path = STATIC_DIR / "index.html"
            content = html_path.read_bytes().replace(
                b"__WS_PORT__", str(WS_PORT).encode()
            )
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)
        else:
            super().do_GET()


def start_https_server(ip: str):
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(str(CERT_FILE), str(KEY_FILE))

    server = http.server.HTTPServer(("0.0.0.0", HTTP_PORT), HTTPSHandler)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    log.info(f"HTTPS server running on port {HTTP_PORT}")
    server.serve_forever()


# ─── Mouse controller ─────────────────────────────────────────────────────────
class MouseController:
    def __init__(self):
        self.prev_alpha = None
        self.prev_beta  = None
        self.smooth_dx  = 0.0
        self.smooth_dy  = 0.0

    def reset(self):
        self.prev_alpha = None
        self.prev_beta  = None
        self.prev_gamma = None
        self.smooth_dx  = 0.0
        self.smooth_dy  = 0.0

    def move(self, alpha: float, beta: float, gamma: float):
        """
        Smooth blended control — works in any phone orientation:
          - Phone flat (screen up): rotating left/right → alpha (yaw) drives X
          - Phone upright (like normal use): tilting left/right → gamma drives X
          - Blend between the two based on how much the phone is tilted (beta)
          - Up/down always driven by beta (forward/back tilt)
        """
        if self.prev_alpha is None:
            self.prev_alpha = alpha
            self.prev_beta  = beta
            self.prev_gamma = gamma
            return

        # Alpha wraps 0-360 — handle wraparound correctly
        d_alpha = alpha - self.prev_alpha
        if d_alpha >  180: d_alpha -= 360
        if d_alpha < -180: d_alpha += 360

        d_beta  = beta  - self.prev_beta
        d_gamma = gamma - self.prev_gamma

        self.prev_alpha = alpha
        self.prev_beta  = beta
        self.prev_gamma = gamma

        # Clamp huge jumps (phone picked up / put down)
        d_alpha = max(-MAX_DELTA_DEG, min(MAX_DELTA_DEG, d_alpha))
        d_beta  = max(-MAX_DELTA_DEG, min(MAX_DELTA_DEG, d_beta))
        d_gamma = max(-MAX_DELTA_DEG, min(MAX_DELTA_DEG, d_gamma))

        # Dead zone
        if abs(d_alpha) < DEAD_ZONE: d_alpha = 0.0
        if abs(d_beta)  < DEAD_ZONE: d_beta  = 0.0
        if abs(d_gamma) < DEAD_ZONE: d_gamma = 0.0

        # ── Blend alpha and gamma for X axis ──────────────────────────────────
        # Use beta to smoothly blend: flat phone → alpha dominates, upright → gamma
        import math
        tilt_ratio = min(1.0, abs(beta) / 75.0)   # 0=flat, 1=upright (saturates at 75°)
        # alpha sign: rotating phone clockwise (right) increases alpha → cursor should go right
        # BUT on most Android devices alpha increases counter-clockwise, so negate it
        d_x_blended = (-d_alpha * (1.0 - tilt_ratio)) + (d_gamma * tilt_ratio)

        # Scale to pixels
        raw_dx =  d_x_blended * SENSITIVITY
        raw_dy = -d_beta      * SENSITIVITY

        # Exponential smoothing
        self.smooth_dx = SMOOTHING_ALPHA * raw_dx + (1 - SMOOTHING_ALPHA) * self.smooth_dx
        self.smooth_dy = SMOOTHING_ALPHA * raw_dy + (1 - SMOOTHING_ALPHA) * self.smooth_dy

        dx = int(self.smooth_dx)
        dy = int(self.smooth_dy)

        if dx != 0 or dy != 0:
            cur = _mouse.position
            if DISPLAY_AVAILABLE:
                _mouse.position = (cur[0] + dx, cur[1] + dy)

    def click(self, button: str = "left"):
        btn = Button.right if button == "right" else Button.left
        if DISPLAY_AVAILABLE:
            _mouse.click(btn)

    def double_click(self):
        if DISPLAY_AVAILABLE:
            _mouse.click(Button.left, 2)

    def right_click(self):
        if DISPLAY_AVAILABLE:
            _mouse.click(Button.right)

    def scroll(self, dy: int):
        if DISPLAY_AVAILABLE:
            _mouse.scroll(0, dy)

    def key_press(self, key: str):
        try:
            if DISPLAY_AVAILABLE:
                _kbd.press(key)
                _kbd.release(key)
        except Exception:
            pass


mouse = MouseController()

# ─── WebSocket handler ────────────────────────────────────────────────────────
connected_clients: set = set()

async def ws_handler(websocket):
    global mouse
    addr = websocket.remote_address
    log.info(f"Phone connected from {addr}")
    connected_clients.add(websocket)
    mouse.reset()

    try:
        async for raw in websocket:
            try:
                # ── Binary fast-path: Float32[alpha, beta, gamma] = 12 bytes ──
                if isinstance(raw, bytes):
                    if len(raw) == 12:
                        alpha, beta, gamma = struct.unpack('!fff', raw)
                        mouse.move(alpha, beta, gamma)
                    continue  # skip JSON parsing for binary messages

                # ── JSON fallback ─────────────────────────────────────────────
                msg = json.loads(raw)
                t = msg.get("type")

                if t == "move":
                    alpha = float(msg.get("alpha", 0))
                    beta  = float(msg.get("beta",  0))
                    gamma = float(msg.get("gamma", 0))
                    mouse.move(alpha, beta, gamma)

                elif t == "click":
                    btn = msg.get("button", "left")
                    if btn == "double":
                        mouse.double_click()
                    elif btn == "right":
                        mouse.right_click()
                    else:
                        mouse.click("left")

                elif t == "scroll":
                    dy = int(msg.get("dy", 0))
                    mouse.scroll(dy)

                elif t == "key":
                    key = msg.get("key", "")
                    if key:
                        mouse.key_press(key)

                elif t == "sensitivity":
                    global SENSITIVITY
                    SENSITIVITY = max(1.0, min(60.0, float(msg.get("value", SENSITIVITY))))

                elif t == "ping":
                    await websocket.send(json.dumps({"type": "pong"}))

            except (json.JSONDecodeError, ValueError, KeyError) as e:
                log.warning(f"Bad message: {e}")

    except websockets.exceptions.ConnectionClosed:
        pass
    finally:
        connected_clients.discard(websocket)
        mouse.reset()
        log.info(f"Phone disconnected from {addr}")


# ─── Main ─────────────────────────────────────────────────────────────────────
    def print_banner(ip: str):
        render_url = os.environ.get("RENDER_EXTERNAL_URL")

    if render_url:
        url = render_url
    else:
        url = f"https://{ip}:{HTTP_PORT}"
    print()
    print("╔══════════════════════════════════════════════════════════╗")
    print("║             🖱️  GyroCursor Server  🖱️                    ║")
    print("╠══════════════════════════════════════════════════════════╣")
    print(f"║  Open this URL on your phone's Chrome browser:           ║")
    print(f"║  👉  {url:<52} ║")
    print("╠══════════════════════════════════════════════════════════╣")
    print("║  ⚠️  Chrome will say 'Not private' — tap Advanced →       ║")
    print("║      Proceed to continue. (Self-signed cert, safe.)      ║")
    print("╠══════════════════════════════════════════════════════════╣")
    print("║  Both devices must be on the same WiFi network.          ║")
    print("╚══════════════════════════════════════════════════════════╝")
    print()

    qr = qr_ascii(url)
    if qr:
        print("  QR Code (scan with phone camera or Chrome):")
        for line in qr.split("\n"):
            print("  " + line)
        print()


async def main():
    ensure_cert()
    ip = get_lan_ip()
    print_banner(ip)

    # Start HTTPS server in background thread
    t = threading.Thread(target=start_https_server, args=(ip,), daemon=True)
    t.start()

    # WebSocket server (WSS)
    ssl_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_ctx.load_cert_chain(str(CERT_FILE), str(KEY_FILE))

    log.info(f"WebSocket server  → wss://{ip}:{WS_PORT}")
    log.info("Waiting for phone connection … (Ctrl+C to quit)")

    async with serve(ws_handler, "0.0.0.0", WS_PORT, ssl=ssl_ctx):
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("Server stopped.")
