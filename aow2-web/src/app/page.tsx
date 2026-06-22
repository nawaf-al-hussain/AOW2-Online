"use client";

import { useState, useEffect } from "react";
import {
  Shield, Swords, Map, Trophy, MessageSquare, Target,
  Play, Zap, Flame
} from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/lib/store";
import AnimatedBackground from "@/components/AnimatedBackground";
import LoginDialog from "@/components/LoginDialog";
import UserPanel from "@/components/UserPanel";
import MatchmakingPanel from "@/components/MatchmakingPanel";
import { LeaderboardTab } from "@/components/tabs/LeaderboardTab";
import { MapsTab } from "@/components/tabs/MapsTab";
import { ChatTab } from "@/components/tabs/ChatTab";
import { ReplaysTab } from "@/components/tabs/ReplaysTab";
import { UnitsTab } from "@/components/tabs/UnitsTab";
// FIX (H8 from CRITICAL_ANALYSIS_REPORT.md): Fetch live server stats instead of
// the previously hardcoded 1,247 / 89 / 342 / 56 numbers on the landing page.
import { getStats, type ServerStats } from "@/lib/api";

// ─── Faction Colors ─────────────────────────────────────────────
const CONFED_COLOR = "from-blue-600 to-blue-800";
const CONFED_BG = "bg-blue-900/30";
const CONFED_BORDER = "border-blue-700/50";
const REBEL_COLOR = "from-red-600 to-red-800";
const REBEL_BG = "bg-red-900/30";
const REBEL_BORDER = "border-red-700/50";

// ─── Main Page ──────────────────────────────────────────────────
export default function Home() {
  const { isLoggedIn } = useAuthStore();
  const [activeTab, setActiveTab] = useState("home");
  // FIX (H8 from CRITICAL_ANALYSIS_REPORT.md): Live server stats for the Quick Stats panel.
  // `null` while loading or when the server is unreachable; the panel renders "—" in that case.
  const [stats, setStats] = useState<ServerStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    getStats()
      .then((data) => {
        if (!cancelled) {
          setStats(data);
          setStatsLoading(false);
        }
      })
      .catch(() => {
        // Server unavailable — leave stats null; the panel will show "—" placeholders.
        if (!cancelled) setStatsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="min-h-screen flex flex-col relative">
      <AnimatedBackground />

      {/* Header */}
      <header className="relative z-10 border-b border-zinc-800/50 bg-[#0a0e17]/80 backdrop-blur-xl">
        <div className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="relative">
              <Shield className="h-8 w-8 text-amber-500" />
              <Swords className="h-4 w-4 text-red-500 absolute -bottom-1 -right-1" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-wider">ART OF WAR 2</h1>
              <p className="text-[10px] text-zinc-500 tracking-widest">ONLINE — v0.2.0-ALPHA</p>
            </div>
          </div>

          <nav className="hidden md:flex items-center gap-1">
            {[
              { id: "home", label: "Home", icon: Shield },
              { id: "leaderboard", label: "Leaderboard", icon: Trophy },
              { id: "maps", label: "Maps", icon: Map },
              { id: "units", label: "Units", icon: Target },
              { id: "replays", label: "Replays", icon: Play },
              { id: "chat", label: "Chat", icon: MessageSquare },
            ].map((tab) => (
              <Button
                key={tab.id}
                variant="ghost"
                size="sm"
                onClick={() => setActiveTab(tab.id)}
                className={`text-zinc-400 hover:text-white ${activeTab === tab.id ? "bg-zinc-800 text-white" : ""}`}
              >
                <tab.icon className="mr-1 h-4 w-4" />
                {tab.label}
              </Button>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            {isLoggedIn ? (
              <UserPanel />
            ) : (
              <LoginDialog />
            )}
          </div>
        </div>

        {/* Mobile nav */}
        <div className="md:hidden flex items-center gap-1 px-4 pb-2 overflow-x-auto">
          {[
            { id: "home", label: "Home", icon: Shield },
            { id: "leaderboard", label: "Ranks", icon: Trophy },
            { id: "maps", label: "Maps", icon: Map },
            { id: "units", label: "Units", icon: Target },
            { id: "replays", label: "Replays", icon: Play },
            { id: "chat", label: "Chat", icon: MessageSquare },
          ].map((tab) => (
            <Button
              key={tab.id}
              variant="ghost"
              size="sm"
              onClick={() => setActiveTab(tab.id)}
              className={`text-zinc-400 shrink-0 ${activeTab === tab.id ? "bg-zinc-800 text-white" : ""}`}
            >
              <tab.icon className="h-4 w-4" />
            </Button>
          ))}
        </div>
      </header>

      {/* Main Content */}
      <main className="relative z-10 flex-1 max-w-7xl mx-auto w-full px-4 py-6">
        {activeTab === "home" && (
          <div className="space-y-6">
            {/* Hero Section */}
            <div className="text-center py-8">
              <h2 className="text-5xl font-black tracking-tight mb-4">
                <span className="bg-gradient-to-r from-amber-400 via-amber-500 to-red-500 bg-clip-text text-transparent">
                  THE LEGEND RETURNS
                </span>
              </h2>
              <p className="text-zinc-400 text-lg max-w-2xl mx-auto mb-8">
                The legendary mobile RTS is back — rebuilt from the ground up with modern Java, online multiplayer,
                map builder, full modding support, and the complete campaign.
              </p>
              <div className="flex items-center justify-center gap-4">
                {isLoggedIn ? (
                  <Button className="bg-gradient-to-r from-red-600 to-red-700 hover:from-red-500 hover:to-red-600 text-white font-bold px-8 py-6 text-lg">
                    <Swords className="mr-2 h-5 w-5" />
                    Quick Match
                  </Button>
                ) : (
                  <LoginDialog />
                )}
              </div>
            </div>

            {/* Feature Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Card className="bg-gradient-to-br from-blue-900/20 to-blue-900/5 border-blue-800/30">
                <CardHeader>
                  <Shield className="h-8 w-8 text-blue-500 mb-2" />
                  <CardTitle>Full Campaign</CardTitle>
                  <CardDescription>29 missions across 3 episodes with Lua-scripted events</CardDescription>
                </CardHeader>
              </Card>
              <Card className="bg-gradient-to-br from-red-900/20 to-red-900/5 border-red-800/30">
                <CardHeader>
                  <Swords className="h-8 w-8 text-red-500 mb-2" />
                  <CardTitle>Online Multiplayer</CardTitle>
                  <CardDescription>Lockstep P2P with ELO matchmaking and desync detection</CardDescription>
                </CardHeader>
              </Card>
              <Card className="bg-gradient-to-br from-green-900/20 to-green-900/5 border-green-800/30">
                <CardHeader>
                  <Map className="h-8 w-8 text-green-500 mb-2" />
                  <CardTitle>Map Builder</CardTitle>
                  <CardDescription>Create, share, and play custom maps with validation</CardDescription>
                </CardHeader>
              </Card>
            </div>

            {/* Quick Access Panels */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <MatchmakingPanel />
              <Card className="bg-[#111827] border-zinc-800">
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-lg">
                    <Zap className="h-5 w-5 text-amber-500" />
                    Quick Stats
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-2 gap-4">
                    {/* FIX (H8 from CRITICAL_ANALYSIS_REPORT.md): Live server stats. */}
                    <div className="text-center">
                      <p className="text-3xl font-bold text-amber-400">
                        {statsLoading ? "…" : stats ? stats.totalPlayers.toLocaleString() : "—"}
                      </p>
                      <p className="text-xs text-zinc-500">Total Players</p>
                    </div>
                    <div className="text-center">
                      <p className="text-3xl font-bold text-green-400">
                        {statsLoading ? "…" : stats ? stats.matchesToday.toLocaleString() : "—"}
                      </p>
                      <p className="text-xs text-zinc-500">Matches Today</p>
                    </div>
                    <div className="text-center">
                      <p className="text-3xl font-bold text-blue-400">
                        {statsLoading ? "…" : stats ? stats.totalMaps.toLocaleString() : "—"}
                      </p>
                      <p className="text-xs text-zinc-500">Community Maps</p>
                    </div>
                    <div className="text-center">
                      <p className="text-3xl font-bold text-purple-400">
                        {statsLoading ? "…" : stats ? stats.totalMatches.toLocaleString() : "—"}
                      </p>
                      <p className="text-xs text-zinc-500">Total Matches Played</p>
                    </div>
                  </div>
                  {stats === null && !statsLoading && (
                    <p className="text-xs text-amber-500 mt-3 text-center">
                      Server unavailable — showing placeholders.
                    </p>
                  )}
                </CardContent>
              </Card>
            </div>

            {/* Faction Comparison */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Card className={`bg-gradient-to-br ${CONFED_BG} ${CONFED_BORDER} border`}>
                <CardHeader>
                  <CardTitle className="text-blue-400 flex items-center gap-2">
                    <Shield className="h-5 w-5" />
                    Global Confederation
                  </CardTitle>
                  <CardDescription>Heavy armor, superior firepower, structured military</CardDescription>
                </CardHeader>
                <CardContent className="space-y-2 text-sm">
                  <div className="flex justify-between"><span className="text-zinc-400">Strength</span><span className="text-blue-400">Armor & Firepower</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Units</span><span className="text-blue-400">7 types + 3 mines</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Buildings</span><span className="text-blue-400">8 structures</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Key Unit</span><span className="text-blue-400">T-22 Zeus Tank</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Playstyle</span><span className="text-blue-400">Defensive fortress</span></div>
                </CardContent>
              </Card>
              <Card className={`bg-gradient-to-br ${REBEL_BG} ${REBEL_BORDER} border`}>
                <CardHeader>
                  <CardTitle className="text-red-400 flex items-center gap-2">
                    <Flame className="h-5 w-5" />
                    Resistance
                  </CardTitle>
                  <CardDescription>Speed, stealth, guerrilla tactics, resourceful</CardDescription>
                </CardHeader>
                <CardContent className="space-y-2 text-sm">
                  <div className="flex justify-between"><span className="text-zinc-400">Strength</span><span className="text-red-400">Speed & Stealth</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Units</span><span className="text-red-400">7 types</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Buildings</span><span className="text-red-400">8 structures</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Key Unit</span><span className="text-red-400">Sniper (long range)</span></div>
                  <div className="flex justify-between"><span className="text-zinc-400">Playstyle</span><span className="text-red-400">Hit & run raids</span></div>
                </CardContent>
              </Card>
            </div>
          </div>
        )}

        {activeTab === "leaderboard" && <LeaderboardTab />}
        {activeTab === "maps" && <MapsTab />}
        {activeTab === "units" && <UnitsTab />}
        {activeTab === "replays" && <ReplaysTab />}
        {activeTab === "chat" && <ChatTab />}
      </main>

      {/* Footer */}
      <footer className="relative z-10 border-t border-zinc-800/50 bg-[#0a0e17]/80 backdrop-blur-xl mt-auto">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between text-xs text-zinc-600">
          <span>Art of War 2: Online — Community Web Client</span>
          <span>v0.2.0-ALPHA • Java 21 • FXGL • Spring Boot</span>
        </div>
      </footer>
    </div>
  );
}
