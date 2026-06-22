"use client";

import { useState, useEffect, useRef } from "react";
import { MessageSquare } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { useAuthStore, useChatStore } from "@/lib/store";

export function ChatTab() {
  const { messages, addMessage } = useChatStore();
  const [input, setInput] = useState("");
  const { username, isLoggedIn } = useAuthStore();
  const scrollRef = useRef<HTMLDivElement>(null);

  // Demo messages — shown only when no real messages have been received.
  const demoMessages = [
    { id: "1", player: "IronCommander", message: "Anyone up for a 1v1?", timestamp: Date.now() - 300000 },
    { id: "2", player: "SteelBlade", message: "Just finished a 45 min match. Intense!", timestamp: Date.now() - 240000 },
    { id: "3", player: "WarEagle", message: "New map uploaded - try Frozen Lake!", timestamp: Date.now() - 180000 },
    { id: "4", player: "System", message: "Tournament starting in 2 hours. Register now!", timestamp: Date.now() - 120000 },
    { id: "5", player: "GhostRecon", message: "Confed or Rebel? I prefer Rebel for the speed advantage", timestamp: Date.now() - 60000 },
  ];

  // FIX (H4 from CRITICAL_ANALYSIS_REPORT.md): Derive `isDemo` from messages.length
  // instead of calling setIsDemo(false) inside a render-time ternary, which is a
  // React anti-pattern that triggers a re-render warning and can loop in strict mode.
  const isDemo = messages.length === 0;
  const allMessages = isDemo ? demoMessages : messages;

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
      {isDemo && <div className="text-xs text-amber-500 bg-amber-900/20 border border-amber-800/30 rounded px-3 py-1 mb-3">Demo Data — Server unavailable</div>}
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
