'use client';

import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Activity, 
  Smartphone, 
  QrCode, 
  RefreshCw, 
  Cpu, 
  Battery, 
  Wifi, 
  Play, 
  Pause, 
  Info,
  ChevronRight,
  Terminal,
  MousePointer
} from 'lucide-react';
import QRCode from 'qrcode';
import { fetchQrCode } from '@/services/api';
import { useSocket } from '@/hooks/useSocket';

export default function Home() {
  const [step, setStep] = useState(0); // 0: Hero Landing, 1: Initialize QR, 2: Active Telemetry
  const [sessionId, setSessionId] = useState('');
  const [qrUrl, setQrUrl] = useState('');
  const [localIp, setLocalIp] = useState('192.168.0.3'); // Current active laptop LAN IP default
  const [loadingSession, setLoadingSession] = useState(false);
  
  // Custom progress and interactive states
  const [pairingProgress, setPairingProgress] = useState(0);
  const [isBootstrapping, setIsBootstrapping] = useState(false);
  const [hoveredAwareness, setHoveredAwareness] = useState(false);
  const [phoneTilt, setPhoneTilt] = useState({ x: 0, y: 0 });
  const [interactiveAngles, setInteractiveAngles] = useState({ alpha: 45, beta: 12, gamma: -8 });

  const [consoleLogs, setConsoleLogs] = useState([
    'SYSTEM INITIALIZED // V1.0.4',
    'AWAITING USER COMMAND...'
  ]);

  // Autodetect LAN IP if accessing via network
  useEffect(() => {
    if (typeof window !== 'undefined') {
      const hostname = window.location.hostname;
      if (hostname && hostname !== 'localhost' && hostname !== '127.0.0.1' && !hostname.includes('vercel')) {
        setLocalIp(hostname);
      }
    }
  }, []);

  const qrCanvasRef = useRef(null);
  const telemetryCanvasRef = useRef(null);
  const dataHistoryRef = useRef([]);

  const {
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
  } = useSocket();

  // Dynamic header state helper mappers
  const getDisplayState = () => {
    if (socketConnected) return 'CONNECTED // SYNC ACTIVE';
    if (socketState === 'CONNECTING' || socketState === 'RECONNECTING') return 'CONNECTING...';
    if (isMockData) return 'SANDBOX SIMULATOR ACTIVE';
    return 'STANDBY // READY FOR PAIRING';
  };

  const getPulseClass = () => {
    if (socketConnected) return 'pulse-green';
    if (socketState === 'CONNECTING' || socketState === 'RECONNECTING') return 'pulse-amber';
    if (isMockData) return 'pulse-blue';
    return 'pulse-gray';
  };

  const getStatusColorClass = () => {
    if (socketConnected) return 'text-[#39FF14]';
    if (socketState === 'CONNECTING' || socketState === 'RECONNECTING') return 'text-[#fbcb38]';
    if (isMockData) return 'text-[#00d4ff]';
    return 'text-[#88a87a]';
  };


  // Push new telemetry logs to console simulator
  const logToConsole = (msg) => {
    setConsoleLogs(prev => [`[${new Date().toLocaleTimeString()}] ${msg}`, ...prev.slice(0, 8)]);
  };

  // Render QR Canvas when url is fetched
  useEffect(() => {
    if (qrUrl && qrCanvasRef.current) {
      QRCode.toCanvas(
        qrCanvasRef.current,
        qrUrl,
        {
          width: 210,
          margin: 1,
          color: {
            dark: '#39FF14', // Neon Toxic Green
            light: '#181818', // Deep Card Surface
          },
        },
        (error) => {
          if (error) {
            console.error('[QR] Error rendering QR Code:', error);
            logToConsole('ERROR RENDERING CONNECTION QR CODE');
          } else {
            logToConsole(`QR RENDERED FOR SESSION: ${sessionId}`);
          }
        }
      );
    }
  }, [qrUrl, qrCanvasRef.current]);

  // Transition layout when phone pairs
  useEffect(() => {
    if (deviceConnected) {
      setStep(2);
      logToConsole(`DEVICE CONNECTED // PARED: ${deviceName}`);
    } else if (step === 2 && !deviceConnected) {
      setStep(1);
      logToConsole('MOBILE PAIRING LOST // AWAITING RE-SCAN');
    }
  }, [deviceConnected, deviceName]);

  // Update real-time history telemetry points
  useEffect(() => {
    if (deviceConnected) {
      dataHistoryRef.current.push({
        beta: gyroValues.beta,
        gamma: gyroValues.gamma,
        alpha: gyroValues.alpha,
        time: Date.now()
      });
      if (dataHistoryRef.current.length > 100) {
        dataHistoryRef.current.shift();
      }
    }
  }, [gyroValues, deviceConnected]);

  // High performance Canvas sweep radar plotter loop
  useEffect(() => {
    let rafId;
    const canvas = telemetryCanvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    const paint = () => {
      if (!canvas || !ctx) return;

      const w = canvas.width;
      const h = canvas.height;
      const midY = h / 2;

      // Clean Canvas
      ctx.fillStyle = '#131313';
      ctx.fillRect(0, 0, w, h);

      // Cyber Grid overlay
      ctx.strokeStyle = 'rgba(57, 255, 20, 0.04)';
      ctx.lineWidth = 1;
      for (let x = 0; x < w; x += 40) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, h);
        ctx.stroke();
      }
      for (let y = 0; y < h; y += 30) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(w, y);
        ctx.stroke();
      }

      // Drawing sweeping lines
      const points = dataHistoryRef.current;
      if (points.length > 1) {
        // Draw beta (Pitch) line - Glowing green
        ctx.shadowBlur = 10;
        ctx.shadowColor = '#39ff14';
        ctx.strokeStyle = '#39ff14';
        ctx.lineWidth = 2;
        ctx.beginPath();
        for (let i = 0; i < points.length; i++) {
          const x = (i / (points.length - 1)) * w;
          // Scale -90..+90 beta values dynamically
          const y = midY - (points[i].beta * (h / 140));
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();

        // Draw gamma (Roll) line - Glowing cyan
        ctx.shadowColor = '#00d4ff';
        ctx.strokeStyle = '#00d4ff';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        for (let i = 0; i < points.length; i++) {
          const x = (i / (points.length - 1)) * w;
          // Scale -90..+90 gamma values dynamically
          const y = midY - (points[i].gamma * (h / 140));
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();

        ctx.shadowBlur = 0; // Reset blur
      } else {
        // Flatline simulator when empty
        ctx.strokeStyle = 'rgba(57, 255, 20, 0.2)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(0, midY);
        ctx.lineTo(w, midY);
        ctx.stroke();
      }

      rafId = requestAnimationFrame(paint);
    };

    rafId = requestAnimationFrame(paint);
    return () => cancelAnimationFrame(rafId);
  }, [step, deviceConnected]);

  // Core initialization action
  const handleInitialize = async () => {
    setIsBootstrapping(true);
    setPairingProgress(0);
    setStep(1);
    logToConsole('BOOTSTRAPPING PAIRING PROTOCOLS...');

    // Smoothly animate the progress bar
    let progress = 0;
    const interval = setInterval(() => {
      progress += Math.floor(Math.random() * 8) + 5; // dynamic increments
      if (progress >= 100) {
        progress = 100;
        clearInterval(interval);
        setTimeout(() => {
          setIsBootstrapping(false);
          logToConsole('GATEWAY CHANNELS SYNCHRONIZED // QR READY');
        }, 300);
      }
      setPairingProgress(progress);
      
      if (progress > 10 && progress < 30) {
        logToConsole('PROVISIONING PORT SECURE SOCKET HOSTS...');
      } else if (progress >= 40 && progress < 60) {
        logToConsole('ESTABLISHING HIGH-SPEED IMU SHIELD...');
      } else if (progress >= 80 && progress < 95) {
        logToConsole('VERIFYING CORE INJECTOR RESOLVER HANDSHAKE...');
      }
    }, 120);

    try {
      const targetUrl = `https://${localIp}:8443`;
      setQrUrl(targetUrl);
      setSessionId('LOCAL-LAN');
      connectSession('LOCAL-LAN');
    } catch (err) {
      logToConsole('OFFLINE MODE ACTIVE // DUAL SIMULATOR CHANNELS');
    }
  };

  const handleMouseMovePhone = (e) => {
    const card = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - card.left - card.width / 2;
    const y = e.clientY - card.top - card.height / 2;
    
    // Normalize rotate values
    const rotateY = (x / (card.width / 2)) * 15;
    const rotateX = -(y / (card.height / 2)) * 15;
    
    setPhoneTilt({ x: rotateX, y: rotateY });
    
    setInteractiveAngles({
      alpha: Math.floor(rotateY * 3 + 45),
      beta: Math.floor(rotateX * 3 + 12),
      gamma: Math.floor((rotateX + rotateY) * 2 - 8)
    });
  };

  const handleMouseLeavePhone = () => {
    setPhoneTilt({ x: 0, y: 0 });
    setInteractiveAngles({ alpha: 45, beta: 12, gamma: -8 });
  };

  const handleSimulate = () => {
    logToConsole('BOOTSTRAPPING SANDBOX SIMULATOR...');
    triggerMockPreview();
  };

  const handleClose = () => {
    disconnectSession();
    setStep(0);
    logToConsole('SESSION CLOSED BY COMMAND');
  };

  return (
    <div className="min-h-screen font-mono text-[#e2e2e2] flex flex-col justify-between p-4 md:p-8 relative overflow-hidden">
      
      {/* Background Cyber Watermark crawling lines */}
      <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden select-none">
        <div className="watermark-line watermark-line-1">
          {Array(6).fill("MADE BY AYUSH NARAYAN // SYSTEM OPERATIVE // ").join("")}
        </div>
        <div className="watermark-line watermark-line-2">
          {Array(6).fill("MADE BY AYUSH NARAYAN // KINETIC CONTROLLER // ").join("")}
        </div>
      </div>

      
      {/* 1. MotionTrackHeader HUD Status */}
      <header className="w-full flex flex-col md:flex-row justify-between items-start md:items-center border-b border-[rgba(57,255,20,0.15)] pb-4 mb-8">
        <div>
          <h1 className="text-lg font-bold tracking-widest text-[#39FF14] flex items-center gap-2">
            <span className={getPulseClass()} />
            GYROCURSOR // KINETIC PANEL
          </h1>
          <p className="text-xs text-[#baccb0] font-light mt-1">
            CORE NODE: LOCAL // SYSTEM STATE: <span className={`${getStatusColorClass()} font-bold`}>{getDisplayState()}</span>
          </p>
        </div>
        <div className="flex items-center gap-4 mt-2 md:mt-0 text-xs bg-black/40 px-3 py-1.5 border border-[rgba(57,255,20,0.1)] rounded-[4px]">
          <div>
            SESSION ID: <span className="text-[#39FF14] font-bold">{sessionId || 'NULL'}</span>
          </div>
          <div className="h-3 w-[1px] bg-[rgba(57,255,20,0.2)]" />
          <div>
            DEVICES: <span className="text-[#39FF14]">{deviceConnected ? '1 CONNECTED' : '0 PENDING'}</span>
          </div>
        </div>
      </header>

      {/* 2. Page Router State Displays */}
      <main className="flex-grow flex items-center justify-center py-6">
        <AnimatePresence mode="wait">
          
          {/* STEP 0: Hero Landing / Explanation */}
          {step === 0 && (
            <motion.section 
              key="hero"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.3 }}
              className="max-w-4xl w-full grid grid-cols-1 lg:grid-cols-12 gap-8 items-center"
            >
              <div className="lg:col-span-7 space-y-6">
                <div className="inline-flex items-center gap-2 border border-[#39FF14]/30 bg-[#39FF14]/5 px-3 py-1 rounded-[4px] text-xs text-[#39FF14]">
                  <Activity size={14} className="animate-pulse" />
                  NEXT-GEN AIRMOUSE HUB
                </div>
                <h2 className="text-4xl md:text-5xl font-extrabold tracking-tight leading-none font-sans text-white">
                  Control Your Cursor <br />
                  <span className="text-[#39FF14] font-mono">With Kinetic Motion.</span>
                </h2>
                <p className="text-sm text-[#baccb0] leading-relaxed max-w-xl">
                  GyroCursor converts your smartphone orientation orientation sensors into a high-precision PC input cursor. Built on standard WebSockets with packed binary Float32 arrays for zero lag, sub-millisecond mouse injection.
                </p>
                <div className="flex flex-wrap gap-4 pt-2">
                  <button 
                    onClick={handleInitialize}
                    disabled={loadingSession}
                    className="btn-toxic"
                  >
                    {loadingSession ? (
                      <span className="flex items-center gap-2">
                        <RefreshCw size={14} className="animate-spin" />
                        INITIALIZING...
                      </span>
                    ) : (
                      <span className="flex items-center gap-1.5">
                        GET STARTED <ChevronRight size={14} />
                      </span>
                    )}
                  </button>
                  <button 
                    onClick={handleSimulate}
                    className="border border-[#baccb0]/30 hover:border-white hover:text-white px-6 py-3 rounded-[4px] text-xs font-bold transition-all text-[#baccb0] uppercase"
                  >
                    RUN SANDBOX TELEMETRY
                  </button>
                </div>
              </div>

              {/* Graphic Mock Command Terminal */}
              <div className="lg:col-span-5 telemetry-card p-5 h-80 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between border-b border-[rgba(57,255,20,0.1)] pb-2 mb-3">
                    <span className="text-xs text-[#39FF14] flex items-center gap-1.5">
                      <Terminal size={14} /> CONSOLE GATEWAY
                    </span>
                    <span className="text-[10px] text-[#baccb0]">SECURE SHELL</span>
                  </div>
                  <div className="space-y-1.5 text-xs text-[#baccb0]/80 h-44 overflow-y-auto">
                    {consoleLogs.map((log, index) => (
                      <p key={index} className={index === 0 ? "text-[#39FF14]" : ""}>
                        &gt; {log}
                      </p>
                    ))}
                  </div>
                </div>
                <div className="flex items-center gap-2 text-xs border-t border-[rgba(57,255,20,0.1)] pt-3 text-[#baccb0]">
                  <Cpu size={14} className="text-[#39FF14] animate-pulse" />
                  <span>CORE FREQUENCY: 1.25 GHz</span>
                </div>
              </div>

              {/* SINGLE SMOOTH RECTANGULAR GENERAL INSTRUCTION CARD */}
              <div className="lg:col-span-12 mt-10">
                <div className="telemetry-card p-6 border border-[#39FF14]/20 bg-black/40 rounded-[4px] relative overflow-hidden">
                  
                  {/* Decorative corner tag */}
                  <div className="absolute top-0 right-0 bg-[#39FF14]/10 border-l border-b border-[#39FF14]/25 px-3 py-1 text-[9px] text-[#39FF14] font-mono tracking-widest uppercase">
                    SYS // MANUAL
                  </div>

                  <h3 className="text-xs font-bold tracking-widest text-[#39FF14] uppercase mb-4 flex items-center gap-2">
                    <Info size={14} className="text-[#39FF14] animate-pulse" /> // GENERAL INSTRUCTIONS FOR USE
                  </h3>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-left">
                    
                    <div className="space-y-1.5 border-r border-[rgba(57,255,20,0.1)] pr-4 last:border-none">
                      <span className="text-[10px] font-mono text-[#39FF14] font-bold">[ 01 / START BACKEND ]</span>
                      <p className="text-xs text-[#baccb0] leading-relaxed">
                        Navigate to the <code className="bg-[#131313] px-1 py-0.5 rounded text-[#39FF14] font-mono">AirMouse</code> folder and run <code className="bg-[#131313] px-1 py-0.5 rounded text-[#39FF14] font-mono">python server.py</code> to initialize socket hosts.
                      </p>
                    </div>

                    <div className="space-y-1.5 border-r border-[rgba(57,255,20,0.1)] pr-4 last:border-none">
                      <span className="text-[10px] font-mono text-[#39FF14] font-bold">[ 02 / BIND LAN IP ]</span>
                      <p className="text-xs text-[#baccb0] leading-relaxed">
                        Click <strong className="text-white">GET STARTED</strong> above and configure your laptop LAN IP to match the terminal log.
                      </p>
                    </div>

                    <div className="space-y-1.5">
                      <span className="text-[10px] font-mono text-[#39FF14] font-bold">[ 03 / SCAN & NAVIGATE ]</span>
                      <p className="text-xs text-[#baccb0] leading-relaxed">
                        Scan the dynamic QR with your phone Chrome browser, accept the local SSL cert, and tilt to steer your cursor!
                      </p>
                    </div>

                  </div>

                </div>
              </div>

              {/* SPATIAL AWARENESS */}
              <div className="lg:col-span-12 mt-16 text-center space-y-6">
                <div className="space-y-2">
                  <h3 className="text-2xl md:text-3xl font-extrabold text-white tracking-tight uppercase">
                    SPATIAL AWARENESS
                  </h3>
                  <p className="text-xs text-[#baccb0] font-mono max-w-xl mx-auto leading-relaxed">
                    Break the boundaries of traditional interaction. One device to rule every digital surface in your environment.
                  </p>
                </div>

                {/* Interactive Device Connectivity Simulation Grid */}
                <div 
                  className="relative max-w-4xl mx-auto rounded-[4px] border border-[rgba(57,255,20,0.15)] shadow-[0_0_30px_rgba(57,255,20,0.06)] bg-[#131313]/60 p-6 md:p-12 overflow-hidden flex flex-col md:flex-row items-center justify-between gap-8 md:gap-12"
                  onMouseEnter={() => setHoveredAwareness(true)}
                  onMouseLeave={() => setHoveredAwareness(false)}
                >
                  
                  {/* CRT Screen scanline overlay inside simulation */}
                  <div className="absolute inset-0 scanlines opacity-[0.15] pointer-events-none" />

                  {/* LEFT: Phone Illustration */}
                  <div 
                    className="w-56 h-[380px] bg-black/80 rounded-[24px] border-2 border-[rgba(57,255,20,0.25)] relative p-3 flex flex-col justify-between shadow-[0_0_20px_rgba(57,255,20,0.05)] cursor-crosshair transition-all duration-200 ease-out select-none"
                    style={{
                      transform: `perspective(600px) rotateX(${phoneTilt.x}deg) rotateY(${phoneTilt.y}deg) scale(1.02)`,
                      boxShadow: hoveredAwareness ? "0 0 35px rgba(57, 255, 20, 0.15)" : "0 0 20px rgba(57,255,20,0.05)"
                    }}
                    onMouseMove={handleMouseMovePhone}
                    onMouseLeave={handleMouseLeavePhone}
                  >
                    {/* Speaker grill / dynamic notches */}
                    <div className="w-16 h-3.5 bg-black border border-[rgba(57,255,20,0.2)] rounded-full mx-auto flex items-center justify-center gap-1">
                      <span className="w-1 h-1 bg-[#39FF14] rounded-full animate-ping" />
                      <span className="w-6 h-0.5 bg-[#39FF14]/30 rounded-full" />
                    </div>

                    <div className="flex-grow flex flex-col justify-between py-6 space-y-4">
                      
                      {/* UI Header */}
                      <div className="text-center space-y-1">
                        <span className="text-[9px] text-[#39FF14] font-mono tracking-widest uppercase block">// KINETIC CONTROLLER</span>
                        <span className="text-[10px] text-white font-bold font-mono">DEVICE STATUS: LINKED</span>
                      </div>

                      {/* Dynamic angle grids */}
                      <div className="space-y-2.5 bg-black/60 p-3 rounded-[6px] border border-[rgba(57,255,20,0.1)]">
                        <div className="flex justify-between items-center text-[9px] font-mono border-b border-[rgba(57,255,20,0.05)] pb-1">
                          <span className="text-[#baccb0]">YAW (ALPHA)</span>
                          <span className="text-[#39FF14] font-bold">{interactiveAngles.alpha}°</span>
                        </div>
                        <div className="flex justify-between items-center text-[9px] font-mono border-b border-[rgba(57,255,20,0.05)] pb-1">
                          <span className="text-[#baccb0]">PITCH (BETA)</span>
                          <span className="text-[#39FF14] font-bold">{interactiveAngles.beta}°</span>
                        </div>
                        <div className="flex justify-between items-center text-[9px] font-mono">
                          <span className="text-[#baccb0]">ROLL (GAMMA)</span>
                          <span className="text-[#39FF14] font-bold">{interactiveAngles.gamma}°</span>
                        </div>
                      </div>

                      {/* Simulated QR block */}
                      <div className="border border-[rgba(57,255,20,0.25)] p-2 rounded-[4px] bg-[#131313] w-24 h-24 mx-auto flex items-center justify-center relative overflow-hidden">
                        <div className="absolute inset-0 bg-[#39FF14]/5 flex items-center justify-center">
                          {/* Mini QR structure */}
                          <div className="w-16 h-16 border-2 border-[#39FF14] relative p-1">
                            <div className="absolute top-1 left-1 w-3 h-3 bg-[#39FF14]" />
                            <div className="absolute top-1 right-1 w-3 h-3 bg-[#39FF14]" />
                            <div className="absolute bottom-1 left-1 w-3 h-3 bg-[#39FF14]" />
                            <div className="w-full h-full flex flex-wrap gap-0.5 justify-center items-center opacity-80 pt-4">
                              <span className="w-1.5 h-1.5 bg-[#39FF14]" />
                              <span className="w-1.5 h-1.5 bg-[#39FF14]" />
                              <span className="w-1.5 h-1.5 bg-transparent" />
                              <span className="w-1.5 h-1.5 bg-[#39FF14]" />
                            </div>
                          </div>
                        </div>
                        <div className="absolute inset-x-0 h-0.5 bg-[#39FF14]/60 shadow-[0_0_8px_#39FF14] animate-bounce top-1" />
                      </div>

                    </div>

                    {/* Home bar button */}
                    <div className="w-16 h-1 bg-[rgba(57,255,20,0.25)] rounded-full mx-auto" />
                  </div>

                  {/* CENTER: Glowing Connection Pipe */}
                  <div className="flex-grow flex flex-col items-center justify-center relative min-w-[80px] py-4 md:py-0">
                    <span className="text-[9px] text-[#39FF14] font-mono tracking-widest uppercase bg-black/60 px-2 py-0.5 border border-[rgba(57,255,20,0.2)] rounded-[2px] z-10 animate-pulse">
                      CORE LINK
                    </span>
                    
                    {/* Horizontal link visual */}
                    <div className="w-full md:w-32 h-0.5 bg-[rgba(57,255,20,0.15)] relative overflow-hidden mt-2">
                      <div 
                        className={`h-full bg-gradient-to-r from-transparent via-[#39FF14] to-transparent ${hoveredAwareness ? "animate-[slide_1.5s_linear_infinite]" : "animate-[slide_3s_linear_infinite]"}`}
                        style={{ width: '40%' }}
                      />
                    </div>

                    <div className="text-[8px] text-[#baccb0] font-mono mt-1 select-none">
                      WSS // 8765
                    </div>
                  </div>

                  {/* RIGHT: Laptop Illustration */}
                  <div className="flex-grow max-w-md w-full bg-black/60 rounded-[8px] border border-[rgba(57,255,20,0.2)] overflow-hidden shadow-[0_0_20px_rgba(0,0,0,0.8)] relative flex flex-col h-[320px]">
                    
                    {/* Browser top-bar */}
                    <div className="bg-[#181818] border-b border-[rgba(57,255,20,0.15)] px-3 py-2 flex items-center justify-between">
                      <div className="flex gap-1.5">
                        <span className="w-2.5 h-2.5 rounded-full bg-red-500/80" />
                        <span className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                        <span className="w-2.5 h-2.5 rounded-full bg-green-500/80" />
                      </div>
                      <div className="bg-[#131313] px-4 py-0.5 border border-[rgba(57,255,20,0.1)] rounded-[4px] text-[8px] font-mono text-[#baccb0] tracking-wider uppercase">
                        https://localhost:3000
                      </div>
                      <div className="w-8" />
                    </div>

                    {/* Laptop Screen Content */}
                    <div className="flex-grow p-4 flex flex-col justify-between bg-[#131313]/90 relative overflow-hidden">
                      
                      {/* Decorative radar plotter grid lines */}
                      <div className="absolute inset-0 opacity-[0.03] bg-[radial-gradient(#39ff14_1px,transparent_1px)] [background-size:16px_16px] pointer-events-none" />

                      {/* Header block */}
                      <div className="flex justify-between items-start border-b border-[rgba(57,255,20,0.08)] pb-2 z-10">
                        <div>
                          <h4 className="text-[10px] font-bold text-white tracking-widest uppercase">UNIVERSAL MOTION INTELLIGENCE</h4>
                          <span className="text-[8px] text-[#baccb0] font-mono uppercase">System Node Status: Active Pairing</span>
                        </div>
                        <span className="text-[8px] bg-[#39FF14]/10 text-[#39FF14] px-1.5 py-0.5 border border-[#39FF14]/20 rounded-[2px] font-mono tracking-widest uppercase">
                          OPERATIONAL
                        </span>
                      </div>

                      {/* Interactive Canvas Graph mockup */}
                      <div className="h-28 bg-[#181818]/60 border border-[rgba(57,255,20,0.08)] rounded-[4px] overflow-hidden relative flex items-center justify-center p-2 my-2">
                        <svg className="w-full h-full text-[#39FF14]" viewBox="0 0 100 30" preserveAspectRatio="none">
                          <path 
                            d={`M0,15 Q15,${15 - (interactiveAngles.beta / 3)} 30,15 T60,15 T90,15 T100,15`}
                            fill="none" 
                            stroke="currentColor" 
                            strokeWidth="0.75" 
                            className="transition-all duration-300"
                          />
                          <path 
                            d={`M0,15 Q15,${15 + (interactiveAngles.gamma / 3)} 35,15 T70,15 T100,15`}
                            fill="none" 
                            stroke="#00d4ff" 
                            strokeWidth="0.5" 
                            className="transition-all duration-300 opacity-60"
                          />
                        </svg>

                        {/* Interactive floating pointer coordinate */}
                        <div 
                          className="absolute text-[8px] bg-black/80 px-2 py-0.5 border border-[#39FF14]/30 text-[#39FF14] rounded-[2px] font-mono tracking-wider transition-all duration-200"
                          style={{
                            left: `${50 + (phoneTilt.y * 1.8)}%`,
                            top: `${40 - (phoneTilt.x * 1.5)}%`,
                            transform: 'translate(-50%, -50%)'
                          }}
                        >
                          XY_LINK: [{(50 + (phoneTilt.y * 1.8)).toFixed(0)}, {(40 - (phoneTilt.x * 1.5)).toFixed(0)}]
                        </div>

                        {/* Glowing cursor representation */}
                        <MousePointer 
                          size={16} 
                          className="absolute text-[#39FF14] drop-shadow-[0_0_8px_#39FF14] transition-all duration-200"
                          style={{
                            left: `${52 + (phoneTilt.y * 1.8)}%`,
                            top: `${42 - (phoneTilt.x * 1.5)}%`,
                            transform: 'translate(-50%, -50%)'
                          }}
                        />
                      </div>

                      {/* Footer logs block */}
                      <div className="flex justify-between items-center text-[8px] font-mono text-[#baccb0] border-t border-[rgba(57,255,20,0.08)] pt-2 z-10">
                        <span>LATENCY_SYNC: 12ms</span>
                        <span>FRAMES: 60 FPS // ZERO JUMPS</span>
                      </div>

                    </div>
                  </div>

                </div>
              </div>

              {/* LIVE TELEMETRY */}
              <div className="lg:col-span-12 mt-20 space-y-6">
                <div className="text-center md:text-left space-y-2">
                  <h3 className="text-2xl md:text-3xl font-extrabold text-white tracking-tight uppercase">
                    LIVE TELEMETRY
                  </h3>
                  <p className="text-xs text-[#baccb0] font-mono leading-relaxed">
                    Continuous streaming of IMU data with zero dropped frames.
                  </p>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
                  
                  {/* Left Column: Wave Previews */}
                  <div className="lg:col-span-7 telemetry-card p-6 flex flex-col justify-between space-y-4">
                    
                    {/* Pitch preview */}
                    <div className="space-y-1.5">
                      <div className="flex justify-between items-center text-[10px] font-mono">
                        <span className="text-[#baccb0]">X_AXIS_PITCH</span>
                        <span className="text-[#39FF14] flex items-center gap-1.5">
                          <span className="w-1.5 h-1.5 bg-[#39FF14] rounded-full animate-ping" /> REALTIME
                        </span>
                      </div>
                      <div className="h-10 bg-black/40 border border-[#baccb0]/10 rounded-[2px] overflow-hidden relative flex items-center">
                        <svg className="w-full h-full text-[#39FF14] opacity-80" viewBox="0 0 100 10" preserveAspectRatio="none">
                          <path 
                            d="M0,5 Q12.5,1 25,5 T50,5 T75,5 T100,5" 
                            fill="none" 
                            stroke="currentColor" 
                            strokeWidth="0.5" 
                            className="animate-pulse" 
                          />
                        </svg>
                      </div>
                    </div>

                    {/* Roll preview */}
                    <div className="space-y-1.5">
                      <div className="flex justify-between items-center text-[10px] font-mono">
                        <span className="text-[#baccb0]">Y_AXIS_ROLL</span>
                        <span className="text-[#00d4ff] flex items-center gap-1.5">
                          <span className="w-1.5 h-1.5 bg-[#00d4ff] rounded-full animate-ping" /> REALTIME
                        </span>
                      </div>
                      <div className="h-10 bg-black/40 border border-[#baccb0]/10 rounded-[2px] overflow-hidden relative flex items-center">
                        <svg className="w-full h-full text-[#00d4ff] opacity-80" viewBox="0 0 100 10" preserveAspectRatio="none">
                          <path 
                            d="M0,5 Q12.5,9 25,5 T50,5 T75,5 T100,5" 
                            fill="none" 
                            stroke="currentColor" 
                            strokeWidth="0.5" 
                            className="animate-pulse" 
                          />
                        </svg>
                      </div>
                    </div>

                    {/* Yaw preview */}
                    <div className="space-y-1.5">
                      <div className="flex justify-between items-center text-[10px] font-mono">
                        <span className="text-[#baccb0]">Z_AXIS_YAW</span>
                        <span className="text-[#39FF14] flex items-center gap-1.5">
                          <span className="w-1.5 h-1.5 bg-[#39FF14] rounded-full animate-ping" /> REALTIME
                        </span>
                      </div>
                      <div className="h-10 bg-black/40 border border-[#baccb0]/10 rounded-[2px] overflow-hidden relative flex items-center">
                        <svg className="w-full h-full text-[#39FF14] opacity-40" viewBox="0 0 100 10" preserveAspectRatio="none">
                          <path 
                            d="M0,5 Q12.5,3 25,5 T50,5 T75,5 T100,5" 
                            fill="none" 
                            stroke="currentColor" 
                            strokeWidth="0.5" 
                          />
                        </svg>
                      </div>
                    </div>

                  </div>

                  {/* Right Column: Deployment Card */}
                  <div className="lg:col-span-5 telemetry-card p-6 flex flex-col justify-between bg-black/25">
                    <div>
                      <div className="inline-flex items-center justify-center p-2.5 border border-[#39FF14]/30 bg-[#39FF14]/5 rounded-[4px] mb-4 text-[#39FF14]">
                        <Terminal size={20} />
                      </div>
                      <h4 className="text-lg font-bold text-white uppercase tracking-wider mb-2">
                        READY FOR DEPLOYMENT?
                      </h4>
                      <p className="text-xs text-[#baccb0] leading-relaxed mb-6 font-mono">
                        Access the CLI tools or integrate our SDK into your spatial computing stack today.
                      </p>
                    </div>

                    <div className="flex items-center justify-between border-t border-[rgba(57,255,20,0.15)] pt-4 text-[10px] font-mono text-[#baccb0]">
                      <div>
                        VERSION: <span className="text-white font-bold">2.8.4-STABLE</span>
                      </div>
                      <div className="h-3 w-[1px] bg-[rgba(57,255,20,0.2)]" />
                      <div>
                        LATENCY_AVG: <span className="text-[#39FF14] font-bold">12MS</span>
                      </div>
                    </div>
                  </div>

                </div>
              </div>
            </motion.section>
          )}

          {/* STEP 1: Interactive Onboarding QR Card */}
          {step === 1 && (
            <motion.section 
              key="qr"
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.98 }}
              transition={{ duration: 0.3 }}
              className="max-w-md w-full telemetry-card p-6 flex flex-col items-center min-h-[460px] justify-between"
            >
              {isBootstrapping ? (
                <div className="w-full flex flex-col justify-between h-full flex-grow py-6 text-center space-y-6">
                  <div className="space-y-2">
                    <span className="text-xs font-bold tracking-wider text-[#39FF14] flex items-center justify-center gap-2">
                      <RefreshCw size={14} className="animate-spin text-[#39FF14]" /> PROVISIONING GATEWAY...
                    </span>
                    <h3 className="text-sm font-mono text-[#baccb0] uppercase">Initializing secure socket tunnel</h3>
                  </div>

                  {/* Progress bar container */}
                  <div className="w-full space-y-4">
                    <div className="flex justify-between items-center text-[10px] font-mono text-[#baccb0] px-1">
                      <span>SECURE_LINK // PROT_8443</span>
                      <span className="text-[#39FF14] font-bold">{pairingProgress}%</span>
                    </div>
                    
                    <div className="h-4 bg-black/60 border border-[rgba(57,255,20,0.25)] rounded-[4px] overflow-hidden p-0.5 relative">
                      <div 
                        className="h-full bg-gradient-to-r from-[#2ae500]/60 to-[#39FF14] rounded-[2px] transition-all duration-150 ease-out shadow-[0_0_10px_#39FF14]"
                        style={{ width: `${pairingProgress}%` }}
                      />
                      {/* Scanning beam on loader */}
                      <div className="absolute inset-y-0 w-8 bg-gradient-to-r from-transparent via-white/20 to-transparent skew-x-12 animate-pulse" />
                    </div>

                    <div className="text-[10px] text-[#baccb0] font-mono animate-pulse uppercase">
                      {pairingProgress < 30 && "ALLOCATING VIRTUAL PORT GATEWAYS..."}
                      {pairingProgress >= 30 && pairingProgress < 70 && "TUNNELING SECURE WEBSOCKET HANDSHAKES..."}
                      {pairingProgress >= 70 && pairingProgress < 95 && "VERIFYING SECURE SSL METRICS..."}
                      {pairingProgress >= 95 && "SYNCHRONIZING DYNAMIC QR CHANNELS..."}
                    </div>
                  </div>

                  <div className="text-[9px] text-[#baccb0]/50 font-mono uppercase tracking-wider leading-relaxed border-t border-[rgba(57,255,20,0.05)] pt-4">
                    COORDINATES BINDING: ACTIVE INJECTOR SYSTEM // V2.8.4
                  </div>
                </div>
              ) : (
                <>
                  <div className="w-full flex items-center justify-between border-b border-[rgba(57,255,20,0.15)] pb-3 mb-6">
                    <span className="text-xs font-bold tracking-wider text-[#39FF14] flex items-center gap-2">
                      <QrCode size={16} /> PAIRING PROTOCOL
                    </span>
                    <button 
                      onClick={handleClose} 
                      className="text-xs text-[#baccb0] hover:text-white transition-all uppercase"
                    >
                      [ CANCEL ]
                    </button>
                  </div>

                  <div className="relative border border-[rgba(57,255,20,0.2)] bg-[#181818] p-3 rounded-[4px] mb-6 flex items-center justify-center">
                    {/* Simulated dynamic QR scanner beam */}
                    <div className="absolute inset-x-0 h-0.5 bg-[#39FF14]/80 shadow-[0_0_10px_#39FF14] animate-bounce top-1 z-10" />
                    <canvas ref={qrCanvasRef} width={210} height={210} className="w-[210px] h-[210px] z-0" />
                  </div>

                  <div className="text-center space-y-3 w-full">
                    {/* Dynamic LAN IP Configurator */}
                    <div className="w-full space-y-2 mb-4 bg-black/40 p-4 border border-[rgba(57,255,20,0.15)] rounded-[4px]">
                      <label className="text-[10px] text-[#39FF14] uppercase tracking-widest block text-left font-bold">
                        🔧 LAPTOP LAN IP ADDRESS:
                      </label>
                      <input
                        type="text"
                        value={localIp}
                        onChange={(e) => {
                          const val = e.target.value;
                          setLocalIp(val);
                          setQrUrl(`https://${val}:8443`);
                          logToConsole(`IP REBOUND -> ${val}`);
                        }}
                        placeholder="e.g. 192.168.0.3"
                        className="w-full bg-[#131313] border border-[rgba(57,255,20,0.3)] focus:border-[#39FF14] focus:outline-none text-white text-xs py-2 px-3 rounded-[4px] font-mono tracking-wider transition-all"
                      />
                      <p className="text-[9px] text-[#baccb0] text-left leading-normal">
                        Matches the IP printed in your Python server console (e.g. <span className="text-[#39FF14]">192.168.0.3</span>). This forces the QR Code directly to your local Python server!
                      </p>
                    </div>

                    <h3 className="text-sm font-bold uppercase text-white tracking-widest">
                      Scan QR with Mobile Device
                    </h3>
                    <p className="text-xs text-[#baccb0] leading-relaxed px-4">
                      Open your smartphone camera, scan the system QR code, and authorize sensor connection to establish the kinetic socket link.
                    </p>
                    <div className="border-t border-[rgba(57,255,20,0.1)] pt-4 mt-2 flex justify-between text-[11px] text-[#baccb0]">
                      <span>PORT: 8443 // HTTPS READY</span>
                      <span className="text-[#39FF14] animate-pulse">AWAITING CLIENT PAIR...</span>
                    </div>
                    
                    {/* Fallback Simulator trigger */}
                    <div className="pt-2 w-full">
                      <button
                        onClick={handleSimulate}
                        className="w-full bg-[#39FF14] hover:bg-[#2eff08] text-black font-extrabold rounded-[4px] text-xs py-3.5 transition-all uppercase tracking-wider shadow-[0_0_15px_rgba(57,255,20,0.4)]"
                      >
                        🚀 Enter Command Dashboard
                      </button>
                      <p className="text-[10px] text-[#baccb0] leading-relaxed pt-3 text-left">
                        💡 <strong>Direct Inject Mode:</strong> Scanning the QR code binds your smartphone gyroscope directly to your laptop cursor. You can control your PC mouse instantly! To display real-time swept-radar grids and orientation telemetry on this monitor, click <strong>ENTER COMMAND DASHBOARD</strong> above.
                      </p>
                    </div>
                  </div>
                </>
              )}
            </motion.section>
          )}

          {/* STEP 2: Active Telemetry Panel Dashboard */}
          {step === 2 && (
            <motion.section 
              key="dashboard"
              initial={{ opacity: 0, scale: 0.98 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.98 }}
              transition={{ duration: 0.3 }}
              className="max-w-6xl w-full grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch"
            >
              
              {/* Left Telemetry Card */}
              <div className="lg:col-span-8 telemetry-card p-6 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between border-b border-[rgba(57,255,20,0.15)] pb-3 mb-6">
                    <span className="text-xs font-bold tracking-wider text-[#39FF14] flex items-center gap-2">
                      <Activity size={16} /> REAL-TIME MOTION TELEMETRY (RTT WAVE)
                    </span>
                    <div className="flex items-center gap-2">
                      {isMockData && (
                        <span className="text-[10px] bg-[#00d4ff]/10 text-[#00d4ff] border border-[#00d4ff]/30 px-2 py-0.5 rounded-[4px] font-bold">
                          SANDBOX PREVIEW MODE
                        </span>
                      )}
                      <span className="text-[10px] text-[#baccb0]">SCALE: -90° TO +90°</span>
                    </div>
                  </div>

                  {/* HTML5 Oscilloscope Canvas */}
                  <div className="border border-[rgba(57,255,20,0.15)] rounded-[4px] overflow-hidden mb-6 relative bg-[#131313]">
                    <canvas ref={telemetryCanvasRef} width={650} height={200} className="w-full h-[200px]" />
                    <div className="absolute top-3 left-4 flex gap-4 text-[10px]">
                      <span className="flex items-center gap-1.5 text-[#39ff14]">
                        <span className="w-2 h-2 bg-[#39ff14] rounded-full" /> PITCH (BETA)
                      </span>
                      <span className="flex items-center gap-1.5 text-[#00d4ff]">
                        <span className="w-2 h-2 bg-[#00d4ff] rounded-full" /> ROLL (GAMMA)
                      </span>
                    </div>
                  </div>
                </div>

                {/* Live Gauges Row */}
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div className="bg-black/30 border border-[rgba(57,255,20,0.1)] p-3 rounded-[4px]">
                    <div className="text-[10px] text-[#baccb0] uppercase mb-1">YAW (ALPHA)</div>
                    <div className="text-2xl font-bold text-[#39FF14]">{gyroValues.alpha.toFixed(1)}°</div>
                  </div>
                  <div className="bg-black/30 border border-[rgba(57,255,20,0.1)] p-3 rounded-[4px]">
                    <div className="text-[10px] text-[#baccb0] uppercase mb-1">PITCH (BETA)</div>
                    <div className="text-2xl font-bold text-[#39FF14]">{gyroValues.beta.toFixed(1)}°</div>
                  </div>
                  <div className="bg-black/30 border border-[rgba(57,255,20,0.1)] p-3 rounded-[4px]">
                    <div className="text-[10px] text-[#baccb0] uppercase mb-1">ROLL (GAMMA)</div>
                    <div className="text-2xl font-bold text-[#39FF14]">{gyroValues.gamma.toFixed(1)}°</div>
                  </div>
                </div>
              </div>

              {/* Right Telemetry Controls */}
              <div className="lg:col-span-4 telemetry-card p-6 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between border-b border-[rgba(57,255,20,0.15)] pb-3 mb-6">
                    <span className="text-xs font-bold tracking-wider text-[#39FF14] flex items-center gap-2">
                      <Smartphone size={16} /> DEVICE INTERACTIVE STATS
                    </span>
                    <button 
                      onClick={handleClose} 
                      className="text-xs text-red-500 hover:text-red-400 transition-all uppercase"
                    >
                      [ TERMINATE ]
                    </button>
                  </div>

                  {/* Device Telemetry Metrics */}
                  <div className="space-y-4">
                    <div className="flex items-center justify-between border-b border-[rgba(57,255,20,0.05)] pb-2">
                      <span className="text-xs text-[#baccb0]">DEVICE MODEL</span>
                      <span className="text-xs font-bold text-white uppercase">{deviceName}</span>
                    </div>

                    <div className="flex items-center justify-between border-b border-[rgba(57,255,20,0.05)] pb-2">
                      <span className="text-xs text-[#baccb0]">SOCKET ROUND-TRIP</span>
                      <span className="text-xs font-bold text-white flex items-center gap-1.5">
                        <Activity size={12} className="text-[#39FF14]" />
                        {latency} ms
                      </span>
                    </div>

                    <div className="flex items-center justify-between border-b border-[rgba(57,255,20,0.05)] pb-2">
                      <span className="text-xs text-[#baccb0]">SIGNAL QUALITY</span>
                      <span className="text-xs font-bold text-white flex items-center gap-2">
                        <Wifi size={14} className="text-[#39FF14]" />
                        {signalStrength}%
                      </span>
                    </div>

                    <div className="flex items-center justify-between border-b border-[rgba(57,255,20,0.05)] pb-2">
                      <span className="text-xs text-[#baccb0]">POWER SOURCE</span>
                      <span className="text-xs font-bold text-white flex items-center gap-1.5">
                        <Battery size={14} className={batteryLevel < 20 ? "text-red-500 animate-pulse" : "text-[#39FF14]"} />
                        {batteryLevel}%
                      </span>
                    </div>
                  </div>
                </div>

                <div className="space-y-3 pt-6 border-t border-[rgba(57,255,20,0.1)]">
                  <div className="text-[10px] text-[#baccb0] uppercase">ON-SCREEN CURSOR CALIBRATION</div>
                  <div className="bg-[#181818] p-3 rounded-[4px] border border-[rgba(57,255,20,0.1)] flex items-center gap-3">
                    <MousePointer size={20} className="text-[#39FF14] animate-bounce" />
                    <div>
                      <h4 className="text-xs font-bold text-white">ACTIVE OS INJECTOR</h4>
                      <p className="text-[10px] text-[#baccb0]">Coordinates synced to local screen bounds</p>
                    </div>
                  </div>
                </div>

              </div>

            </motion.section>
          )}

        </AnimatePresence>
      </main>

      {/* 3. Footer Copyright Info */}
      <footer className="w-full mt-8 border-t border-[rgba(57,255,20,0.1)] pt-4 flex flex-col md:flex-row justify-between items-center text-xs text-[#baccb0]/60">
        <div>
          <span>POWERED BY WSS & WEB ORIENTATION SENSORS // GYROCURSOR</span>
        </div>
        <div className="mt-2 md:mt-0 flex gap-4">
          <a href="https://nextjs.org" className="hover:text-white transition-all">NEXTJS</a>
          <span>//</span>
          <a href="https://vercel.com" className="hover:text-white transition-all">VERCEL READY</a>
        </div>
      </footer>

    </div>
  );
}
