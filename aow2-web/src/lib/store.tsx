import { create } from "zustand";

// Auth store with localStorage persistence
interface AuthState {
  token: string | null;
  username: string | null;
  elo: number;
  isLoggedIn: boolean;
  login: (token: string, username: string, elo: number) => void;
  logout: () => void;
  hydrate: () => void;
}

const AUTH_KEY = "aow2_auth";

function loadAuth(): Partial<AuthState> {
  if (typeof window === "undefined") return {};
  try {
    const stored = localStorage.getItem(AUTH_KEY);
    if (stored) {
      return JSON.parse(stored);
    }
  } catch {
    // Ignore parse errors
  }
  return {};
}

function persistAuth(state: Partial<AuthState>) {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(
      AUTH_KEY,
      JSON.stringify({
        token: state.token,
        username: state.username,
        elo: state.elo,
        isLoggedIn: state.isLoggedIn,
      })
    );
  } catch {
    // Ignore storage errors (e.g. quota exceeded)
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  username: null,
  elo: 1000,
  isLoggedIn: false,
  login: (token, username, elo) => {
    const newState = { token, username, elo, isLoggedIn: true };
    set(newState);
    persistAuth(newState);
  },
  logout: () => {
    const newState = { token: null, username: null, elo: 1000, isLoggedIn: false };
    set(newState);
    persistAuth(newState);
    if (typeof window !== "undefined") {
      localStorage.removeItem(AUTH_KEY);
    }
  },
  hydrate: () => {
    const stored = loadAuth();
    if (stored.token) {
      set({
        token: stored.token ?? null,
        username: stored.username ?? null,
        elo: stored.elo ?? 1000,
        isLoggedIn: stored.isLoggedIn ?? false,
      });
    }
  },
}));

// Chat store
interface ChatMessage {
  id: string;
  player: string;
  message: string;
  timestamp: number;
}

interface ChatState {
  messages: ChatMessage[];
  addMessage: (msg: ChatMessage) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  addMessage: (msg) =>
    set((state) => ({ messages: [...state.messages.slice(-99), msg] })),
}));

// Matchmaking store
interface MatchmakingState {
  isSearching: boolean;
  searchTime: number;
  matchFound: boolean;
  opponent: string | null;
  startSearch: () => void;
  stopSearch: () => void;
  foundMatch: (opponent: string) => void;
}

export const useMatchmakingStore = create<MatchmakingState>((set) => ({
  isSearching: false,
  searchTime: 0,
  matchFound: false,
  opponent: null,
  startSearch: () => set({ isSearching: true, searchTime: 0, matchFound: false }),
  stopSearch: () => set({ isSearching: false, searchTime: 0 }),
  foundMatch: (opponent) => set({ isSearching: false, matchFound: true, opponent }),
}));

// Combined provider — just re-exports children
export function AOW2Provider({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
