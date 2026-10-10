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
