import {describe, expect, it} from 'vitest';
import {addHoliday, type HolidayRow} from './holidayRows';

const rows: HolidayRow[] = [
  {date: '2027-01-26', names: ['Fixture A'], added: false},
  {date: '2027-08-27', names: ['Fixture B'], added: false},
];

describe('addHoliday (PRD: the same date added twice is kept once, showing both names)', () => {
  it('adds a new date in date order, marked as added', () => {
    expect(addHoliday(rows, '2027-03-04', 'Fixture C').map((r) => r.date)).toEqual([
      '2027-01-26',
      '2027-03-04',
      '2027-08-27',
    ]);
    expect(addHoliday(rows, '2027-03-04', 'Fixture C')[1]?.added).toBe(true);
  });

  it('adds a second name to an existing date instead of a second row', () => {
    const next = addHoliday(rows, '2027-08-27', 'Fixture D');
    expect(next).toHaveLength(2);
    expect(next[1]?.names).toEqual(['Fixture B', 'Fixture D']);
  });

  it('ignores the same name on the same date', () => {
    expect(addHoliday(rows, '2027-01-26', 'Fixture A')).toBe(rows);
  });
});
