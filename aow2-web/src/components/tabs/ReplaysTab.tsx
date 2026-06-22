"use client";

import { useState, useEffect } from "react";
import { Play, Clock, SwordsIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getReplays } from "@/lib/api";
import { toast } from "sonner";

export function ReplaysTab() {
  const [isDemo, setIsDemo] = useState(true);
  const [replays, setReplays] = useState<any[]>([]);

  const demoReplays = [
    { id: 1, player1: "IronCommander", player2: "SteelBlade", winner: "IronCommander", mapName: "Crossroads", duration: "23:45", playedAt: "2 hours ago" },
    { id: 2, player1: "WarEagle", player2: "TacticalNuke", winner: "TacticalNuke", mapName: "Valley of Death", duration: "31:12", playedAt: "4 hours ago" },
    { id: 3, player1: "GhostRecon", player2: "PhoenixRise", winner: "GhostRecon", mapName: "Island Fortress", duration: "18:33", playedAt: "6 hours ago" },
    { id: 4, player1: "ViperStrike", player2: "ThunderBolt", winner: "ThunderBolt", mapName: "Urban Warfare", duration: "27:08", playedAt: "8 hours ago" },
    { id: 5, player1: "NightHawk", player2: "StormRider", winner: "NightHawk", mapName: "Desert Storm", duration: "42:51", playedAt: "12 hours ago" },
    { id: 6, player1: "IronCommander", player2: "WarEagle", winner: "IronCommander", mapName: "Mountain Pass", duration: "35:17", playedAt: "1 day ago" },
  ];

  useEffect(() => {
    // FIX (M9 from CRITICAL_ANALYSIS_REPORT.md): Use the getReplays() helper
    // instead of a direct `fetch('/api/replays')` so the request goes through
    // apiUrl() with the ?XTransformPort=8080 query string.
    getReplays()
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setReplays(data);
          setIsDemo(false);
        }
      })
      .catch(() => {});
  }, []);

  const displayData = replays.length > 0 ? replays : demoReplays;

  return (
    <div className="space-y-4">
      {isDemo && <div className="text-xs text-amber-500 bg-amber-900/20 border border-amber-800/30 rounded px-3 py-1 mb-3">Demo Data — Server unavailable</div>}
      <h2 className="text-2xl font-bold flex items-center gap-2">
        <Play className="h-6 w-6 text-cyan-500" />
        Recent Replays
      </h2>

      <Card className="bg-[#111827] border-zinc-800">
        <CardContent className="p-0">
          <div className="divide-y divide-zinc-800/50">
            {displayData.map((r) => (
              <div key={r.id} className="flex items-center gap-4 px-4 py-3 hover:bg-zinc-800/30 transition-colors">
                <div className="flex items-center gap-2 flex-1 min-w-0">
                  <span className={`font-semibold ${r.winner === r.player1 ? "text-green-400" : "text-zinc-400"}`}>
                    {r.player1}
                  </span>
                  <SwordsIcon className="h-4 w-4 text-zinc-600 shrink-0" />
                  <span className={`font-semibold ${r.winner === r.player2 ? "text-green-400" : "text-zinc-400"}`}>
                    {r.player2}
                  </span>
                </div>
                <div className="flex items-center gap-4 text-sm text-zinc-500 shrink-0">
                  <Badge variant="outline" className="border-zinc-700 text-zinc-400">{r.mapName}</Badge>
                  <div className="flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {r.duration}
                  </div>
                  <span className="w-20 text-right">{r.playedAt}</span>
                  <Button
                    size="sm"
                    variant="outline"
                    className="border-zinc-700 text-cyan-400 hover:bg-cyan-900/20 h-7 text-xs"
                    // FIX (H3 from CRITICAL_ANALYSIS_REPORT.md): Wire the Watch button.
                    // In-browser replay viewer is not yet implemented; show a toast
                    // instead of silently dead-ending.
                    onClick={() =>
                      toast.info("Replay viewer coming soon", {
                        description: `Replay ${r.player1} vs ${r.player2} on ${r.mapName} (${r.duration})`,
                      })
                    }
                  >
                    <Play className="mr-1 h-3 w-3" /> Watch
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}