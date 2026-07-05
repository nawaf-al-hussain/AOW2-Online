const API_BASE = process.env.NEXT_PUBLIC_API_URL || "";

// Helper to build API URL with optional port transform.
// FIX (M9 from CRITICAL_ANALYSIS_REPORT.md): Exported so that components that
// previously called `fetch('/api/...')` directly (bypassing the helper and
// missing the ?XTransformPort=8080 query string used elsewhere) can be migrated.
export function apiUrl(path: string, port?: number): string {
  const base = API_BASE || "";
  if (port) {
    // FIX (ANALYSIS_V2 4.6): Use & instead of ? if path already has query params
    const separator = path.includes("?") ? "&" : "?";
    return `${base}${path}${separator}XTransformPort=${port}`;
  }
  return `${base}${path}`;
}

// Auth API
export async function register(username: string, password: string) {
  const res = await fetch(apiUrl("/api/auth/register", 8080), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error((await res.json()).error || "Registration failed");  // FIX (ANALYSIS_V2 4.5): server returns 'error' not 'message'
  return res.json();
}

export async function login(username: string, password: string) {
  const res = await fetch(apiUrl("/api/auth/login", 8080), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error((await res.json()).error || "Login failed");  // FIX (ANALYSIS_V2 4.5)
  return res.json();
}

// Leaderboard API
export async function getLeaderboard() {
  const res = await fetch(apiUrl("/api/leaderboard", 8080));
  if (!res.ok) throw new Error("Failed to fetch leaderboard");
  return res.json();
}

// Maps API
export async function getMaps() {
  const res = await fetch(apiUrl("/api/maps", 8080));
  if (!res.ok) throw new Error("Failed to fetch maps");
  return res.json();
}

export async function uploadMap(token: string, data: { name: string; description: string; mapData: string }) {
  const res = await fetch(apiUrl("/api/maps", 8080), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  });
  if (!res.ok) throw new Error("Failed to upload map");
  return res.json();
}

export async function downloadMap(mapId: number) {
  const res = await fetch(apiUrl(`/api/maps/${mapId}`, 8080));
  if (!res.ok) throw new Error("Failed to download map");
  return res.json();
}

// Replay API
export async function getReplays() {
  const res = await fetch(apiUrl("/api/replays", 8080));
  if (!res.ok) throw new Error("Failed to fetch replays");
  return res.json();
}

// FIX (ANALYSIS_V2 6.4): /api/units endpoint doesn't exist on the server.
// The UnitsTab was fetching from a non-existent endpoint and always failing.
// Return empty data until a proper units endpoint is added to the server.
export async function getUnits() {
  // No server endpoint exists for unit data yet — return empty array
  return [];
}

// Chat API
export async function getChatHistory() {
  const res = await fetch(apiUrl("/api/chat/history", 8080));
  if (!res.ok) throw new Error("Failed to fetch chat history");
  return res.json();
}

// Player API
export async function getPlayerInfo(token: string) {
  const res = await fetch(apiUrl("/api/auth/me", 8080), {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error("Failed to fetch player info");
  return res.json();
}

// Server stats API
// FIX (H8 from CRITICAL_ANALYSIS_REPORT.md): Replaces the hardcoded Quick Stats
// numbers on the dashboard landing page with real server counts.
export interface ServerStats {
  totalPlayers: number;
  matchesToday: number;
  matchesThisWeek: number;
  totalMatches: number;
  totalMaps: number;
  newPlayersToday: number;
  serverTime: number;
}

export async function getStats(): Promise<ServerStats> {
  const res = await fetch(apiUrl("/api/stats", 8080));
  if (!res.ok) throw new Error("Failed to fetch server stats");
  return res.json();
}

// Types
export interface Player {
  id: number;
  username: string;
  eloRating: number;
  createdAt: string;
}

export interface MapEntry {
  id: number;
  name: string;
  description: string;
  uploaderId: number;
  downloadCount: number;
  createdAt: string;
}

export interface ReplayEntry {
  id: number;
  player1: string;
  player2: string;
  winner: string;
  mapName: string;
  duration: number;
  playedAt: string;
}
