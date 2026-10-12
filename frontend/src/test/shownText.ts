import {screen} from '@testing-library/react';

/**
 * Elements showing the text on the page. Astryx's screen-reader live region repeats an announcement for a moment,
 * so a plain `getByText` sometimes finds two copies and fails depending on timing.
 */
export const shownText = (text: string) =>
  screen.queryAllByText(text).filter((el) => !el.closest('[data-astryx-live-region]'));
