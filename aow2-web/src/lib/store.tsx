import { create } from "zustand";

// Auth store
interface AuthState {
  token: string | null;
  username: string | null;
  elo: number;
  isLoggedIn: boolean;
  login: (token: string, username: string, elo: number) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  username: null,
  elo: 1000,
  isLoggedIn: false,
  login: (token, username, elo) =>
    set({ token, username, elo, isLoggedIn: true }),
  logout: () =>
    set({ token: null, username: null, elo: 1000, isLoggedIn: false }),
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
