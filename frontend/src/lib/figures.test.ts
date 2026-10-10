import {describe, expect, it} from 'vitest';
import {daysOffPerLeave} from './figures';

describe('daysOffPerLeave (same rounding as the server, LeaveBreak.daysOffPerLeave)', () => {
  it.each([
    [7, 2, '3.5'],
    [36, 12, '3.0'],
    [43, 20, '2.2'], // toFixed alone gives 2.1 here: 2.15 is stored as 2.1499...
    [38, 12, '3.2'],
    [10, 3, '3.3'],
  ])('%d days over %d leaves is %s', (days, leaves, expected) => {
    expect(daysOffPerLeave(days, leaves)).toBe(expected);
  });
});
