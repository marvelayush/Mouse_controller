/**
 * High-performance WebSocket wrapper for the GyroCursor platform.
 * Handles automatic reconnects, exponential backoff, keepalive pings,
 * and support for both JSON text frames and 12-byte packed float32 Big-Endian array buffers.
 */

export class GyroWebSocket {
  constructor(url, onMessage, onStatusChange) {
    this.url = url;
    this.onMessage = onMessage;
    this.onStatusChange = onStatusChange;
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectTimeout = null;
    this.isClosedPurposely = false;
    this.pingInterval = null;
  }

  connect() {
    this.isClosedPurposely = false;
    
    try {
      console.log(`[WebSocket] Connecting to ${this.url}`);
      this.ws = new WebSocket(this.url);
      
      // Use ArrayBuffer for low latency data packing
      this.ws.binaryType = 'arraybuffer';
      
      this.ws.onopen = () => {
        console.log('[WebSocket] Connection established successfully');
        this.reconnectAttempts = 0;
        this.onStatusChange({ connected: true, state: 'CONNECTED' });
        this.startHeartbeat();
      };

      this.ws.onclose = (event) => {
        console.log('[WebSocket] Connection closed', event);
        this.stopHeartbeat();
        this.onStatusChange({ connected: false, state: 'DISCONNECTED' });
        if (!this.isClosedPurposely) {
          this.attemptReconnect();
        }
      };

      this.ws.onerror = (error) => {
        console.warn('[WebSocket] Connection failed or cert blocked. Graceful mock fallback initialized.', error);
        this.onStatusChange({ connected: false, state: 'ERROR', error });
      };

      this.ws.onmessage = async (event) => {
        // 1. Process Low-Latency Binary Struct Arrays
        if (event.data instanceof ArrayBuffer) {
          const view = new DataView(event.data);
          // Big-endian float32 struct unpacking matching server.py struct.unpack('!fff')
          if (event.data.byteLength >= 12) {
            const alpha = view.getFloat32(0, false);
            const beta = view.getFloat32(4, false);
            const gamma = view.getFloat32(8, false);
            this.onMessage({ type: 'orientation', alpha, beta, gamma });
          }
        } 
        // 2. Process Standard JSON Messages (Clicks, Pings, Configs)
        else if (typeof event.data === 'string') {
          try {
            const parsed = JSON.parse(event.data);
            this.onMessage(parsed);
          } catch (e) {
            this.onMessage({ type: 'text', data: event.data });
          }
        }
      };
    } catch (e) {
      console.warn('[WebSocket] Setup exception. Graceful mock fallback initialized.', e);
      this.onStatusChange({ connected: false, state: 'ERROR', error: e });
      this.attemptReconnect();
    }
  }

  attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log('[WebSocket] Exceeded max reconnect tries. Halting.');
      this.onStatusChange({ connected: false, state: 'FAILED' });
      return;
    }
    
    this.reconnectAttempts++;
    this.onStatusChange({ 
      connected: false, 
      state: 'RECONNECTING', 
      attempt: this.reconnectAttempts,
      max: this.maxReconnectAttempts
    });
    
    clearTimeout(this.reconnectTimeout);
    
    // Exponential backoff
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 10000);
    console.log(`[WebSocket] Retrying connection in ${delay}ms...`);
    
    this.reconnectTimeout = setTimeout(() => {
      this.connect();
    }, delay);
  }

  startHeartbeat() {
    this.stopHeartbeat();
    this.pingInterval = setInterval(() => {
      this.send({ type: 'ping', timestamp: Date.now() });
    }, 5000); // Send keepalive ping every 5 seconds
  }

  stopHeartbeat() {
    clearInterval(this.pingInterval);
  }

  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(typeof data === 'string' ? data : JSON.stringify(data));
      return true;
    }
    return false;
  }

  close() {
    this.isClosedPurposely = true;
    this.stopHeartbeat();
    clearTimeout(this.reconnectTimeout);
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}
