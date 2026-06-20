import { NextResponse } from "next/server";

export async function GET() {
  return NextResponse.json({
    status: "ok",
    service: "aow2-online-dashboard",
    version: "0.2.0"
  });
}