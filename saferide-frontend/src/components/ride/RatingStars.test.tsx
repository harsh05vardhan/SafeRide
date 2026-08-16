import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { RatingStars } from "./RatingStars";

describe("RatingStars", () => {
  it("renders one labelled button per star, 1 through 5", () => {
    render(<RatingStars value={0} onChange={() => {}} />);
    for (let star = 1; star <= 5; star++) {
      expect(screen.getByLabelText(`Rate ${star} star${star > 1 ? "s" : ""}`)).toBeInTheDocument();
    }
  });

  it("calls onChange with the clicked star's value", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<RatingStars value={0} onChange={onChange} />);

    await user.click(screen.getByLabelText("Rate 4 stars"));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(4);
  });

  it("fills stars up to and including the current value", () => {
    render(<RatingStars value={3} onChange={() => {}} />);

    const star2 = screen.getByLabelText("Rate 2 stars").querySelector("svg");
    const star3 = screen.getByLabelText("Rate 3 stars").querySelector("svg");
    const star4 = screen.getByLabelText("Rate 4 stars").querySelector("svg");

    expect(star2).toHaveClass("fill-primary");
    expect(star3).toHaveClass("fill-primary");
    expect(star4).toHaveClass("fill-transparent");
  });

  it("previews a hovered star without committing it, and reverts on mouse leave", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<RatingStars value={1} onChange={onChange} />);

    await user.hover(screen.getByLabelText("Rate 5 stars"));
    expect(screen.getByLabelText("Rate 5 stars").querySelector("svg")).toHaveClass("fill-primary");
    expect(onChange).not.toHaveBeenCalled();

    await user.unhover(screen.getByLabelText("Rate 5 stars"));
    expect(screen.getByLabelText("Rate 5 stars").querySelector("svg")).toHaveClass("fill-transparent");
    // Still committed at the original value=1, never touched by hovering alone.
    expect(screen.getByLabelText("Rate 1 star").querySelector("svg")).toHaveClass("fill-primary");
  });
});
