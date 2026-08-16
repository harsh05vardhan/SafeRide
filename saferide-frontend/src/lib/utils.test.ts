import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("joins plain class names", () => {
    expect(cn("a", "b", "c")).toBe("a b c");
  });

  it("drops falsy values from conditional classes", () => {
    const active = false;
    expect(cn("base", active && "active", "always")).toBe("base always");
  });

  it("resolves conflicting Tailwind utilities to the last one, not both", () => {
    // tailwind-merge's whole job: "p-2 p-4" would otherwise ship both classes to the DOM.
    expect(cn("p-2", "p-4")).toBe("p-4");
  });

  it("leaves non-conflicting classes from different utility groups alone", () => {
    expect(cn("text-sm", "font-bold")).toBe("text-sm font-bold");
  });
});
