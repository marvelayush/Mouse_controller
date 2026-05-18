"""
GyroCursor UDP Server — WebTransport (QUIC/HTTP3) edition.
Zero TCP overhead: move data sent as unreliable UDP datagrams.

Usage:
    pip install -r requirements.txt
    python server.py
"""

import asyncio
import base64
import hashlib
import http.server
import json
import logging
import socket
import ssl
import struct
import threading
from pathlib import Path
import datetime

from pynput.mouse import Button, Controller as MouseCtrl
from pynput.keyboard import Controller as KeyCtrl

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
import ipaddress

from aioquic.asyncio import serve as quic_serve
from aioquic.asyncio.protocol import QuicConnectionProtocol
from aioquic.h3.connection import H3_ALPN, H3Connection
from aioquic.h3.events import (
    DatagramReceived,
    HeadersReceived,
    WebTransportStreamDataReceived,
)
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent

# ─── Configuration ─────────────────────────────────────────────────────────────
HTTP_PORT = 8543   # HTTPS — serves the phone web app
WT_PORT   = 8766   # WebTransport/QUIC (UDP) port
CERT_FILE = Path(__file__).parent / "cert.pem"
KEY_FILE  = Path(__file__).parent / "key.pem"
STATIC    = Path(__file__).parent / "static"
CERT_DAYS = 13     # WebTransport requires cert validity ≤ 14 days

# Mouse movement tuning
SENSITIVITY     = 18.0
DEAD_ZONE       = 0.15
SMOOTHING_ALPHA = 0.85
MAX_DELTA_DEG   = 25.0

# ─── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(level=logging.INFO, format="%(asctime)s  %(message)s")
log = logging.getLogger("gyrocursor-udp")

_mouse = MouseCtrl()
_kbd   = KeyCtrl()

# ─── Certificate ───────────────────────────────────────────────────────────────
def get_lan_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]; s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def generate_cert(lan_ip: str):
    """
    Short-lived ECDSA P-256 cert required for WebTransport serverCertificateHashes.
    Chrome requires:  ECDSA key (NOT RSA) + validity ≤ 14 days + SHA-256 hash of DER.
    """
    from cryptography.hazmat.primitives.asymmetric import ec
    log.info("Generating 13-day ECDSA P-256 cert for WebTransport…")

    # MUST be ECDSA P-256 — Chrome rejects RSA for serverCertificateHashes
    key = ec.generate_private_key(ec.SECP256R1())

    subject = issuer = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "GyroCursor-UDP")])
    san = x509.SubjectAlternativeName([
        x509.DNSName("localhost"),
        x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
        x509.IPAddress(ipaddress.ip_address(lan_ip)),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject).issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now)
        .not_valid_after(now + datetime.timedelta(days=CERT_DAYS))
        .add_extension(san, critical=False)
        .sign(key, hashes.SHA256())   # SHA-256 required
    )
    CERT_FILE.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    KEY_FILE.write_bytes(key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.TraditionalOpenSSL,
        serialization.NoEncryption(),
    ))
    log.info("ECDSA P-256 certificate generated.")
    return cert


def load_cert_object():
    from cryptography.x509 import load_pem_x509_certificate
    return load_pem_x509_certificate(CERT_FILE.read_bytes())


def cert_fingerprint(cert) -> str:
    """SHA-256 of full DER cert — used as serverCertificateHashes value."""
    der = cert.public_bytes(serialization.Encoding.DER)
    digest = hashlib.sha256(der).digest()
    return ",".join(str(b) for b in digest)


def cert_needs_regen(cert) -> bool:
    """Returns True if cert expires within 1 day."""
    remaining = cert.not_valid_after_utc.replace(tzinfo=None) - datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
    return remaining.days < 1


def ensure_cert(lan_ip: str):
    if not CERT_FILE.exists() or not KEY_FILE.exists():
        return generate_cert(lan_ip)
    cert = load_cert_object()
    if cert_needs_regen(cert):
        log.info("Cert expires soon — regenerating…")
        return generate_cert(lan_ip)
    return cert


# ─── Mouse controller ──────────────────────────────────────────────────────────
class MouseController:
    def __init__(self):
        self.prev_alpha = self.prev_beta = self.prev_gamma = None
        self.smooth_dx = self.smooth_dy = 0.0

    def reset(self):
        self.prev_alpha = self.prev_beta = self.prev_gamma = None
        self.smooth_dx = self.smooth_dy = 0.0

    def move(self, alpha: float, beta: float, gamma: float):
        if self.prev_alpha is None:
            self.prev_alpha, self.prev_beta, self.prev_gamma = alpha, beta, gamma
            return

        d_alpha = alpha - self.prev_alpha
        if d_alpha >  180: d_alpha -= 360
        if d_alpha < -180: d_alpha += 360
        d_beta  = beta  - self.prev_beta
        d_gamma = gamma - self.prev_gamma

        self.prev_alpha, self.prev_beta, self.prev_gamma = alpha, beta, gamma

        for d in [d_alpha, d_beta, d_gamma]:
            d = max(-MAX_DELTA_DEG, min(MAX_DELTA_DEG, d))

        d_alpha = max(-MAX_DELTA_DEG, min(MAX_DELTA_DEG, d_alpha))
        d_beta  = max(-MAX_DELTA_DEG, min(MAX_DELTA_DEG, d_beta))
        d_gamma = max(-MAX_DELTA_DEG, min(MAX_DELTA_DEG, d_gamma))

        if abs(d_alpha) < DEAD_ZONE: d_alpha = 0.0
        if abs(d_beta)  < DEAD_ZONE: d_beta  = 0.0
        if abs(d_gamma) < DEAD_ZONE: d_gamma = 0.0

        import math
        tilt = min(1.0, abs(beta) / 75.0)
        d_x = (-d_alpha * (1.0 - tilt)) + (d_gamma * tilt)

        raw_dx =  d_x   * SENSITIVITY
        raw_dy = -d_beta * SENSITIVITY

        self.smooth_dx = SMOOTHING_ALPHA * raw_dx + (1 - SMOOTHING_ALPHA) * self.smooth_dx
        self.smooth_dy = SMOOTHING_ALPHA * raw_dy + (1 - SMOOTHING_ALPHA) * self.smooth_dy
        dx, dy = int(self.smooth_dx), int(self.smooth_dy)
        if dx or dy:
            cur = _mouse.position
            _mouse.position = (cur[0] + dx, cur[1] + dy)

    def click(self, button="left"):
        _mouse.click(Button.right if button == "right" else Button.left)

    def double_click(self): _mouse.click(Button.left, 2)
    def scroll(self, dy: int): _mouse.scroll(0, dy)

    def key_press(self, key: str):
        try: _kbd.press(key); _kbd.release(key)
        except Exception: pass


def handle_control(msg: dict, mc: MouseController):
    global SENSITIVITY
    t = msg.get("type")
    if   t == "click":
        btn = msg.get("button", "left")
        if btn == "double": mc.double_click()
        elif btn == "right": mc.click("right")
        else: mc.click("left")
    elif t == "scroll":       mc.scroll(int(msg.get("dy", 0)))
    elif t == "key":          mc.key_press(msg.get("key", ""))
    elif t == "sensitivity":  SENSITIVITY = max(1.0, min(60.0, float(msg.get("value", SENSITIVITY))))


# ─── WebTransport protocol ─────────────────────────────────────────────────────
class GyroCursorProtocol(QuicConnectionProtocol):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._http: H3Connection | None = None
        self._sessions: dict[int, MouseController] = {}
        self._stream_buf: dict[int, bytes] = {}
        self._stream_writers: dict[int, int] = {}  # stream_id -> session_id mapping

    def quic_event_received(self, event: QuicEvent) -> None:
        if self._http is None:
            self._http = H3Connection(self._quic, enable_webtransport=True)
        for h3_event in self._http.handle_event(event):
            self._h3_event(h3_event)

    def _h3_event(self, event) -> None:
        if isinstance(event, HeadersReceived):
            hdrs = dict(event.headers)
            if (hdrs.get(b":method") == b"CONNECT" and
                    hdrs.get(b":protocol") == b"webtransport"):
                # Accept WebTransport session
                self._http.send_headers(
                    stream_id=event.stream_id,
                    headers=[(b":status", b"200"),
                             (b"access-control-allow-origin", b"*")],
                )
                mc = MouseController()
                self._sessions[event.stream_id] = mc
                self.transmit()
                log.info(f"WebTransport session {event.stream_id} opened "
                         f"from {self._quic._network_paths[0].addr}")

        elif isinstance(event, DatagramReceived):
            # Fast-path: 12-byte binary move packet
            if len(event.data) == 12:
                try:
                    alpha, beta, gamma = struct.unpack("!fff", event.data)
                    # Apply to first available session
                    for mc in self._sessions.values():
                        mc.move(alpha, beta, gamma)
                        break
                except struct.error:
                    pass

        elif isinstance(event, WebTransportStreamDataReceived):
            # Reliable control stream (JSON newline-delimited)
            stream_id = event.stream_id
            sid = event.session_id
            buf = self._stream_buf.get(stream_id, b"") + event.data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                if line:
                    try:
                        msg = json.loads(line.decode())
                        mc = self._sessions.get(sid, MouseController())
                        
                        # Handle ping/pong for latency measurement
                        if msg.get("type") == "ping":
                            # Echo back a pong message
                            try:
                                pong_msg = json.dumps({"type": "pong"}) + "\n"
                                log.info(f"[PING/PONG] Received ping from session {sid}, sending pong via stream {stream_id}")
                                # Send via QUIC connection directly
                                self._quic.send_stream_data(stream_id, pong_msg.encode())
                                self.transmit()
                                log.info(f"[PING/PONG] Pong sent successfully")
                            except Exception as e:
                                log.info(f"[PING/PONG] Failed to send pong: {e}")
                        else:
                            handle_control(msg, mc)
                    except (json.JSONDecodeError, UnicodeDecodeError):
                        pass
            self._stream_buf[stream_id] = buf

            if event.stream_ended:
                mc = self._sessions.pop(sid, None)
                if mc: mc.reset()
                self._stream_buf.pop(stream_id, None)
                log.info(f"WebTransport session {sid} closed")


# ─── HTTPS file server (serves the phone HTML) ─────────────────────────────────
_fingerprint_str = ""
_wt_port_str     = str(WT_PORT)


class HTTPSHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(STATIC), **kwargs)

    def log_message(self, *a): pass

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            html = (STATIC / "index.html").read_bytes()
            html = html.replace(b"__WT_PORT__",        _wt_port_str.encode())
            html = html.replace(b"__CERT_HASH__",      _fingerprint_str.encode())
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.wfile.write(html)
        else:
            super().do_GET()


def start_https_server():
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(str(CERT_FILE), str(KEY_FILE))
    srv = http.server.HTTPServer(("0.0.0.0", HTTP_PORT), HTTPSHandler)
    srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
    log.info(f"HTTPS (HTML) server → https://{get_lan_ip()}:{HTTP_PORT}")
    srv.serve_forever()


# ─── QR helper ─────────────────────────────────────────────────────────────────
def print_qr(url: str):
    try:
        import qrcode
        qr = qrcode.QRCode(border=1)
        qr.add_data(url); qr.make(fit=True)
        for row in qr.get_matrix():
            print("  " + "".join("██" if c else "  " for c in row))
        print()
    except ImportError:
        pass


# ─── Banner ────────────────────────────────────────────────────────────────────
def print_banner(ip: str):
    url = f"https://{ip}:{HTTP_PORT}"
    print()
    print("============================================================")
    print("         GyroCursor  UDP / WebTransport                     ")
    print("============================================================")
    print(f"Open on your phone (Chrome):                            ")
    print(f">>> {url}")
    print("============================================================")
    print("[OK] No cert warning! Uses WebTransport fingerprint.     ")
    print("[OK] UDP datagrams - minimum possible latency.           ")
    print("Both devices must be on the same WiFi.                  ")
    print("============================================================")
    print()
    print_qr(url)


# ─── Main ──────────────────────────────────────────────────────────────────────
# ─── Main ──────────────────────────────────────────────────────────────────────
async def main():
    global _fingerprint_str, _wt_port_str

    ip   = get_lan_ip()
    cert = ensure_cert(ip)
    _fingerprint_str = cert_fingerprint(cert)
    _wt_port_str     = str(WT_PORT)

    # Debug: show cert info
    log.info(f"Certificate fingerprint (SHA-256): {_fingerprint_str}")
    log.info(f"Certificate validity: {cert.not_valid_before_utc} → {cert.not_valid_after_utc}")
    
    # HTTPS server for serving HTML (background thread)
    threading.Thread(target=start_https_server, daemon=True).start()

    # WebTransport / QUIC server
    quic_cfg = QuicConfiguration(
        alpn_protocols=H3_ALPN,
        is_client=False,
        max_datagram_frame_size=65536,
    )
    quic_cfg.load_cert_chain(str(CERT_FILE), str(KEY_FILE))

    print_banner(ip)
    log.info(f"WebTransport server → wss/quic://{ip}:{WT_PORT}")
    log.info("Waiting for phone connection… (Ctrl+C to quit)")

    server = await quic_serve(
        "0.0.0.0", WT_PORT,
        configuration=quic_cfg,
        create_protocol=GyroCursorProtocol,
    )
    try:
        await asyncio.Future()   # run forever
    finally:
        server.close()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("Server stopped.")
