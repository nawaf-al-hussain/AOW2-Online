"use client";

import { useState, useEffect } from "react";
import { Map, Search, Download } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getMaps } from "@/lib/api";

export function MapsTab() {
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
