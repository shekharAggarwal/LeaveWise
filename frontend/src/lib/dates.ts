// Dates are calendar dates (YYYY-MM-DD) with no time zone. They are parsed as UTC and formatted by
// hand, so the browser's time zone can never shift a day and the format is always "Fri, 2 Oct".

import type {IsoDate} from '../api/types';

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS_SHORT = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
];
const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

function parse(date: IsoDate): Date {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y ?? 1970, (m ?? 1) - 1, d ?? 1));
}

/** "Fri, 2 Oct", with the year added when it differs from {@code planYear}. */
export function formatDay(date: IsoDate, planYear?: number): string {
  const d = parse(date);
  const text = `${WEEKDAYS[d.getUTCDay()]}, ${d.getUTCDate()} ${MONTHS_SHORT[d.getUTCMonth()]}`;
  return planYear !== undefined && d.getUTCFullYear() !== planYear
    ? `${text} ${d.getUTCFullYear()}`
    : text;
}

/** "Thu, 4 Mar – Sun, 7 Mar", or one day when start equals end. */
export function formatRange(start: IsoDate, end: IsoDate, planYear?: number): string {
  return start === end
    ? formatDay(start, planYear)
    : `${formatDay(start, planYear)} – ${formatDay(end, planYear)}`;
}

/** 1 → "January". */
export function monthName(month: number): string {
  return MONTHS[month - 1] ?? '';
}

/** Monday = 0 … Sunday = 6, for Monday-first calendar grids. */
export function weekdayIndex(date: IsoDate): number {
  return (parse(date).getUTCDay() + 6) % 7;
}

export function monthOf(date: IsoDate): number {
  return Number(date.slice(5, 7));
}

export function dayOfMonth(date: IsoDate): number {
  return Number(date.slice(8, 10));
}
