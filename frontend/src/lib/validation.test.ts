import {describe, expect, it} from 'vitest';
import {hasControlCharacter, parseLeaveDays, stepLeaveDays} from './validation';

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

describe('stepLeaveDays (the −/+ buttons)', () => {
  it.each([
    ['12', 1, 13],
    ['12', -1, 11],
    [' 12 ', 1, 13],
    ['007', 1, 8],
    ['60', 1, 60],
    ['0', -1, 0],
    ['75', -1, 60],
    ['12.5', 1, 1],
    ['abc', 1, 1],
    ['abc', -1, 0],
    ['', 1, 1],
    ['-5', 1, 1],
    ['99999999999999999999', -1, 60],
  ] as const)('%j and %d give %d', (text, delta, expected) => {
    expect(stepLeaveDays(text, delta, 60)).toBe(expected);
  });
});
