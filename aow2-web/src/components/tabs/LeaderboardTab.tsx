"use client";

import { useState, useEffect } from "react";
import { Trophy, Crown } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { getLeaderboard } from "@/lib/api";

export function LeaderboardTab() {
  const [players, setPlayers] = useState<any[]>([]);
  const [isDemo, setIsDemo] = useState(true);
  const [loading, setLoading] = useState(true);

  // Demo data for when server is offline
  const demoPlayers = [
    { id: 1, username: "IronCommander", eloRating: 1820, wins: 156, losses: 34 },
    { id: 2, username: "SteelBlade", eloRating: 1750, wins: 142, losses: 48 },
    { id: 3, username: "WarEagle", eloRating: 1680, wins: 128, losses: 52 },
    { id: 4, username: "TacticalNuke", eloRating: 1590, wins: 110, losses: 60 },
    { id: 5, username: "GhostRecon", eloRating: 1540, wins: 98, losses: 55 },
    { id: 6, username: "PhoenixRise", eloRating: 1480, wins: 89, losses: 61 },
    { id: 7, username: "ViperStrike", eloRating: 1420, wins: 82, losses: 68 },
    { id: 8, username: "ThunderBolt", eloRating: 1360, wins: 75, losses: 70 },
    { id: 9, username: "NightHawk", eloRating: 1290, wins: 68, losses: 72 },
    { id: 10, username: "StormRider", eloRating: 1230, wins: 62, losses: 78 },
    { id: 11, username: "DesertFox", eloRating: 1180, wins: 55, losses: 80 },
    { id: 12, username: "SilverWolf", eloRating: 1120, wins: 48, losses: 82 },
    { id: 13, username: "CrimsonTide", eloRating: 1060, wins: 42, losses: 85 },
    { id: 14, username: "ShadowOps", eloRating: 1010, wins: 38, losses: 88 },
    { id: 15, username: "RookieCadet", eloRating: 920, wins: 22, losses: 95 },
  ];

  useEffect(() => {
    getLeaderboard()
      .then((data) => {
        setPlayers(data);
        setIsDemo(false);
      })
      .catch(() => setPlayers(demoPlayers))
      .finally(() => setLoading(false));
  }, []);

  const data = players.length > 0 ? players : demoPlayers;

  const getRankIcon = (rank: number) => {
    if (rank === 1) return <Crown className="h-5 w-5 text-amber-400" />;
    if (rank === 2) return <Crown className="h-5 w-5 text-zinc-300" />;
    if (rank === 3) return <Crown className="h-5 w-5 text-amber-700" />;
    return <span className="text-zinc-600 font-mono w-5 text-center">{rank}</span>;
  };

  const getEloBadge = (elo: number) => {
    if (elo >= 1500) return <Badge className="bg-amber-600/20 text-amber-400 border-amber-700/50">General</Badge>;
    if (elo >= 1200) return <Badge className="bg-purple-600/20 text-purple-400 border-purple-700/50">Colonel</Badge>;
    if (elo >= 1000) return <Badge className="bg-blue-600/20 text-blue-400 border-blue-700/50">Captain</Badge>;
    return <Badge className="bg-zinc-600/20 text-zinc-400 border-zinc-700/50">Lieutenant</Badge>;
  };

  return (
    <div className="space-y-4">
      {isDemo && <div className="text-xs text-amber-500 bg-amber-900/20 border border-amber-800/30 rounded px-3 py-1 mb-3">Demo Data — Server unavailable</div>}
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <Trophy className="h-6 w-6 text-amber-500" />
          Global Leaderboard
        </h2>
        <Badge variant="outline" className="border-zinc-700">{data.length} Players</Badge>
      </div>

      {/* Top 3 Podium */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        {data.slice(0, 3).map((p, i) => (
          <Card key={p.id} className={`text-center ${i === 0 ? "bg-amber-900/20 border-amber-700/50 -mt-4" : i === 1 ? "bg-zinc-800/50 border-zinc-700" : "bg-amber-900/10 border-amber-900/30"}`}>
            <CardContent className="p-4">
              <div className="mx-auto mb-2">
                {getRankIcon(i + 1)}
              </div>
              <p className="font-bold truncate">{p.username}</p>
              <p className={`text-2xl font-bold ${i === 0 ? "text-amber-400" : i === 1 ? "text-zinc-300" : "text-amber-700"}`}>
                {p.eloRating}
              </p>
              {getEloBadge(p.eloRating)}
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Full Leaderboard Table */}
      <Card className="bg-[#111827] border-zinc-800">
        <CardContent className="p-0">
          <ScrollArea className="h-[400px]">
            <div className="divide-y divide-zinc-800/50">
              {data.map((p, i) => (
                <div key={p.id} className="flex items-center gap-3 px-4 py-3 hover:bg-zinc-800/30 transition-colors">
                  <div className="w-8 flex justify-center">{getRankIcon(i + 1)}</div>
                  <Avatar className="h-8 w-8">
                    <AvatarFallback className="bg-zinc-800 text-zinc-400 text-xs">
                      {p.username.charAt(0)}
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold truncate">{p.username}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    {getEloBadge(p.eloRating)}
                    <span className="text-sm font-mono text-zinc-300 w-16 text-right">{p.eloRating}</span>
                  </div>
                </div>
              ))}
            </div>
          </ScrollArea>
        </CardContent>
      </Card>
    </div>
  );
}
