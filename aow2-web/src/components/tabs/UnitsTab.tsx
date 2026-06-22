"use client";

import { useState, useEffect } from "react";
import { Shield, Flame, Target } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import StatBar from "@/components/StatBar";
import { getUnits } from "@/lib/api";

const CONFED_COLOR = "from-blue-600 to-blue-800";
const CONFED_BG = "bg-blue-900/30";
const CONFED_BORDER = "border-blue-700/50";
const REBEL_COLOR = "from-red-600 to-red-800";
const REBEL_BG = "bg-red-900/30";
const REBEL_BORDER = "border-red-700/50";

export function UnitsTab() {
  const [isDemo, setIsDemo] = useState(true);
  const [confedUnits, setConfedUnits] = useState<any[]>([]);
  const [rebelUnits, setRebelUnits] = useState<any[]>([]);

  const defaultConfedUnits = [
    { name: "Infantry", hp: 40, damage: 2, speed: 5, armor: 5, range: 4, cost: 10, type: "Infantry" },
    { name: "Grenadier", hp: 40, damage: 2, speed: 6, armor: 5, range: 4, cost: 10, type: "Infantry" },
    { name: "Flame Assault", hp: 60, damage: 6, speed: 4, armor: 3, range: 3, cost: 20, type: "Special" },
    { name: "T-22 Zeus", hp: 70, damage: 6, speed: 7, armor: 5, range: 6, cost: 30, type: "Vehicle" },
    { name: "T-21 Hammer", hp: 50, damage: 8, speed: 5, armor: 9, range: 6, cost: 40, type: "Vehicle" },
    { name: "AV-40 Fortress", hp: 50, damage: 4, speed: 7, armor: 5, range: 9, cost: 20, type: "Vehicle" },
    { name: "MLRS Torrent", hp: 80, damage: 3, speed: 4, armor: 4, range: 9, cost: 50, type: "Vehicle" },
  ];

  const defaultRebelUnits = [
    { name: "Infantry", hp: 40, damage: 2, speed: 5, armor: 4, range: 5, cost: 10, type: "Infantry" },
    { name: "Grenadier", hp: 40, damage: 2, speed: 6, armor: 4, range: 5, cost: 10, type: "Infantry" },
    { name: "Sniper", hp: 30, damage: 8, speed: 4, armor: 6, range: 7, cost: 25, type: "Infantry" },
    { name: "Coyote", hp: 60, damage: 5, speed: 8, armor: 3, range: 5, cost: 25, type: "Vehicle" },
    { name: "Rhino", hp: 90, damage: 7, speed: 5, armor: 7, range: 6, cost: 40, type: "Vehicle" },
    { name: "Armadillo", hp: 70, damage: 6, speed: 6, armor: 6, range: 7, cost: 35, type: "Vehicle" },
    { name: "Porcupine", hp: 80, damage: 8, speed: 4, armor: 5, range: 8, cost: 55, type: "Vehicle" },
  ];

  useEffect(() => {
    // FIX (M9 from CRITICAL_ANALYSIS_REPORT.md): Use the getUnits() helper instead
    // of a direct `fetch('/api/units')` so the request goes through apiUrl() and
    // includes the ?XTransformPort=8080 query string used by the rest of the app.
    getUnits()
      .then((data) => {
        if (data && data.confed && data.rebel) {
          setConfedUnits(data.confed);
          setRebelUnits(data.rebel);
          setIsDemo(false);
        }
      })
      .catch(() => {
        setConfedUnits(defaultConfedUnits);
        setRebelUnits(defaultRebelUnits);
      });
  }, []);

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
      {isDemo && <div className="text-xs text-amber-500 bg-amber-900/20 border border-amber-800/30 rounded px-3 py-1 mb-3">Demo Data — Server unavailable</div>}
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
          {(confedUnits.length > 0 ? confedUnits : defaultConfedUnits).map((u) => renderUnitCard(u, "confed"))}
        </div>
      </div>

      <div>
        <h3 className="text-lg font-semibold mb-3 flex items-center gap-2">
          <Flame className="h-5 w-5 text-red-500" />
          Resistance
        </h3>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
          {(rebelUnits.length > 0 ? rebelUnits : defaultRebelUnits).map((u) => renderUnitCard(u, "rebel"))}
        </div>
      </div>
    </div>
  );
}
