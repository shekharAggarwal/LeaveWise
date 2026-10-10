/** Whole number 0 to 60, typed as text so "12.5" and "abc" can be refused with the PRD message. */
export function parseLeaveDays(text: string, max: number): number | null {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const value = Number(trimmed);
  return value <= max ? value : null;
}

/** Mirrors the server's HolidayPasteParser.hasControlCharacter, so the browser refuses the same names. */
export function hasControlCharacter(text: string): boolean {
  for (const ch of text) {
    const c = ch.codePointAt(0) ?? 0;
    if (c < 0x20 || c === 0x7f || (c >= 0x80 && c < 0xa0) || c === 0x2028 || c === 0x2029) {
      return true;
    }
  }
  return false;
}
