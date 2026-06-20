"use client";

import { Play, Star } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { useAuthStore } from "@/lib/store";

const CONFED_BG = "bg-blue-900/30";
const CONFED_BORDER = "border-blue-700/50";

export default function UserPanel() {
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
