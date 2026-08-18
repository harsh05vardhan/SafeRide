import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Badge } from "./Badge";

describe("Badge", () => {
  it("renders its children", () => {
    render(<Badge>VERIFIED</Badge>);
    expect(screen.getByText("VERIFIED")).toBeInTheDocument();
  });

  it("defaults to the neutral variant", () => {
    render(<Badge>Default</Badge>);
    expect(screen.getByText("Default")).toHaveClass("bg-slate-100 dark:bg-white/10");
  });

  it("applies the requested variant's classes", () => {
    render(<Badge variant="danger">Rejected</Badge>);
    expect(screen.getByText("Rejected")).toHaveClass("bg-danger/15");
  });

  it("merges a caller-supplied className rather than dropping it", () => {
    render(<Badge className="ml-2">Tagged</Badge>);
    expect(screen.getByText("Tagged")).toHaveClass("ml-2");
  });
});
