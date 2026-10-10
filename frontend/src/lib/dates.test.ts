import {describe, expect, it} from 'vitest';
import {formatDay, formatRange, monthName, weekdayIndex} from './dates';

describe('Indian date format (Fri, 2 Oct)', () => {
  it('formats a date with weekday, day and short month', () => {
    expect(formatDay('2026-10-02')).toBe('Fri, 2 Oct');
  });

  it('adds the year only when it differs from the plan year', () => {
    expect(formatDay('2028-01-01', 2027)).toBe('Sat, 1 Jan 2028');
    expect(formatDay('2027-01-01', 2027)).toBe('Fri, 1 Jan');
  });

  it('never shifts the day for the browser time zone', () => {
    expect(formatDay('2027-03-01')).toBe('Mon, 1 Mar');
    expect(formatDay('2028-02-29')).toBe('Tue, 29 Feb');
  });

  it('formats a range, collapsing a single day', () => {
    expect(formatRange('2027-03-04', '2027-03-07', 2027)).toBe('Thu, 4 Mar – Sun, 7 Mar');
    expect(formatRange('2027-03-04', '2027-03-04', 2027)).toBe('Thu, 4 Mar');
    expect(formatRange('2027-12-31', '2028-01-02', 2027)).toBe('Fri, 31 Dec – Sun, 2 Jan 2028');
  });

  it('gives month names and Monday-first weekday indexes', () => {
    expect(monthName(1)).toBe('January');
    expect(weekdayIndex('2027-03-01')).toBe(0); // Monday
    expect(weekdayIndex('2027-03-07')).toBe(6); // Sunday
  });
});
