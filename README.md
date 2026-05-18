# 🖱️ AirMouse

**Use your Android phone as a 3D wireless mouse** — tilt the phone to move your laptop's cursor, just like pointing a TV remote.

No APK install needed. Your phone just opens a URL in Chrome.

---

## Quick Start

### 1. Install Python dependencies (laptop)

```powershell
cd "AirMouse"
pip install -r requirements.txt
```

### 2. Start the server (laptop)

```powershell
python server.py
```

The terminal will print something like:
```
👉  https://192.168.1.42:8443
```

### 3. Connect your phone

1. Make sure your phone and laptop are on **the same WiFi network**
2. Open the printed URL in **Chrome** on your phone
3. Chrome shows "Your connection is not private" → tap **Advanced → Proceed to site**  
   *(This is because of the self-signed cert — normal for local network apps)*
4. The AirMouse UI opens on your phone
5. Tap **Connect** — the IP is auto-filled
6. Tap **Enable Gyroscope** if prompted
7. Now tilt the phone → cursor moves! ✅

---

## Controls

| Action | How |
|--------|-----|
| Move cursor | Tilt phone (like pointing a TV remote) |
| Left click | Tap **Left Click** button |
| Right click | Tap **Right Click** button |
| Double click | Tap **Double** button |
| Scroll | Tap **Scroll Up / Down** buttons |
| Pause tracking | Tap **Tracking ON** button |
| Adjust speed | Use the **Sensitivity** slider |

---

## Tips

- Hold the phone **parallel to the ground**, then tilt to move the cursor
- For **fine control**: lower sensitivity, slow tilts
- For **quick movement**: higher sensitivity, bold tilts
- Tap **Tracking ON/OFF** to pause when you need to reposition your hand
- The phone screen stays **awake** automatically (Wake Lock API)

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Can't connect | Check both devices are on same WiFi; firewall may block ports 8443/8765 — allow them in Windows Defender |
| Cursor jumps | Lower sensitivity slider; hold phone steadier or increase dead-zone in `server.py` |
| Chrome shows "Not private" | Normal! Tap Advanced → Proceed once |
| Gyroscope doesn't work | On Android Chrome it needs HTTPS — make sure you used `https://` not `http://` |

---

## Architecture

```
Phone (Chrome)                  Laptop (Python)
────────────────                ───────────────────────
DeviceOrientation API           server.py
  beta / gamma angles     →     websockets + pyautogui
  WebSocket (WSS:8765)          moves OS cursor
                          ←     HTTP page served HTTPS:8443
```

## Files

```
AirMouse/
├── server.py           ← Run this on your laptop
├── requirements.txt    ← pip install -r requirements.txt
├── static/
│   └── index.html      ← Phone web app (auto-served)
├── cert.pem            ← Auto-generated on first run
├── key.pem             ← Auto-generated on first run
└── README.md
```
