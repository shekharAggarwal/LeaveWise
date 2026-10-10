import * as stylex from '@stylexjs/stylex';

/**
 * Sizes from the Claude Design canvas that have no Astryx token (ADR 0008 amendments). Components use these names
 * instead of raw px, so the canvas's measurements live in one place.
 */
export const canvasVars = stylex.defineVars({
  radiusCard: '20px', // cards, the summary card, the empty results panel
  radiusPanel: '16px', // tinted panels inside a card
  radiusTile: '14px', // summary figures, the tapped-day panel
  radiusChip: '10px', // month chips, 44 px calendar days
  radiusCell: '6px', // 28 px calendar days and day squares
  squareSm: '24px',
  squareMd: '30px',
  squareLg: '40px',
  barHeight: '14px', // break-style day bars
  leaveInput: '88px', // the stepper's number field
  chipMinWidth: '52px',
  hairline: '1px', // gap between a day number and its letter
});
