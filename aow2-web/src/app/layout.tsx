import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";
import { AOW2Provider } from "@/lib/store";

const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Art of War 2: Online",
  description: "The legendary RTS returns — modern multiplayer with map builder, modding, and full campaign.",
  keywords: ["RTS", "Art of War 2", "multiplayer", "strategy game", "online gaming"],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning className="dark">
      <body className={`${inter.variable} font-sans antialiased bg-[#0a0e17] text-zinc-100`}>
        <AOW2Provider>
          {children}
          <Toaster />
        </AOW2Provider>
      </body>
    </html>
  );
}
