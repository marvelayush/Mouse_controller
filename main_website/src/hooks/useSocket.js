import { useState, useEffect, useRef } from 'react';
import { GyroWebSocket } from '@/services/websocket';

const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8000/ws';

export function useSocket() {
  const [socketConnected, setSocketConnected] = useState(false);
  const [socketState, setSocketState] = useState('DISCONNECTED');
  const [deviceConnected, setDeviceConnected] = useState(false);
  const [deviceName, setDeviceName] = useState('Not Paired');
  const [latency, setLatency] = useState(0);
  const [gyroValues, setGyroValues] = useState({ alpha: 0, beta: 0, gamma: 0 });
  const [signalStrength, setSignalStrength] = useState(0);
  const [batteryLevel, setBatteryLevel] = useState(100);
  const [isMockData, setIsMockData] = useState(false);

  const socketRef = useRef(null);
  const mockIntervalRef = useRef(null);
  const pingStartRef = useRef(0);

  // 1. Establish session connection
  const connectSession = (sessionId) => {
    disconnectSession(); // Clean up prior connection if any
    
    // Stop mock generator if active
    stopMockGenerator();
    setIsMockData(false);

    const wsUrl = `${WS_BASE_URL}?session=${sessionId}&client=desktop`;
    
    socketRef.current = new GyroWebSocket(
      wsUrl,
      // Message handler
      (msg) => {
        if (!msg) return;

        // Process live orientation packets
        if (msg.type === 'orientation') {
          setGyroValues({
            alpha: Number(msg.alpha || 0),
            beta: Number(msg.beta || 0),
            gamma: Number(msg.gamma || 0)
          });
          
          // Once we receive Orientation, verify device is active
          setDeviceConnected(true);
          setSignalStrength(Math.floor(Math.random() * 15) + 85); // 85-100% active
        } 
        
        // Process pairing/handshake notifications
        else if (msg.type === 'device_connected') {
          setDeviceConnected(true);
          setDeviceName(msg.device || 'Mobile Controller');
          setBatteryLevel(msg.battery || 88);
          setSignalStrength(100);
        } 
        
        else if (msg.type === 'device_disconnected') {
          setDeviceConnected(false);
          setDeviceName('Not Paired');
          setSignalStrength(0);
        }
        
        // Process latency pings
        else if (msg.type === 'pong') {
          const rtt = Date.now() - (msg.timestamp || pingStartRef.current);
          setLatency(Math.max(1, rtt));
        }
      },
      // Status transition handler
      (status) => {
        setSocketConnected(status.connected);
        setSocketState(status.state);
        
        if (status.state === 'FAILED' || status.state === 'ERROR') {
          console.warn('[WebSocket] Session state changed to error/failed:', status.state);
        }
      }
    );

    socketRef.current.connect();
  };

  // 2. Disconnect helper
  const disconnectSession = () => {
    if (socketRef.current) {
      socketRef.current.close();
      socketRef.current = null;
    }
    setSocketConnected(false);
    setSocketState('DISCONNECTED');
    setDeviceConnected(false);
    setDeviceName('Not Paired');
    setSignalStrength(0);
  };

  // 3. Trigger premium offline mock telemetry
  const triggerMockPreview = () => {
    setIsMockData(true);
    setDeviceConnected(true);
    setDeviceName('Mobile Sandbox Controller');
    setBatteryLevel(92);
    setLatency(4); // Simulated ultra-low LAN RTT
    setSignalStrength(95);

    stopMockGenerator();

    let angle = 0;
    mockIntervalRef.current = setInterval(() => {
      angle += 0.05;
      // Generate ultra-smooth dynamic curves simulating pitch, yaw, and roll movement
      const betaVal = Math.sin(angle) * 18 + Math.cos(angle * 0.5) * 4;
      const gammaVal = Math.cos(angle * 1.2) * 22 + Math.sin(angle * 0.8) * 6;
      const alphaVal = (angle * 20) % 360;

      setGyroValues({
        alpha: parseFloat(alphaVal.toFixed(1)),
        beta: parseFloat(betaVal.toFixed(1)),
        gamma: parseFloat(gammaVal.toFixed(1))
      });

      // Slowly drain simulated battery indicator
      setBatteryLevel(prev => {
        if (prev <= 10) return 98; // Wrap around
        return Math.random() < 0.005 ? prev - 1 : prev;
      });

      // Random jitter on latency & signal strength
      setLatency(prev => {
        const jitter = Math.floor(Math.random() * 5) - 2;
        return Math.max(2, Math.min(15, prev + jitter));
      });
      setSignalStrength(prev => {
        const jitter = Math.floor(Math.random() * 3) - 1;
        return Math.max(90, Math.min(100, prev + jitter));
      });
    }, 50); // Refresh at 20Hz for seamless chart graphics
  };

  const stopMockGenerator = () => {
    if (mockIntervalRef.current) {
      clearInterval(mockIntervalRef.current);
      mockIntervalRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      disconnectSession();
      stopMockGenerator();
    };
  }, []);

  return {
    socketConnected,
    socketState,
    deviceConnected,
    deviceName,
    latency,
    gyroValues,
    signalStrength,
    batteryLevel,
    isMockData,
    connectSession,
    disconnectSession,
    triggerMockPreview
  };
}
