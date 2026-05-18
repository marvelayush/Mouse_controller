# AirMouse Android Client

Gyroscope-based cursor control — Android companion to your Python WebSocket server.
Sends the same `{"alpha": float, "beta": float, "gamma": float}` JSON your Chrome app sends.

## Setup in Android Studio

1. Open Android Studio → **File > Open** → select the `AirMouseClient` folder
2. Let Gradle sync (it downloads OkHttp + Gson automatically)
3. Connect your Android phone via USB or use an emulator
4. Hit **Run ▶**

## Usage

1. Start your Python server on your laptop:
   ```
   python server.py  # port 8765
   ```
2. Make sure your laptop and phone are on the **same Wi-Fi network**
3. Find your laptop's local IP:
   - Windows: `ipconfig` → look for IPv4 Address
   - Mac/Linux: `ifconfig` or `ip addr`
4. In the app: enter the IP + port → tap **CONNECT**
5. Tilt your phone to move the cursor

## Protocol

Exactly matches your Chrome DeviceOrientationEvent:
```json
{"alpha": 180.0, "beta": 45.0, "gamma": -10.0}
```

Click events are extended with a `type` field:
```json
{"type": "left_click"}
{"type": "right_click"}
```
Add handling for these in your Python server.

## Architecture

```
Android Phone
├── GyroscopeManager.kt  — reads TYPE_ROTATION_VECTOR, converts to alpha/beta/gamma
├── AirMouseWebSocket.kt — OkHttp WebSocket, handles ws:// and wss://
└── MainActivity.kt      — UI, sensitivity, send rate throttling

Python Server (existing)
└── port 8765 WebSocket  — receives JSON, moves cursor
```

## Sensor Mapping

Android `TYPE_ROTATION_VECTOR` → `SensorManager.getOrientation()` → Euler angles:

| Browser          | Android           | Meaning                 |
|------------------|-------------------|-------------------------|
| `alpha` (0–360°) | azimuth           | Compass heading (Z-axis)|
| `beta` (-180–180°)| pitch            | Front/back tilt (X-axis)|
| `gamma` (-90–90°)| roll             | Left/right tilt (Y-axis)|

## Features

- **ws:// and wss://** — toggle between plain and TLS (your server uses self-signed cert)
- **Sensitivity** — 0.5x to 4.5x multiplier on all axes
- **Send rate** — 10 to 100 Hz (independent of sensor sampling rate)
- **Calibrate** — zero current orientation as neutral position
- **Left/Right click** — sends click events via WebSocket
- **Latency display** — shows ping round-trip time
- **Persists settings** — IP, port, sensitivity saved across restarts
