// LeaveWise theme: Stone, recoloured and reshaped to the Claude Design canvas "LeaveWise UI redesign" (ADR 0008
// amendments). The app renders in light mode only; the dark values below are used only by the summary card, a nested
// dark <Theme>, and are that card's canvas colours.
// Rebuild after every edit: npm run astryx -- theme build src/theme/leavewise.source.ts

import {defineTheme} from '@astryxdesign/core/theme';
import {stoneTheme} from '@astryxdesign/theme-stone';

// Canvas palette.
const INK = '#1d1e24';
const INK_SOFT = '#2a2b33';
const GROUND = '#f5f4f0';
const LINE = '#e2e0d8';
const LINE_STRONG = '#d6d4cc';
const TRACK = '#f0efea';
const MUTED_TEXT = '#575963';
const INDIGO = '#3341b8';
const INDIGO_TEXT = '#2b3aa8';
const INDIGO_TINT = '#eef0fc';
const MARIGOLD = '#ffd8a6';
const MARIGOLD_TEXT = '#6e3a00';
const STONE_GREY = '#e9e8e3';
const STONE_GREY_TEXT = '#4e5058';

export const leavewiseTheme = defineTheme({
  name: 'leavewise',
  extends: stoneTheme,
  tokens: {
    // [light, dark (summary card only)]
    '--color-accent': [INDIGO, MARIGOLD],
    '--color-accent-muted': [INDIGO_TINT, '#ffd8a626'],
    '--color-text-accent': [INDIGO_TEXT, MARIGOLD],
    '--color-icon-accent': [INDIGO_TEXT, MARIGOLD],
    '--color-on-accent': ['#ffffff', INK],

    '--color-background-body': [GROUND, INK],
    '--color-background-surface': ['#ffffff', INK],
    '--color-background-card': ['#ffffff', INK],
    '--color-background-muted': [STONE_GREY, INK_SOFT],
    '--color-background-inverted': [INK, '#ffffff'],
    '--color-border': [LINE, '#3a3b44'],
    '--color-border-emphasized': [LINE_STRONG, '#5e5f66'],

    '--color-text-primary': [INK, '#ffffff'],
    '--color-text-secondary': [MUTED_TEXT, '#c9cad3'],
    '--color-icon-primary': [INK, '#ffffff'],

    // Marigold-tinted panels (no bundled list, paste results) and the "inputs changed" banner.
    '--color-warning-muted': ['#fff5ea', '#fff5ea'],
    '--color-border-orange': ['#f1ceae', '#f1ceae'],

    // Calendar kinds: holiday (marigold), weekly off (stone grey). Same in both, so day squares match everywhere.
    '--color-background-orange': [MARIGOLD, MARIGOLD],
    '--color-text-orange': [MARIGOLD_TEXT, MARIGOLD_TEXT],
    '--color-background-gray': [STONE_GREY, STONE_GREY],
    '--color-text-gray': [STONE_GREY_TEXT, STONE_GREY_TEXT],
  },
  components: {
    // Canvas type sizes as named types: <Heading type="title">, <Text type="field">, ...
    heading: {
      'type:hero': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '52px',
        fontWeight: '800',
        lineHeight: '1.05',
        letterSpacing: '-0.03em',
      },
      'type:hero-phone': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '34px',
        fontWeight: '800',
        lineHeight: '1.1',
        letterSpacing: '-0.03em',
      },
      'type:title': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '26px',
        fontWeight: '700',
        lineHeight: '1.2',
        letterSpacing: '-0.02em',
      },
      'type:title-phone': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '28px',
        fontWeight: '700',
        lineHeight: '1.15',
        letterSpacing: '-0.02em',
      },
      'type:card': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '22px',
        fontWeight: '700',
        lineHeight: '1.25',
        letterSpacing: '-0.01em',
      },
      'type:eyebrow': {
        fontFamily: 'var(--font-family-body)',
        fontSize: '13px',
        fontWeight: '600',
        lineHeight: '1.4',
        letterSpacing: '0.08em',
        textTransform: 'uppercase',
        color: 'var(--color-text-accent)',
      },
      'type:month': {
        fontFamily: 'var(--font-family-body)',
        fontSize: '14px',
        fontWeight: '700',
        lineHeight: '1.4',
      },
    },
    text: {
      'type:wordmark': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '22px',
        fontWeight: '800',
        letterSpacing: '-0.02em',
      },
      'type:field': {fontSize: '14px', fontWeight: '600', lineHeight: '1.4'},
      'type:small': {fontSize: '13px', lineHeight: '1.45'},
      'type:figure': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '26px',
        fontWeight: '700',
        lineHeight: '1.2',
      },
      'type:ratio': {
        fontFamily: 'var(--font-family-heading)',
        fontSize: '18px',
        fontWeight: '700',
        lineHeight: '1.2',
      },
      'type:lead': {fontSize: '17px', lineHeight: '1.5'},
    },
    card: {
      base: {borderRadius: '20px', padding: '28px'},
    },
    button: {
      base: {borderRadius: '12px', fontWeight: '600'},
      // "Plan my leaves": the one large primary action.
      'variant:plan': {
        minHeight: '56px',
        width: '100%',
        borderRadius: '14px',
        backgroundColor: 'var(--color-accent)',
        color: 'var(--color-on-accent)',
        fontFamily: 'var(--font-family-heading)',
        fontSize: '17px',
        fontWeight: '700',
      },
      // The −/+ leave-day buttons: 48 px squares with a light border.
      'variant:stepper': {
        width: '48px',
        height: '48px',
        minWidth: '48px',
        borderRadius: '12px',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: LINE_STRONG,
        backgroundColor: '#ffffff',
        color: INK,
      },
      // Text actions such as "Edit list" and "Show all 12 breaks".
      'variant:link': {
        backgroundColor: 'transparent',
        color: 'var(--color-text-accent)',
        fontWeight: '700',
        paddingInline: '4px',
      },
      // Outlined actions on white: "Add one", "Paste a list", "Load holidays".
      'variant:outline': {
        backgroundColor: '#ffffff',
        color: INK,
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: INK,
        fontWeight: '700',
      },
    },
    // Only the leave-day stepper uses a large text input: a 48 px box (its number's font is in index.css).
    'text-input': {
      'size:lg': {
        minHeight: '48px',
        borderRadius: '12px',
        borderColor: LINE_STRONG,
      },
    },
    'segmented-control': {
      base: {backgroundColor: TRACK, borderRadius: '12px', padding: '4px'},
    },
    'segmented-control-item': {
      base: {minHeight: '44px', borderRadius: '9px', fontWeight: '600', color: MUTED_TEXT},
      selected: {
        backgroundColor: '#ffffff',
        color: INK,
        fontWeight: '700',
        boxShadow: '0 1px 3px rgba(29, 30, 36, 0.12)',
      },
    },
    'toggle-button': {
      // Seven weekday buttons share one row; pressed reads as ink (canvas).
      base: {
        minHeight: '44px',
        minWidth: '0',
        paddingInline: '0',
        borderRadius: '10px',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: LINE_STRONG,
        backgroundColor: '#ffffff',
        color: INK,
        fontSize: '13px',
      },
      'isPressed:true': {
        backgroundColor: INK,
        borderColor: INK,
        color: '#ffffff',
        fontWeight: '600',
      },
    },
    'radio-list': {
      base: {gap: '8px'},
    },
    'radio-list-item': {
      // Each break style is a bordered option card; the chosen one is outlined in indigo on a tint.
      base: {
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: LINE_STRONG,
        borderRadius: '12px',
        padding: '12px 14px',
      },
      selected: {
        borderWidth: '2px',
        borderColor: INDIGO,
        backgroundColor: INDIGO_TINT,
        padding: '11px 13px',
      },
    },
    token: {
      // Header pills: "No login", "Nothing stored".
      base: {
        backgroundColor: '#ffffff',
        borderWidth: '1px',
        borderStyle: 'solid',
        borderColor: LINE,
        borderRadius: '999px',
        color: MUTED_TEXT,
      },
    },
  },
});
