import { useState } from "react";
import { Star } from "lucide-react";
import { cn } from "@/lib/utils";

interface RatingStarsProps {
  value: number;
  onChange: (value: number) => void;
  size?: number;
}

/** A plain 1-5 star picker - hover previews the value, click commits it. */
export function RatingStars({ value, onChange, size = 32 }: RatingStarsProps) {
  const [hovered, setHovered] = useState<number | null>(null);
  const displayed = hovered ?? value;

  return (
    <div className="flex items-center gap-1" onMouseLeave={() => setHovered(null)}>
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          onClick={() => onChange(star)}
          onMouseEnter={() => setHovered(star)}
          aria-label={`Rate ${star} star${star > 1 ? "s" : ""}`}
          className="p-0.5"
        >
          <Star
            size={size}
            className={cn("transition-colors", star <= displayed ? "fill-primary text-primary" : "fill-transparent text-slate-300")}
          />
        </button>
      ))}
    </div>
  );
}
