/**
 * Days off per leave to one decimal, rounded half up like the server (LeaveBreak.daysOffPerLeave:
 * Math.round(days * 10 / leaves) / 10), so the summary and the break rows always agree. toFixed alone
 * rounds the binary value and can differ (43 / 20 gives 2.1, the server says 2.2).
 */
export function daysOffPerLeave(days: number, leaves: number): string {
  return (Math.round((days * 10) / leaves) / 10).toFixed(1);
}

export function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}
