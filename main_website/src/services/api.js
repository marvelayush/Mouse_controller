/**
 * REST API client helper for GyroCursor platform.
 * Binds directly to NEXT_PUBLIC_API_URL.
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000';

/**
 * Fetch dynamic QR code data from backend.
 * @returns {Promise<{ qrUrl: string, sessionId: string, message?: string }>}
 */
export async function fetchQrCode() {
  try {
    const res = await fetch(`${API_BASE_URL}/qr`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!res.ok) {
      throw new Error(`API returned error status: ${res.status}`);
    }

    return await res.json();
  } catch (error) {
    console.warn('[API] Fetching /qr failed. Generating offline fallback session.', error);
    
    // Simulate dynamic fallback session offline
    const fallbackSession = Math.random().toString(36).substring(2, 10).toUpperCase();
    const phoneClientUrl = `https://gyrocursor.vercel.app/phone?session=${fallbackSession}`;
    
    // Return mock integration-ready data
    return {
      qrUrl: phoneClientUrl,
      sessionId: fallbackSession,
      isOfflineFallback: true,
      message: 'FastAPI server not connected. Operating in developer sandbox preview mode.',
    };
  }
}
