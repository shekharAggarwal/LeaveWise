import type {HolidayDay, LeaveBreak} from '../../api/types';
import {formatDay} from '../../lib/dates';

export function holidayNames(holidays: HolidayDay[]): string {
  return holidays.map((h) => h.names.join(' / ')).join(', ');
}

export function leaveList(b: LeaveBreak, year: number): string {
  return b.leaveDates.map((d) => formatDay(d, year)).join(', ');
}
