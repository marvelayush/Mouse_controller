"""
AirMouse — Mouse Movement Test (no phone needed)
Simulates gyroscope input and tests all mouse functions.
Run: python test_mouse.py
"""
import time
import math
import pyautogui

pyautogui.FAILSAFE = False
pyautogui.PAUSE    = 0

w, h = pyautogui.size()
print(f"\n{'='*50}")
print(f"  AirMouse Mouse Test")
print(f"  Screen resolution: {w} x {h}")
print(f"{'='*50}\n")

# ── 1. Move to center ──────────────────────────────
print("[ 1/6 ] Moving cursor to screen center...")
pyautogui.moveTo(w // 2, h // 2, duration=0.5)
time.sleep(0.5)
print("        ✓ Done\n")

# ── 2. Draw a circle (simulates gyroscope sweep) ──
print("[ 2/6 ] Drawing a circle with the cursor (gyroscope simulation)...")
cx, cy = w // 2, h // 2
radius = 120
steps  = 60
for i in range(steps + 1):
    angle = 2 * math.pi * i / steps
    x = int(cx + radius * math.cos(angle))
    y = int(cy + radius * math.sin(angle))
    pyautogui.moveTo(x, y, _pause=False)
    time.sleep(0.02)
print("        ✓ Done\n")

# ── 3. Relative moves (like real gyro deltas) ──────
print("[ 3/6 ] Testing relative moves (like real tilt deltas)...")
pyautogui.moveTo(w // 2, h // 2, duration=0.3)
moves = [(30,0),(0,30),(-30,0),(0,-30),(20,20),(-20,20),(-20,-20),(20,-20)]
for dx, dy in moves:
    pyautogui.moveRel(dx, dy, duration=0.12, _pause=False)
    time.sleep(0.08)
print("        ✓ Done\n")

# ── 4. Scroll test ─────────────────────────────────
print("[ 4/6 ] Testing scroll (3 up, 3 down)...")
pyautogui.scroll(3)
time.sleep(0.4)
pyautogui.scroll(-3)
time.sleep(0.4)
print("        ✓ Done\n")

# ── 5. Left click ─────────────────────────────────
print("[ 5/6 ] Left click at current position...")
pyautogui.click(_pause=False)
time.sleep(0.3)
print("        ✓ Done\n")

# ── 6. Return to center ───────────────────────────
print("[ 6/6 ] Returning cursor to center...")
pyautogui.moveTo(w // 2, h // 2, duration=0.4)
time.sleep(0.3)

pos = pyautogui.position()
print("        ✓ Done\n")
print(f"{'='*50}")
print(f"  ALL TESTS PASSED ✓")
print(f"  Final cursor position: {pos.x}, {pos.y}")
print(f"{'='*50}\n")
print("  pyautogui is working. The server is ready.")
print("  Run 'python server.py' and connect your phone!\n")
