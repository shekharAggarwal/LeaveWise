import {describe, expect, it} from 'vitest';
import type {CalendarDay} from '../../api/types';
import {plan2027} from '../../test/fakeApi';
import {dayDetail} from './describeDay';

const day = (date: string): CalendarDay => {
  const found = plan2027.days.find((d) => d.date === date);
  if (!found) throw new Error(date);
  return found;
};

describe('dayDetail (the tapped-day panel and sheet)', () => {
  it('a leave day names its break, the break days and the holidays in it', () => {
    expect(dayDetail(day('2027-03-05'), plan2027)).toEqual({
      heading: 'Fri, 5 Mar',
      kind: 'Leave day',
      detail: 'Break 1: Thu, 4 Mar – Sun, 7 Mar, 4 days off with Fixture Holiday 2',
    });
  });

  it('a holiday keeps its names whole, even with " · " in them', () => {
    const named: CalendarDay = {...day('2027-01-26'), holidayNames: ['Fixture · Day']};
    expect(dayDetail(named, plan2027)).toMatchObject({
      heading: 'Tue, 26 Jan',
      kind: 'Holiday, Fixture · Day',
      detail: 'Not part of a break.',
    });
  });

  it('a free long weekend day says so', () => {
    expect(dayDetail(day('2027-08-28'), plan2027).detail).toBe(
      'Free long weekend: Fri, 27 Aug – Sun, 29 Aug',
    );
  });

  it('a past day says it is not planned', () => {
    const past: CalendarDay = {...day('2027-02-01'), planned: false};
    expect(dayDetail(past, plan2027)).toMatchObject({
      kind: 'Working day',
      detail: 'Already passed, so it is not planned.',
    });
  });
});
