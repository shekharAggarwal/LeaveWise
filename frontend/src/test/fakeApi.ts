// Test doubles for the LeaveWise API. Holiday names are fixtures, not real holidays.
import {vi} from 'vitest';
import type {
  CalendarDay,
  HolidaysResponse,
  Options,
  PlanRequest,
  PlanResponse,
  Problem,
} from '../api/types';

export const options: Options = {
  today: '2026-10-09',
  timeZone: 'Asia/Kolkata',
  years: [2026, 2027],
  defaultYear: 2027,
  leaveDays: {min: 0, max: 60, default: 12},
  defaultWeeklyOffs: ['SATURDAY', 'SUNDAY'],
  breakStyles: [
    {id: 'LONG_WEEKENDS', label: 'Long weekends', minDays: 3},
    {id: 'SHORT_TRIPS', label: 'Short trips', minDays: 5},
    {id: 'BIG_TRIPS', label: 'Big trips', minDays: 9},
  ],
  defaultBreakStyle: 'LONG_WEEKENDS',
  maxPastedLines: 50,
};

export const holidays2027: HolidaysResponse = {
  year: 2027,
  available: true,
  kind: 'government',
  list: 'Fixture list',
  source: {
    publisher: 'Fixture publisher',
    title: 'Fixture memorandum',
    url: 'https://fixture.gov.in/list.pdf',
    retrievedOn: '2026-10-01',
  },
  reminder: "Check this against your company's official holiday list.",
  holidays: [
    {date: '2027-01-26', names: ['Fixture Holiday 1'], optional: false},
    {date: '2027-03-04', names: ['Fixture Holiday 2'], optional: false},
    {date: '2027-08-27', names: ['Fixture Holiday 3', 'Fixture Holiday 4'], optional: false},
  ],
};

export const noHolidays2026: HolidaysResponse = {
  year: 2026,
  available: false,
  kind: null,
  list: null,
  source: null,
  reminder: "Check this against your company's official holiday list.",
  holidays: [],
};

function yearDays(year: number, mark: (date: string) => Partial<CalendarDay>): CalendarDay[] {
  const days: CalendarDay[] = [];
  for (let d = new Date(Date.UTC(year, 0, 1)); d.getUTCFullYear() === year;) {
    const iso = d.toISOString().slice(0, 10);
    const weekday = d.getUTCDay();
    days.push({
      date: iso,
      kind: weekday === 0 || weekday === 6 ? 'WEEKLY_OFF' : 'WORKING',
      planned: true,
      holidayNames: [],
      breakIndex: null,
      freeLongWeekendIndex: null,
      ...mark(iso),
    });
    d = new Date(d.getTime() + 86_400_000);
  }
  return days;
}

/** Two breaks: Thu 4 Mar holiday + Fri 5 Mar leave (to Sun 7 Mar), and Sat 14 to Mon 16 Aug. */
export const plan2027: PlanResponse = {
  year: 2027,
  planningFrom: '2027-01-01',
  planningTo: '2027-12-31',
  breakStyle: 'LONG_WEEKENDS',
  minBreakDays: 3,
  summary: {leavesAvailable: 2, leavesUsed: 2, leavesUnused: 0, totalDaysOff: 7, breakCount: 2},
  breaks: [
    {
      start: '2027-03-04',
      end: '2027-03-07',
      days: 4,
      leaveDates: ['2027-03-05'],
      holidays: [{date: '2027-03-04', names: ['Fixture Holiday 2']}],
      daysOffPerLeave: 4,
    },
    {
      start: '2027-08-14',
      end: '2027-08-16',
      days: 3,
      leaveDates: ['2027-08-16'],
      holidays: [],
      daysOffPerLeave: 3,
    },
  ],
  freeLongWeekends: [
    {
      start: '2027-08-27',
      end: '2027-08-29',
      days: 3,
      holidays: [{date: '2027-08-27', names: ['Fixture Holiday 3', 'Fixture Holiday 4']}],
    },
  ],
  notes: [],
  days: yearDays(2027, (date) => {
    if (date === '2027-03-04')
      return {kind: 'HOLIDAY', holidayNames: ['Fixture Holiday 2'], breakIndex: 0};
    if (date === '2027-03-05') return {kind: 'LEAVE', breakIndex: 0};
    if (date === '2027-03-06' || date === '2027-03-07') return {breakIndex: 0};
    if (date === '2027-08-14' || date === '2027-08-15') return {breakIndex: 1};
    if (date === '2027-08-16') return {kind: 'LEAVE', breakIndex: 1};
    if (date === '2027-08-27')
      return {
        kind: 'HOLIDAY',
        holidayNames: ['Fixture Holiday 3', 'Fixture Holiday 4'],
        freeLongWeekendIndex: 0,
      };
    if (date === '2027-08-28' || date === '2027-08-29') return {freeLongWeekendIndex: 0};
    if (date === '2027-01-26') return {kind: 'HOLIDAY', holidayNames: ['Fixture Holiday 1']};
    return {};
  }),
};

export const zeroLeavePlan2027: PlanResponse = {
  ...plan2027,
  summary: {leavesAvailable: 0, leavesUsed: 0, leavesUnused: 0, totalDaysOff: 0, breakCount: 0},
  breaks: [],
  notes: [
    {code: 'NO_LEAVE_USED', message: 'No leave used. These are the long weekends you already get.'},
  ],
  days: plan2027.days.map((d) => ({
    ...d,
    kind: d.kind === 'LEAVE' ? 'WORKING' : d.kind,
    breakIndex: null,
  })),
};

export interface Recorded {
  method: string;
  url: string;
  body: unknown;
}

type Reply = {status: number; json?: unknown; text?: string; rawText?: string};
type Handler = (url: string, body: unknown) => Reply | Promise<Reply>;

/** A response the test releases later, for race-condition tests. */
export function deferred() {
  let release!: (reply: Reply) => void;
  const promise = new Promise<Reply>((resolve) => {
    release = resolve;
  });
  return {promise, release};
}

/** Replaces global fetch; records every request. Unknown URLs fail the test loudly. */
export function stubApi(overrides: Partial<Record<string, Handler>> = {}) {
  const calls: Recorded[] = [];
  const handlers: Record<string, Handler> = {
    'GET /api/options': () => ({status: 200, json: options}),
    'GET /api/holidays': (url) => ({
      status: 200,
      json: url.includes('year=2026') ? noHolidays2026 : holidays2027,
    }),
    'POST /api/plan': (_url, body) => ({
      status: 200,
      json: (body as PlanRequest).leaveDays === 0 ? zeroLeavePlan2027 : plan2027,
    }),
    'POST /api/plan/ics': () => ({status: 200, text: 'BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n'}),
    'POST /api/holidays/parse': () => ({
      status: 200,
      json: {
        holidays: [{line: 1, date: '2027-11-15', name: 'Fixture Company Day'}],
        errors: [{line: 2, reason: '2027-02-30 is not a real date.'}],
      },
    }),
    ...overrides,
  } as Record<string, Handler>;

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({method, url, body});
    const key = `${method} ${url.split('?')[0]}`;
    const handler = handlers[key];
    if (!handler) throw new Error(`Unexpected request in test: ${key}`);
    const result = await handler(url, body);
    const isProblem = result.status >= 400;
    return new Response(result.rawText ?? result.text ?? JSON.stringify(result.json), {
      status: result.status,
      headers: {
        'Content-Type': result.text
          ? 'text/calendar; charset=utf-8'
          : isProblem
            ? 'application/problem+json'
            : 'application/json',
      },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return {calls, fetchMock};
}

export function problem(status: number, detail: string, field = 'leaveDays'): Problem {
  return {
    type: 'urn:leavewise:problem:invalid-input',
    title: 'Check your inputs',
    status,
    detail,
    errors: [{field, message: detail}],
  };
}
