"use client";

import { useState } from "react";
import { Shield, User, Lock, Eye, EyeOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { useAuthStore } from "@/lib/store";
import { login, register } from "@/lib/api";
import { toast } from "sonner";

export default function LoginDialog() {
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
