import * as stylex from '@stylexjs/stylex';
import {colorVars} from '@astryxdesign/core/theme/tokens.stylex';
import type {IsoDate, LeaveBreak} from '../../api/types';

/** A day off inside a break: Leave, Holiday or Weekly off. The letter always goes with the colour (PRD). */
export type DayCode = 'L' | 'H' | 'W';

export function addDays(date: IsoDate, n: number): IsoDate {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y ?? 1970, (m ?? 1) - 1, (d ?? 1) + n)).toISOString().slice(0, 10);
}

/**
 * Each day of a break as a letter: a break holds only days off, so a day that is neither a leave nor a holiday
 * is a weekly off. Built from the break itself, so days in the next or previous year are included.
 */
export function breakDays(b: LeaveBreak): DayCode[] {
  const leaves = new Set(b.leaveDates);
  const holidays = new Set(b.holidays.map((h) => h.date));
  const codes: DayCode[] = [];
  for (let day = b.start; day <= b.end; day = addDays(day, 1)) {
    codes.push(leaves.has(day) ? 'L' : holidays.has(day) ? 'H' : 'W');
  }
  return codes;
}

export const kindStyles = stylex.create({
  L: {backgroundColor: colorVars['--color-accent'], color: colorVars['--color-on-accent']},
  H: {
    backgroundColor: colorVars['--color-background-orange'],
    color: colorVars['--color-text-orange'],
  },
  W: {
    backgroundColor: colorVars['--color-background-gray'],
    color: colorVars['--color-text-gray'],
  },
});
