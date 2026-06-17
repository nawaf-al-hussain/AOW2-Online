"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import {
  Shield, Swords, Map, Trophy, MessageSquare, Users,
  Play, ChevronRight, Star, Clock, Download, Search,
  User, Lock, Eye, EyeOff, Volume2, VolumeX, Settings,
  Flame, Target, Zap, Crown, SwordsIcon
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Separator } from "@/components/ui/separator";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { useAuthStore, useChatStore, useMatchmakingStore } from "@/lib/store";
import { login, register, getLeaderboard, getMaps, getReplays } from "@/lib/api";
import { toast } from "sonner";

// ─── Faction Colors ─────────────────────────────────────────────
const CONFED_COLOR = "from-blue-600 to-blue-800";
const CONFED_BG = "bg-blue-900/30";
const CONFED_BORDER = "border-blue-700/50";
const REBEL_COLOR = "from-red-600 to-red-800";
const REBEL_BG = "bg-red-900/30";
const REBEL_BORDER = "border-red-700/50";

// ─── Animated Background ────────────────────────────────────────
function AnimatedBackground() {
  return (
    <div className="fixed inset-0 overflow-hidden pointer-events-none">
      <div className="absolute inset-0 bg-gradient-to-b from-[#0a0e17] via-[#0d1321] to-[#0a0e17]" />
      {/* Grid lines */}
      <svg className="absolute inset-0 w-full h-full opacity-[0.03]">
        <defs>
          <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M 40 0 L 0 0 0 40" fill="none" stroke="white" strokeWidth="0.5" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#grid)" />
      </svg>
      {/* Glowing orbs */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-blue-500/5 rounded-full blur-3xl animate-pulse" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-red-500/5 rounded-full blur-3xl animate-pulse" style={{ animationDelay: "1s" }} />
      <div className="absolute top-1/2 left-1/2 w-64 h-64 bg-amber-500/3 rounded-full blur-3xl animate-pulse" style={{ animationDelay: "2s" }} />
    </div>
  );
}

// ─── Stat Bar Component ─────────────────────────────────────────
function StatBar({ label, value, max, color }: { label: string; value: number; max: number; color: string }) {
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="w-20 text-zinc-500">{label}</span>
      <div className="flex-1 h-1.5 bg-zinc-800 rounded-full overflow-hidden">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${(value / max) * 100}%` }} />
      </div>
      <span className="w-6 text-right text-zinc-400">{value}</span>
    </div>
  );
}

// ─── Login Dialog ────────────────────────────────────────────────
function LoginDialog() {
  const [isLogin, setIsLogin] = useState(true);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const { login: doLogin } = useAuthStore();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const fn = isLogin ? login : register;
      const data = await fn(username, password);
      doLogin(data.token, username, data.eloRating || 1000);
      toast.success(isLogin ? "Welcome back, Commander!" : "Account created! Welcome, Commander!");
    } catch (err: any) {
      toast.error(err.message || "Authentication failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button className="bg-gradient-to-r from-amber-600 to-amber-700 hover:from-amber-500 hover:to-amber-600 text-white font-bold px-8 py-6 text-lg shadow-lg shadow-amber-900/30 transition-all duration-300 hover:scale-105">
          <Shield className="mr-2 h-5 w-5" />
          Login to Play
        </Button>
      </DialogTrigger>
      <DialogContent className="bg-[#111827] border-zinc-800 text-zinc-100 sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-2xl font-bold text-center">
            {isLogin ? "Commander Login" : "New Recruitment"}
          </DialogTitle>
          <DialogDescription className="text-center text-zinc-500">
            {isLogin ? "Enter your credentials to access the battlefield" : "Create your account and join the war"}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4 mt-2">
          <div className="space-y-2">
            <Label htmlFor="username">Callsign</Label>
            <div className="relative">
              <User className="absolute left-3 top-3 h-4 w-4 text-zinc-500" />
              <Input
                id="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="pl-10 bg-zinc-900 border-zinc-700"
                placeholder="Enter your callsign"
                required
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Access Code</Label>
            <div className="relative">
              <Lock className="absolute left-3 top-3 h-4 w-4 text-zinc-500" />
              <Input
                id="password"
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="pl-10 pr-10 bg-zinc-900 border-zinc-700"
                placeholder="Enter your access code"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-3 text-zinc-500 hover:text-zinc-300"
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>
          <Button
            type="submit"
            disabled={loading}
            className="w-full bg-gradient-to-r from-amber-600 to-amber-700 hover:from-amber-500 hover:to-amber-600 text-white font-bold"
          >
            {loading ? "Authenticating..." : isLogin ? "Deploy" : "Enlist"}
          </Button>
          <div className="text-center text-sm text-zinc-500">
            {isLogin ? "New commander?" : "Already enlisted?"}{" "}
            <button
              type="button"
              onClick={() => setIsLogin(!isLogin)}
              className="text-amber-500 hover:text-amber-400 underline"
            >
              {isLogin ? "Create account" : "Login instead"}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

// ─── User Panel (when logged in) ────────────────────────────────
function UserPanel() {
  const { username, elo, logout } = useAuthStore();
  const eloRank = elo >= 1500 ? "General" : elo >= 1200 ? "Colonel" : elo >= 1000 ? "Captain" : elo >= 800 ? "Lieutenant" : "Recruit";
  const eloColor = elo >= 1500 ? "text-amber-400" : elo >= 1200 ? "text-purple-400" : elo >= 1000 ? "text-blue-400" : "text-zinc-400";

  return (
    <Card className={`bg-gradient-to-r ${CONFED_BG} ${CONFED_BORDER} border`}>
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Avatar className="h-12 w-12 bg-gradient-to-br from-amber-600 to-amber-800 border-2 border-amber-600/50">
              <AvatarFallback className="bg-transparent text-amber-100 font-bold text-lg">
                {username?.charAt(0).toUpperCase() || "C"}
              </AvatarFallback>
            </Avatar>
            <div>
              <p className="font-bold text-lg">{username}</p>
              <div className="flex items-center gap-2">
                <Star className={`h-4 w-4 ${eloColor}`} />
                <span className={`text-sm font-semibold ${eloColor}`}>{elo} ELO</span>
                <Badge variant="outline" className="text-xs border-zinc-700 text-zinc-400">{eloRank}</Badge>
              </div>
            </div>
          </div>
          <div className="flex gap-2">
            <Button size="sm" className="bg-green-700 hover:bg-green-600 text-white">
              <Play className="mr-1 h-3 w-3" /> Play
            </Button>
            <Button size="sm" variant="outline" className="border-zinc-700 text-zinc-400" onClick={logout}>
              Logout
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// ─── Matchmaking Panel ──────────────────────────────────────────
function MatchmakingPanel() {
  const { isSearching, searchTime, matchFound, opponent, startSearch, stopSearch, foundMatch } = useMatchmakingStore();
  const [elapsed, setElapsed] = useState(0);
  const [searchKey, setSearchKey] = useState(0); // changes when search starts, resets elapsed

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
  const handleStartSearch = () => {
    setSearchKey((k) => k + 1);
    startSearch();
  };

  // Simulate match found after 8 seconds (demo)
  useEffect(() => {
    if (isSearching && elapsed >= 8 && !matchFound) {
      foundMatch("EnemyCommander");
    }
  }, [elapsed, isSearching, matchFound, foundMatch]);

  const formatTime = (s: number) => `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, "0")}`;

  return (
    <Card className="bg-[#111827] border-zinc-800">
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
            <Button className="bg-gradient-to-r from-green-600 to-green-700 hover:from-green-500 hover:to-green-600 text-white font-bold w-full">
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

// ─── Leaderboard Tab ────────────────────────────────────────────
function LeaderboardTab() {
  const [players, setPlayers] = useState<any[]>([]);
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
      .then(setPlayers)
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

// ─── Maps Tab ───────────────────────────────────────────────────
function MapsTab() {
  const [maps, setMaps] = useState<any[]>([]);
  const [search, setSearch] = useState("");

  const demoMaps = [
    { id: 1, name: "Crossroads", description: "Strategic crossroads with multiple attack paths", uploaderId: "IronCommander", downloadCount: 342, size: "30x20" },
    { id: 2, name: "Island Fortress", description: "Naval assault on a fortified island base", uploaderId: "SteelBlade", downloadCount: 287, size: "25x25" },
    { id: 3, name: "Valley of Death", description: "Narrow valley with ambush points", uploaderId: "WarEagle", downloadCount: 198, size: "40x15" },
    { id: 4, name: "Mountain Pass", description: "High altitude warfare with limited routes", uploaderId: "TacticalNuke", downloadCount: 156, size: "35x20" },
    { id: 5, name: "Desert Storm", description: "Open desert with scattered oases", uploaderId: "GhostRecon", downloadCount: 134, size: "50x40" },
    { id: 6, name: "Urban Warfare", description: "City combat with building-to-building fighting", uploaderId: "PhoenixRise", downloadCount: 112, size: "30x30" },
    { id: 7, name: "Frozen Lake", description: "Ice terrain with treacherous crossings", uploaderId: "ViperStrike", downloadCount: 89, size: "25x20" },
    { id: 8, name: "Jungle Recon", description: "Dense jungle with limited visibility", uploaderId: "ThunderBolt", downloadCount: 76, size: "40x30" },
  ];

  useEffect(() => {
    getMaps().then(setMaps).catch(() => setMaps(demoMaps));
  }, []);

  const data = (maps.length > 0 ? maps : demoMaps).filter((m) =>
    m.name.toLowerCase().includes(search.toLowerCase()) || m.description.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <Map className="h-6 w-6 text-green-500" />
          Community Maps
        </h2>
        <div className="relative w-64">
          <Search className="absolute left-3 top-3 h-4 w-4 text-zinc-500" />
          <Input
            placeholder="Search maps..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-10 bg-zinc-900 border-zinc-700"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {data.map((map) => (
          <Card key={map.id} className="bg-[#111827] border-zinc-800 hover:border-green-700/50 transition-colors group">
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base group-hover:text-green-400 transition-colors">{map.name}</CardTitle>
                <Badge variant="outline" className="text-xs border-zinc-700 text-zinc-500">{map.size || "Custom"}</Badge>
              </div>
              <CardDescription className="text-zinc-500">{map.description}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="flex items-center justify-between text-xs text-zinc-500">
                <span>by {map.uploaderId || "Unknown"}</span>
                <div className="flex items-center gap-1">
                  <Download className="h-3 w-3" />
                  {map.downloadCount}
                </div>
              </div>
              <Button size="sm" className="mt-3 w-full bg-green-700/50 hover:bg-green-700 text-green-100 border border-green-800/50">
                <Download className="mr-1 h-3 w-3" /> Download
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

// ─── Chat Tab ───────────────────────────────────────────────────
function ChatTab() {
  const { messages, addMessage } = useChatStore();
  const [input, setInput] = useState("");
  const { username, isLoggedIn } = useAuthStore();
  const scrollRef = useRef<HTMLDivElement>(null);

  // Demo messages
  const demoMessages = [
    { id: "1", player: "IronCommander", message: "Anyone up for a 1v1?", timestamp: Date.now() - 300000 },
    { id: "2", player: "SteelBlade", message: "Just finished a 45 min match. Intense!", timestamp: Date.now() - 240000 },
    { id: "3", player: "WarEagle", message: "New map uploaded - try Frozen Lake!", timestamp: Date.now() - 180000 },
    { id: "4", player: "System", message: "Tournament starting in 2 hours. Register now!", timestamp: Date.now() - 120000 },
    { id: "5", player: "GhostRecon", message: "Confed or Rebel? I prefer Rebel for the speed advantage", timestamp: Date.now() - 60000 },
  ];

  const allMessages = messages.length > 0 ? messages : demoMessages;

  const sendMessage = () => {
    if (!input.trim() || !isLoggedIn) return;
    addMessage({
      id: Date.now().toString(),
      player: username || "Unknown",
      message: input.trim(),
      timestamp: Date.now(),
    });
    setInput("");
  };

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [allMessages]);

  return (
    <div className="space-y-4 h-full flex flex-col">
      <h2 className="text-2xl font-bold flex items-center gap-2">
        <MessageSquare className="h-6 w-6 text-purple-500" />
        Lobby Chat
      </h2>

      <Card className="bg-[#111827] border-zinc-800 flex-1 flex flex-col">
        <CardContent className="p-4 flex-1 flex flex-col">
          <div className="flex-1 h-[500px] overflow-y-auto" ref={scrollRef}>
            <div className="space-y-3">
              {allMessages.map((msg) => (
                <div key={msg.id} className="flex gap-2">
                  <span className="text-amber-500 font-semibold text-sm shrink-0">{msg.player}:</span>
                  <span className="text-zinc-300 text-sm">{msg.message}</span>
                </div>
              ))}
            </div>
          </div>

          <Separator className="my-3 bg-zinc-800" />

          <div className="flex gap-2">
            <Input
              placeholder={isLoggedIn ? "Type a message..." : "Login to chat"}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && sendMessage()}
              disabled={!isLoggedIn}
              className="bg-zinc-900 border-zinc-700"
            />
            <Button onClick={sendMessage} disabled={!isLoggedIn || !input.trim()} className="bg-purple-700 hover:bg-purple-600 text-white">
              Send
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ─── Replays Tab ────────────────────────────────────────────────
function ReplaysTab() {
  const demoReplays = [
    { id: 1, player1: "IronCommander", player2: "SteelBlade", winner: "IronCommander", mapName: "Crossroads", duration: "23:45", playedAt: "2 hours ago" },
    { id: 2, player1: "WarEagle", player2: "TacticalNuke", winner: "TacticalNuke", mapName: "Valley of Death", duration: "31:12", playedAt: "4 hours ago" },
    { id: 3, player1: "GhostRecon", player2: "PhoenixRise", winner: "GhostRecon", mapName: "Island Fortress", duration: "18:33", playedAt: "6 hours ago" },
    { id: 4, player1: "ViperStrike", player2: "ThunderBolt", winner: "ThunderBolt", mapName: "Urban Warfare", duration: "27:08", playedAt: "8 hours ago" },
    { id: 5, player1: "NightHawk", player2: "StormRider", winner: "NightHawk", mapName: "Desert Storm", duration: "42:51", playedAt: "12 hours ago" },
    { id: 6, player1: "IronCommander", player2: "WarEagle", winner: "IronCommander", mapName: "Mountain Pass", duration: "35:17", playedAt: "1 day ago" },
  ];

  return (
    <div className="space-y-4">
      <h2 className="text-2xl font-bold flex items-center gap-2">
        <Play className="h-6 w-6 text-cyan-500" />
        Recent Replays
      </h2>

      <Card className="bg-[#111827] border-zinc-800">
        <CardContent className="p-0">
          <div className="divide-y divide-zinc-800/50">
            {demoReplays.map((r) => (
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
                  <Button size="sm" variant="outline" className="border-zinc-700 text-cyan-400 hover:bg-cyan-900/20 h-7 text-xs">
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

// ─── Unit Database Tab ──────────────────────────────────────────
function UnitsTab() {
  const confedUnits = [
    { name: "Infantry", hp: 40, damage: 2, speed: 5, armor: 5, range: 4, cost: 10, type: "Infantry" },
    { name: "Grenadier", hp: 40, damage: 2, speed: 6, armor: 5, range: 4, cost: 10, type: "Infantry" },
    { name: "Flame Assault", hp: 60, damage: 6, speed: 4, armor: 3, range: 3, cost: 20, type: "Special" },
    { name: "T-22 Zeus", hp: 70, damage: 6, speed: 7, armor: 5, range: 6, cost: 30, type: "Vehicle" },
    { name: "T-21 Hammer", hp: 50, damage: 8, speed: 5, armor: 9, range: 6, cost: 40, type: "Vehicle" },
    { name: "AV-40 Fortress", hp: 50, damage: 4, speed: 7, armor: 5, range: 9, cost: 20, type: "Vehicle" },
    { name: "MLRS Torrent", hp: 80, damage: 3, speed: 4, armor: 4, range: 9, cost: 50, type: "Vehicle" },
  ];

  const rebelUnits = [
    { name: "Infantry", hp: 40, damage: 2, speed: 5, armor: 4, range: 5, cost: 10, type: "Infantry" },
    { name: "Grenadier", hp: 40, damage: 2, speed: 6, armor: 4, range: 5, cost: 10, type: "Infantry" },
    { name: "Sniper", hp: 30, damage: 8, speed: 4, armor: 6, range: 7, cost: 25, type: "Infantry" },
    { name: "Coyote", hp: 60, damage: 5, speed: 8, armor: 3, range: 5, cost: 25, type: "Vehicle" },
    { name: "Rhino", hp: 90, damage: 7, speed: 5, armor: 7, range: 6, cost: 40, type: "Vehicle" },
    { name: "Armadillo", hp: 70, damage: 6, speed: 6, armor: 6, range: 7, cost: 35, type: "Vehicle" },
    { name: "Porcupine", hp: 80, damage: 8, speed: 4, armor: 5, range: 8, cost: 55, type: "Vehicle" },
  ];

  const renderUnitCard = (unit: any, faction: "confed" | "rebel") => {
    const colors = faction === "confed"
      ? { bg: CONFED_BG, border: CONFED_BORDER, gradient: CONFED_COLOR, accent: "text-blue-400" }
      : { bg: REBEL_BG, border: REBEL_BORDER, gradient: REBEL_COLOR, accent: "text-red-400" };

    return (
      <Card key={unit.name} className={`bg-gradient-to-br ${colors.bg} ${colors.border} border`}>
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardTitle className={`text-sm ${colors.accent}`}>{unit.name}</CardTitle>
            <Badge variant="outline" className="text-[10px] border-zinc-700 text-zinc-500">{unit.type}</Badge>
          </div>
        </CardHeader>
        <CardContent className="space-y-2">
          <StatBar label="HP" value={unit.hp} max={100} color={faction === "confed" ? "bg-blue-500" : "bg-red-500"} />
          <StatBar label="DMG" value={unit.damage} max={10} color="bg-orange-500" />
          <StatBar label="SPD" value={unit.speed} max={10} color="bg-green-500" />
          <StatBar label="ARM" value={unit.armor} max={10} color="bg-purple-500" />
          <StatBar label="RNG" value={unit.range} max={10} color="bg-cyan-500" />
          <div className="flex justify-between items-center pt-1">
            <span className="text-xs text-zinc-500">Cost</span>
            <span className="text-sm font-bold text-amber-400">{unit.cost} <span className="text-xs text-zinc-500">credits</span></span>
          </div>
        </CardContent>
      </Card>
    );
  };

  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold flex items-center gap-2">
        <Target className="h-6 w-6 text-blue-500" />
        Unit Database
      </h2>

      <div>
        <h3 className="text-lg font-semibold mb-3 flex items-center gap-2">
          <Shield className="h-5 w-5 text-blue-500" />
          Global Confederation
        </h3>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
          {confedUnits.map((u) => renderUnitCard(u, "confed"))}
        </div>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-3 flex items-center gap-2">
          <Flame className="h-5 w-5 text-red-500" />
          Resistance
        </h3>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
          {rebelUnits.map((u) => renderUnitCard(u, "rebel"))}
        </div>
      </div>
    </div>
  );
}

// ─── Main Page ──────────────────────────────────────────────────
export default function Home() {
  const { isLoggedIn } = useAuthStore();
  const [activeTab, setActiveTab] = useState("home");

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
                    <div className="text-center">
                      <p className="text-3xl font-bold text-amber-400">1,247</p>
                      <p className="text-xs text-zinc-500">Active Players</p>
                    </div>
                    <div className="text-center">
                      <p className="text-3xl font-bold text-green-400">89</p>
                      <p className="text-xs text-zinc-500">Matches In Progress</p>
                    </div>
                    <div className="text-center">
                      <p className="text-3xl font-bold text-blue-400">342</p>
                      <p className="text-xs text-zinc-500">Community Maps</p>
                    </div>
                    <div className="text-center">
                      <p className="text-3xl font-bold text-purple-400">56</p>
                      <p className="text-xs text-zinc-500">Active Mods</p>
                    </div>
                  </div>
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
