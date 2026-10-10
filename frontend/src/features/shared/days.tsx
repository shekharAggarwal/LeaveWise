import * as stylex from '@stylexjs/stylex';
import {
  borderVars,
  colorVars,
  fontWeightVars,
  radiusVars,
  spacingVars,
  textSizeVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import {kindStyles, type DayCode} from './dayCodes';

const styles = stylex.create({
  strip: {display: 'flex', flexWrap: 'wrap', gap: spacingVars['--spacing-1']},
  square: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    borderRadius: radiusVars['--radius-element'],
    fontWeight: fontWeightVars['--font-weight-bold'],
    boxSizing: 'border-box',
  },
  md: {width: '30px', height: '30px', fontSize: textSizeVars['--font-size-sm']},
  sm: {width: '24px', height: '24px', fontSize: textSizeVars['--font-size-xs']},
  lg: {width: '40px', height: '40px', fontSize: textSizeVars['--font-size-base']},
  outline: {
    borderWidth: spacingVars['--spacing-0-5'],
    borderStyle: 'solid',
    borderColor: colorVars['--color-accent'],
  },
  dashed: {
    borderWidth: borderVars['--border-width'],
    borderStyle: 'dashed',
    borderColor: colorVars['--color-border-emphasized'],
  },
});

/**
 * One coloured square with its letter (canvas "sq"). Raw span: no Astryx component draws a fixed-size coloured
 * square; it is decorative wherever the surrounding text already says the same.
 */
export function DaySquare({
  code,
  size = 'md',
}: {
  code: DayCode | 'outline' | 'dashed';
  size?: 'sm' | 'md' | 'lg';
}) {
  const look =
    code === 'outline' ? styles.outline : code === 'dashed' ? styles.dashed : kindStyles[code];
  return (
    <span aria-hidden="true" {...stylex.props(styles.square, styles[size], look)}>
      {code === 'outline' || code === 'dashed' ? '' : code}
    </span>
  );
}

/** A break drawn as day squares; decorative, since the row's text says the same. */
export function DayStrip({codes, size = 'md'}: {codes: DayCode[]; size?: 'sm' | 'md'}) {
  return (
    <span aria-hidden="true" {...stylex.props(styles.strip)}>
      {codes.map((code, i) => (
        <DaySquare key={i} code={code} size={size} />
      ))}
    </span>
  );
}
