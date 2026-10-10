// Mirrors docs/API.md. Change both together.

export type IsoDate = string; // YYYY-MM-DD

export type Weekday =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export type BreakStyleId = 'LONG_WEEKENDS' | 'SHORT_TRIPS' | 'BIG_TRIPS';

export interface Options {
  today: IsoDate;
  timeZone: string;
  years: number[];
  defaultYear: number;
  leaveDays: {min: number; max: number; default: number};
  defaultWeeklyOffs: Weekday[];
  breakStyles: {id: BreakStyleId; label: string; minDays: number}[];
  defaultBreakStyle: BreakStyleId;
  maxPastedLines: number;
}

export interface HolidayDay {
  date: IsoDate;
  names: string[];
}

export interface HolidaysResponse {
  year: number;
  available: boolean;
  list: string | null;
  source: {publisher: string; title: string; url: string; retrievedOn: IsoDate} | null;
  reminder: string;
  holidays: HolidayDay[];
}

export interface ParseResponse {
  holidays: {line: number; date: IsoDate; name: string}[];
  errors: {line: number; reason: string}[];
}

export interface PlanRequest {
  year: number;
  leaveDays: number;
  weeklyOffs: Weekday[];
  breakStyle: BreakStyleId;
  holidays: {date: IsoDate; name: string}[];
}

export type DayKind = 'WORKING' | 'WEEKLY_OFF' | 'HOLIDAY' | 'LEAVE';

export interface LeaveBreak {
  start: IsoDate;
  end: IsoDate;
  days: number;
  leaveDates: IsoDate[];
  holidays: HolidayDay[];
  daysOffPerLeave: number;
}

export interface FreeLongWeekend {
  start: IsoDate;
  end: IsoDate;
  days: number;
  holidays: HolidayDay[];
}

export type NoteCode = 'NO_DAYS_LEFT' | 'NO_LEAVE_USED' | 'NO_BREAK_POSSIBLE' | 'LEAVES_LEFT_OVER';

export interface CalendarDay {
  date: IsoDate;
  kind: DayKind;
  planned: boolean;
  holidayNames: string[];
  breakIndex: number | null;
  freeLongWeekendIndex: number | null;
}

export interface PlanResponse {
  year: number;
  planningFrom: IsoDate | null;
  planningTo: IsoDate | null;
  breakStyle: BreakStyleId;
  minBreakDays: number;
  summary: {
    leavesAvailable: number;
    leavesUsed: number;
    leavesUnused: number;
    totalDaysOff: number;
    breakCount: number;
  };
  breaks: LeaveBreak[];
  freeLongWeekends: FreeLongWeekend[];
  notes: {code: NoteCode; message: string}[];
  days: CalendarDay[];
}

export interface Problem {
  type: string;
  title: string;
  status: number;
  detail: string;
  errors: {field: string; message: string}[];
}
