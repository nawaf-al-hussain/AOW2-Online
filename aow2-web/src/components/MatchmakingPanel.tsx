"use client";

import { useState, useEffect } from "react";
import { Swords, Search } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { useMatchmakingStore } from "@/lib/store";
import { toast } from "sonner";
import { apiUrl } from "@/lib/api";  // FIX (F-19): use apiUrl() so POST goes to Spring Boot (port 8080), not Next.js

export default function MatchmakingPanel() {
  const { isSearching, searchTime, matchFound, opponent, startSearch, stopSearch, foundMatch } = useMatchmakingStore();
  const [elapsed, setElapsed] = useState(0);
  const [searchKey, setSearchKey] = useState(0); // changes when search starts, resets elapsed
  const [serverAvailable, setServerAvailable] = useState(false);

  useEffect(() => {
    if (!isSearching) return;
    const start = Date.now();
    // Immediately show 0, then update every second
    const id = setInterval(() => {
      setElapsed(Math.floor((Date.now() - start) / 1000));
    }, 1000);
    return () => {
      clearInterval(id);
      setElapsed(0);
    };
  }, [isSearching, searchKey]);

  // Increment key when search starts to reset timer
  const handleStartSearch = async () => {
    setSearchKey((k) => k + 1);
    startSearch();
    // Try to join real matchmaking
    try {
      // FIX (F-19): Use apiUrl() with port 8080 so the POST goes to the Spring Boot
      // server, not the Next.js dev server. Previously this was a bare '/api/' path
      // which hit Next.js (no /api/matchmaking route) and always returned 404.
      const res = await fetch(apiUrl('/api/matchmaking/join', 8080), { method: 'POST' });
      if (res.ok) {
        setServerAvailable(true);
      } else {
        setServerAvailable(false);
      }
    } catch {
      setServerAvailable(false);
    }
  };

  // Demo fallback: simulate match found after 8 seconds when server is unavailable
  useEffect(() => {
    if (isSearching && !serverAvailable && elapsed >= 8 && !matchFound) {
      foundMatch("EnemyCommander");
    }
  }, [elapsed, isSearching, matchFound, foundMatch, serverAvailable]);

  const formatTime = (s: number) => `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, "0")}`;

  return (
    <Card className="bg-[#111827] border-zinc-800">
      {isSearching && !serverAvailable && <div className="text-xs text-amber-500 bg-amber-900/20 border border-amber-800/30 rounded mx-4 mt-4 px-3 py-1">Demo Mode — Matchmaking server unavailable</div>}
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-lg">
          <Swords className="h-5 w-5 text-red-500" />
          Quick Match
        </CardTitle>
      </CardHeader>
      <CardContent>
        {matchFound ? (
          <div className="text-center space-y-4 py-4">
            <div className="text-2xl font-bold text-green-400 animate-pulse">Match Found!</div>
            <p className="text-zinc-400">Opponent: <span className="text-white font-semibold">{opponent}</span></p>
            <Button
              className="bg-gradient-to-r from-green-600 to-green-700 hover:from-green-500 hover:to-green-600 text-white font-bold w-full"
              // FIX (H3 from CRITICAL_ANALYSIS_REPORT.md): Wire the Join Battle button.
              // Launching the FXGL client from the browser is not yet implemented; for now
              // show a toast that explains the next step instead of dead-ending silently.
              onClick={() =>
                toast.info("Game client launch coming soon", {
                  description: "Launch the AOW2 desktop client to start the match.",
                })
              }
            >
              <Swords className="mr-2 h-4 w-4" /> Join Battle
            </Button>
          </div>
        ) : isSearching ? (
          <div className="text-center space-y-4 py-4">
            <div className="relative w-24 h-24 mx-auto">
              <div className="absolute inset-0 rounded-full border-4 border-amber-500/30 animate-ping" />
              <div className="absolute inset-2 rounded-full border-4 border-amber-500/60 animate-spin" style={{ animationDuration: "3s" }} />
              <div className="absolute inset-4 rounded-full bg-amber-500/20 flex items-center justify-center">
                <Search className="h-6 w-6 text-amber-400" />
              </div>
            </div>
            <p className="text-zinc-400">Searching for opponent...</p>
            <p className="text-2xl font-mono text-amber-400">{formatTime(elapsed)}</p>
            <Progress value={(elapsed / 30) * 100} className="h-1" />
            <p className="text-xs text-zinc-600">ELO range: ±{Math.min(100 + elapsed * 5, 500)}</p>
            <Button variant="outline" className="border-red-800 text-red-400 hover:bg-red-900/20 w-full" onClick={stopSearch}>
              Cancel Search
            </Button>
          </div>
        ) : (
          <div className="text-center space-y-3 py-4">
            <p className="text-zinc-500">Find an opponent of similar skill level</p>
            <Button
              className="bg-gradient-to-r from-red-600 to-red-700 hover:from-red-500 hover:to-red-600 text-white font-bold w-full"
              onClick={handleStartSearch}
            >
              <Swords className="mr-2 h-4 w-4" /> Find Match
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
