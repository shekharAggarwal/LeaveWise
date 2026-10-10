/** The LeaveWise mark from the canvas: an indigo calendar with a marigold break. Decorative; the word mark names it. */
export function Logo({size}: {size: number}) {
  return (
    <svg width={size} height={size} viewBox="0 0 36 36" aria-hidden="true" focusable="false">
      <rect x="2" y="5" width="32" height="29" rx="7" fill="var(--color-accent)" />
      <rect x="9" y="1" width="4" height="8" rx="2" fill="var(--color-text-primary)" />
      <rect x="23" y="1" width="4" height="8" rx="2" fill="var(--color-text-primary)" />
      <rect x="8" y="16" width="20" height="10" rx="3" fill="var(--color-background-orange)" />
    </svg>
  );
}
