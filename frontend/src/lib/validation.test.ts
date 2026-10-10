import {describe, expect, it} from 'vitest';
import {hasControlCharacter, parseLeaveDays} from './validation';

describe('parseLeaveDays (PRD: whole number from 0 to 60)', () => {
  it.each([
    ['0', 0],
    ['12', 12],
    [' 12 ', 12],
    ['060', 60],
    ['60', 60],
  ])('accepts %j as %d', (text, expected) => {
    expect(parseLeaveDays(text, 60)).toBe(expected);
  });

  it.each(['', '61', '-1', '+5', '1e1', '12.5', '12.0', 'abc', '١٢'])('refuses %j', (text) => {
    expect(parseLeaveDays(text, 60)).toBeNull();
  });
});

describe('hasControlCharacter (same rule as the server)', () => {
  it.each(['Fixture\tDay', 'Fixture\nDay', 'Fixture\u007fDay', 'Fixture\u0085Day', 'A\u2028B'])(
    'refuses %j',
    (text) => {
      expect(hasControlCharacter(text)).toBe(true);
    },
  );

  it.each(['Fixture Day', 'Fixture\u00a0Day', 'दिवाली', '\u{1F389} Party'])(
    'accepts %j',
    (text) => {
      expect(hasControlCharacter(text)).toBe(false);
    },
  );
});
