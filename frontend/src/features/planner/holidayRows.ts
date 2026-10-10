import type {IsoDate} from '../../api/types';

/** One row per date (PRD: the same date added twice is kept once, showing both names). */
export interface HolidayRow {
  date: IsoDate;
  names: string[];
  added: boolean; // added by the user rather than from the bundled list
}

export function addHoliday(rows: HolidayRow[], date: IsoDate, name: string): HolidayRow[] {
  const existing = rows.find((r) => r.date === date);
  if (existing) {
    return existing.names.includes(name)
      ? rows
      : rows.map((r) => (r.date === date ? {...r, names: [...r.names, name]} : r));
  }
  return [...rows, {date, names: [name], added: true}].sort((a, b) => a.date.localeCompare(b.date));
}
