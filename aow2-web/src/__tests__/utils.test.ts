import { describe, it, expect } from "vitest";
import { cn } from "@/lib/utils";

describe("cn (className utility)", () => {
  it("merges class names", () => {
    expect(cn("foo", "bar")).toBe("foo bar");
  });

  it("handles conditional classes", () => {
    expect(cn("base", false && "hidden", "visible")).toBe("base visible");
  });

  it("deduplicates tailwind classes", () => {
    // twMerge should handle conflicting classes
    expect(cn("px-4", "px-2")).toBe("px-2");
  });

  it("handles empty inputs", () => {
    expect(cn()).toBe("");
  });

  it("handles undefined and null", () => {
    expect(cn(undefined, null, "valid")).toBe("valid");
  });

  it("handles arrays of classes", () => {
    expect(cn(["px-4", "py-2"], "mx-1")).toBe("px-4 py-2 mx-1");
  });

  it("merges responsive variants correctly", () => {
    expect(cn("text-sm", "md:text-lg")).toBe("text-sm md:text-lg");
  });

  it("last conflicting class wins", () => {
    expect(cn("bg-red-500", "bg-blue-500")).toBe("bg-blue-500");
  });
});
