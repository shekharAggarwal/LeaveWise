import type {CalendarDay, PlanResponse} from '../../api/types';
import {formatDay, formatRange} from '../../lib/dates';

/** The words a screen reader hears, and the line shown when a day is tapped. */
export function describeDay(day: CalendarDay, plan: PlanResponse): string {
  const date = formatDay(day.date, plan.year);
  const kind =
    day.kind === 'LEAVE'
      ? 'leave day'
      : day.kind === 'HOLIDAY'
        ? `holiday, ${day.holidayNames.join(' / ')}`
        : day.kind === 'WEEKLY_OFF'
          ? 'weekly off'
          : 'working day';
  const parts = [`${date}: ${kind}`];
  if (day.breakIndex !== null) {
    const b = plan.breaks[day.breakIndex];
    if (b) parts.push(`part of a ${b.days}-day break (${formatRange(b.start, b.end, plan.year)})`);
  } else if (day.freeLongWeekendIndex !== null) {
    const w = plan.freeLongWeekends[day.freeLongWeekendIndex];
    if (w) parts.push(`part of a free long weekend (${formatRange(w.start, w.end, plan.year)})`);
  }
  if (!day.planned) parts.push('already passed');
  return parts.join(', ');
}

/** The tapped-day panel (canvas): "Mon, 25 Jan · Leave day" and the break it belongs to. */
export function dayDetail(day: CalendarDay, plan: PlanResponse): {title: string; detail: string} {
  const kind =
    day.kind === 'LEAVE'
      ? 'Leave day'
      : day.kind === 'HOLIDAY'
        ? `Holiday, ${day.holidayNames.join(' / ')}`
        : day.kind === 'WEEKLY_OFF'
          ? 'Weekly off'
          : 'Working day';
  const title = `${formatDay(day.date, plan.year)} · ${kind}`;
  if (day.breakIndex !== null) {
    const b = plan.breaks[day.breakIndex];
    if (b) {
      const holidays = b.holidays.flatMap((h) => h.names);
      return {
        title,
        detail: `Break ${day.breakIndex + 1}: ${formatRange(b.start, b.end, plan.year)}, ${b.days} days off${
          holidays.length > 0 ? ` with ${holidays.join(', ')}` : ''
        }`,
      };
    }
  }
  if (day.freeLongWeekendIndex !== null) {
    const w = plan.freeLongWeekends[day.freeLongWeekendIndex];
    if (w) return {title, detail: `Free long weekend: ${formatRange(w.start, w.end, plan.year)}`};
  }
  if (!day.planned) return {title, detail: 'Already passed, so it is not planned.'};
  return {title, detail: 'Not part of a break.'};
}

/** The day's code for its square: L, H, W, or none for a working day. */
export function dayCode(day: CalendarDay): 'L' | 'H' | 'W' | null {
  return day.kind === 'LEAVE'
    ? 'L'
    : day.kind === 'HOLIDAY'
      ? 'H'
      : day.kind === 'WEEKLY_OFF'
        ? 'W'
        : null;
}
