/** Whole number 0 to 60, typed as text so "12.5" and "abc" can be refused with the PRD message. */
export function parseLeaveDays(text: string, max: number): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return value <= max ? value : null;
}
