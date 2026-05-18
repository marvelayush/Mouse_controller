import websocket
import json

# Use the latest scanned data
IP = "192.168.1.73"
PORT = "65098" 
ROOM_ID = "30112fd1-ea9c-41a6-8d05-68eca8f9ef79"

def on_message(ws, message):
    print(f"\n[NEW DATA]: {message[:500]}...") # Printing first 500 chars
    # If the message looks like base64 or a byte array, that's your file!
    
def on_error(ws, error):
    print(f"Error: {error}")

def on_open(ws):
    print("Connected! Sending Join Request...")
    # This is the standard handshake for most P2P socket apps
    join_payload = {
        "type": "join",
        "roomId": ROOM_ID,
        "name": "Orbit_Explorer"
    }
    ws.send(json.dumps(join_payload))

ws = websocket.WebSocketApp(f"ws://{IP}:{PORT}",
                            on_message=on_message,
                            on_error=on_error)
ws.on_open = on_open
ws.run_forever()