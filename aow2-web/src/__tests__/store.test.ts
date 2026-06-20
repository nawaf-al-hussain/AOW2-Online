import { describe, it, expect, beforeEach, vi } from "vitest";

// Zustand stores use React hooks internally, but the logic is testable
// We test the store behavior by importing and calling store methods directly
import { useAuthStore, useChatStore, useMatchmakingStore } from "@/lib/store";

describe("AuthStore", () => {
  beforeEach(() => {
    useAuthStore.setState({
      token: null,
      username: null,
      elo: 1000,
      isLoggedIn: false,
    });
    localStorage.clear();
  });

  it("starts unauthenticated", () => {
    const state = useAuthStore.getState();
    expect(state.isLoggedIn).toBe(false);
    expect(state.token).toBeNull();
    expect(state.username).toBeNull();
    expect(state.elo).toBe(1000);
  });

  it("login sets token and username", () => {
    useAuthStore.getState().login("jwt-token-123", "testplayer", 1500);
    const state = useAuthStore.getState();
    expect(state.isLoggedIn).toBe(true);
    expect(state.token).toBe("jwt-token-123");
    expect(state.username).toBe("testplayer");
    expect(state.elo).toBe(1500);
  });

  it("login persists to localStorage", () => {
    useAuthStore.getState().login("jwt-abc", "player1", 1200);
    const stored = localStorage.getItem("aow2_auth");
    expect(stored).not.toBeNull();
    const parsed = JSON.parse(stored!);
    expect(parsed.token).toBe("jwt-abc");
    expect(parsed.username).toBe("player1");
  });

  it("logout clears all auth state", () => {
    useAuthStore.getState().login("tok", "user", 1400);
    useAuthStore.getState().logout();
    const state = useAuthStore.getState();
    expect(state.isLoggedIn).toBe(false);
    expect(state.token).toBeNull();
    expect(state.username).toBeNull();
    expect(state.elo).toBe(1000);
  });

  it("logout removes from localStorage", () => {
    useAuthStore.getState().login("tok", "user", 1400);
    useAuthStore.getState().logout();
    expect(localStorage.getItem("aow2_auth")).toBeNull();
  });

  it("hydrate restores state from localStorage", () => {
    localStorage.setItem("aow2_auth", JSON.stringify({
      token: "restored-tok",
      username: "restored-user",
      elo: 1750,
      isLoggedIn: true,
    }));
    useAuthStore.getState().hydrate();
    const state = useAuthStore.getState();
    expect(state.token).toBe("restored-tok");
    expect(state.username).toBe("restored-user");
    expect(state.elo).toBe(1750);
    expect(state.isLoggedIn).toBe(true);
  });

  it("hydrate ignores missing localStorage data", () => {
    localStorage.clear();
    useAuthStore.getState().hydrate();
    const state = useAuthStore.getState();
    expect(state.token).toBeNull();
    expect(state.isLoggedIn).toBe(false);
  });
});

describe("ChatStore", () => {
  beforeEach(() => {
    useChatStore.setState({ messages: [] });
  });

  it("starts with empty messages", () => {
    expect(useChatStore.getState().messages).toEqual([]);
  });

  it("addMessage appends a message", () => {
    useChatStore.getState().addMessage({
      id: "1",
      player: "alice",
      message: "hello",
      timestamp: 1000,
    });
    expect(useChatStore.getState().messages).toHaveLength(1);
    expect(useChatStore.getState().messages[0].player).toBe("alice");
  });

  it("keeps only last 100 messages", () => {
    for (let i = 0; i < 110; i++) {
      useChatStore.getState().addMessage({
        id: String(i),
        player: `player${i}`,
        message: `msg${i}`,
        timestamp: i,
      });
    }
    expect(useChatStore.getState().messages).toHaveLength(100);
    expect(useChatStore.getState().messages[0].id).toBe("10");
  });
});

describe("MatchmakingStore", () => {
  beforeEach(() => {
    useMatchmakingStore.setState({
      isSearching: false,
      searchTime: 0,
      matchFound: false,
      opponent: null,
    });
  });

  it("starts idle", () => {
    const state = useMatchmakingStore.getState();
    expect(state.isSearching).toBe(false);
    expect(state.matchFound).toBe(false);
    expect(state.opponent).toBeNull();
  });

  it("startSearch begins searching", () => {
    useMatchmakingStore.getState().startSearch();
    const state = useMatchmakingStore.getState();
    expect(state.isSearching).toBe(true);
    expect(state.searchTime).toBe(0);
    expect(state.matchFound).toBe(false);
  });

  it("stopSearch resets search state", () => {
    useMatchmakingStore.getState().startSearch();
    useMatchmakingStore.getState().stopSearch();
    const state = useMatchmakingStore.getState();
    expect(state.isSearching).toBe(false);
    expect(state.searchTime).toBe(0);
  });

  it("foundMatch sets opponent and stops searching", () => {
    useMatchmakingStore.getState().startSearch();
    useMatchmakingStore.getState().foundMatch("bob");
    const state = useMatchmakingStore.getState();
    expect(state.isSearching).toBe(false);
    expect(state.matchFound).toBe(true);
    expect(state.opponent).toBe("bob");
  });
});
