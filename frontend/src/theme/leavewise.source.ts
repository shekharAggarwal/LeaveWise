// LeaveWise theme: Stone with an indigo accent (owner's choice, ADR 0008 amendment).
// Rebuild after every edit: npm run astryx -- theme build src/theme/leavewise.source.ts

import {defineTheme} from '@astryxdesign/core/theme';
import {stoneTheme} from '@astryxdesign/theme-stone';

export const leavewiseTheme = defineTheme({
  name: 'leavewise',
  extends: stoneTheme,
  tokens: {
    // [light, dark]. Leave days, the primary action and selection use the accent, so it carries white (light) or
    // near-black (dark) text at AA contrast.
    '--color-accent': ['#3341b8', '#aab4f6'],
    '--color-accent-muted': ['#3341b81a', '#aab4f626'],
    '--color-text-accent': ['#2b3aa8', '#c3caf9'],
    '--color-icon-accent': ['#2b3aa8', '#c3caf9'],
    '--color-on-accent': ['#ffffff', '#15183a'],
  },
});
