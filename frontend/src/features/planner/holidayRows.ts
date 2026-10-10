import type {IsoDate} from '../../api/types';

/** One row per date (PRD: the same date added twice is kept once, showing both names). */
export interface HolidayRow {
  date: IsoDate;
  names: string[];
  added: boolean; // added by the user rather than from the bundled list
  /** A floater from a company list: listed, but a day off only when ticked. */
  optional?: boolean;
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

/** "6 of 6 on": how many holidays count toward the plan. */
export function countOn(rows: HolidayRow[], ticked: Set<IsoDate>): string {
  return `${rows.filter((r) => ticked.has(r.date)).length} of ${rows.length} on`;
}
