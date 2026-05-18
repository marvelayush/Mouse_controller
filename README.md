# 🖱️ Mouse_controller

A futuristic real-time motion-controlled cursor platform that transforms your smartphone into a wireless gyroscopic mouse using motion sensors, WebSockets, FastAPI, Android integration, and a modern Next.js frontend.

---

# 🚀 Overview

Mouse_controller is a full-stack motion intelligence platform that allows users to control a desktop cursor using smartphone gyroscope motion in real time.

The system combines:

* Smartphone motion sensors
* Real-time WebSocket communication
* QR-based device pairing
* FastAPI backend services
* Android applications
* Futuristic Next.js web interface
* Live telemetry dashboards
* Low-latency cursor rendering

The project was designed as a next-generation wireless interaction system inspired by motion-based control technologies.

---

# ✨ Features

## 🎯 Real-Time Gyroscope Tracking

Control your desktop cursor by tilting your smartphone.

## 📡 WebSocket Communication

Ultra low-latency communication using secure WebSockets.

## 📱 QR-Based Pairing

Instant device connection through dynamically generated QR codes.

## 🌐 Modern Frontend Website

Production-ready futuristic frontend built using:

* Next.js
* React
* Tailwind CSS
* Framer Motion

## 📊 Live Telemetry Dashboard

Monitor:

* Device connection state
* Motion sensor values
* Latency
* Battery state
* Signal strength

## 🔒 Secure HTTPS + WSS Support

Supports secure browser sensor access using HTTPS and WSS.

## 🤖 Android Integration

Includes Android-based motion controller applications.

## ⚡ Cross-Platform Architecture

Works across:

* Windows
* Android
* Modern browsers

---

# 🏗️ System Architecture

```text
┌─────────────────────┐
│ Smartphone Client   │
│ Gyroscope Sensors   │
└─────────┬───────────┘
          │
          │ Motion Data
          ▼
┌─────────────────────┐
│ WebSocket Server    │
│ FastAPI Backend     │
└─────────┬───────────┘
          │
          │ Real-Time Events
          ▼
┌─────────────────────┐
│ Desktop Frontend    │
│ Next.js Website     │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│ OS Cursor Control   │
│ pyautogui           │
└─────────────────────┘
```

---

# 🧠 Tech Stack

## Frontend

* Next.js
* React
* Tailwind CSS
* Framer Motion
* JavaScript

## Backend

* FastAPI
* Python
* WebSockets
* pyautogui
* QR Code Generation

## Mobile

* Android Studio
* Kotlin
* Motion Sensors API

## Deployment

* Vercel
* Render / Railway
* GitHub

---

# 📂 Project Structure

```text
GYROCURSOR-MAIN/
│
├── AirMouse/                 # Python motion backend
├── main_website/             # Next.js futuristic frontend
├── CursorBrowser/            # Android browser controller
├── AirMouseClient/           # Android motion client
├── gyroTCP/                  # TCP experiments
├── UDP/                      # UDP communication experiments
├── a2-a7/                    # Research & development modules
├── README.md
├── pp.apk
└── wss.apk
```

---

# 🌐 Frontend Website

The frontend website includes:

* Futuristic command-center UI
* Dynamic QR onboarding panel
* Real-time telemetry dashboard
* Animated connection states
* Motion visualizations
* Responsive cyber-industrial design
* Framer Motion animations
* Glassmorphism effects
* Live WebSocket integration

---

# 📱 Mobile Workflow

1. Open the Mouse_controller desktop website
2. Scan the generated QR code
3. Connect smartphone sensors
4. Start streaming gyroscope data
5. Control desktop cursor in real time

---

# ⚙️ Local Development

## Backend Setup

```bash
cd AirMouse
pip install -r requirements.txt
python server.py
```

The server starts locally and generates:

* HTTPS endpoint
* QR pairing system
* WebSocket connection server

---

## Frontend Setup

```bash
cd main_website
npm install
npm run dev
```

Frontend runs locally using Next.js.

---

# 🔌 Environment Variables

Frontend uses:

```env
NEXT_PUBLIC_API_URL=
NEXT_PUBLIC_WS_URL=
```

Example:

```env
NEXT_PUBLIC_API_URL=https://gyro-backend.onrender.com
NEXT_PUBLIC_WS_URL=wss://gyro-backend.onrender.com/ws
```

---

# 🚀 Deployment

## Frontend Deployment

Deploy using:

* Vercel

## Backend Deployment

Deploy using:

* Render
* Railway

The deployed architecture supports public internet-based motion control.

---

# 📸 Screenshots

Add screenshots inside:

```text
/docs/screenshots
```

Recommended screenshots:

* Landing page
* QR pairing screen
* Telemetry dashboard
* Motion control interface
* Android application

---

# 🔮 Future Improvements

* AI-based motion smoothing
* Gesture recognition
* Multi-device support
* Voice commands
* 3D motion calibration
* Cloud session sync
* Remote internet-based pairing
* Haptic feedback integration

---

# 👨‍💻 Author

Ayush Narayan

BTech ISE — BMS College of Engineering

---

# 📜 License

This project is licensed under the MIT License.

---

# ⭐ Repository

If you found this project interesting, consider starring the repository.
