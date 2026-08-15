import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Standard shadcn/ui class-merging helper. Combines conditional class
 * names via clsx and resolves Tailwind conflicts via tailwind-merge, so
 * `cn("p-2", condition && "p-4")` always yields a single, correct class.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
