// Regression tests for the Phase 4 ui-reviewer pass. Each one failed against the code it reviewed.
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {App} from './App';
import {stubApi} from './test/fakeApi';

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

/** Error text shown on the page; Astryx's screen-reader live region keeps the last announcement. */
const shownText = (text: string) =>
  screen.queryAllByText(text).filter((el) => !el.closest('[data-astryx-live-region]'));

async function renderLoaded() {
  stubApi();
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole('checkbox', {name: /Fixture Holiday 1/});
  return user;
}

describe('field errors clear once the field is edited (review 5)', () => {
  it('leave days', async () => {
    const user = await renderLoaded();
    const input = screen.getByRole('textbox', {name: 'Leave days'});
    await user.clear(input);
    await user.type(input, '61');
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(screen.getByText('Enter a whole number from 0 to 60.')).toBeInTheDocument();
    await user.type(input, '{Backspace}');
    expect(shownText('Enter a whole number from 0 to 60.')).toHaveLength(0);
  });

  it('weekly offs', async () => {
    const user = await renderLoaded();
    for (const day of ['Mon', 'Tue', 'Wed', 'Thu', 'Fri']) {
      await user.click(screen.getByRole('button', {name: day}));
    }
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(screen.getByText('Keep at least one working day in the week.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Fri'}));
    expect(shownText('Keep at least one working day in the week.')).toHaveLength(0);
  });

  it('holiday name', async () => {
    const user = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Add holiday'}));
    expect(screen.getByText('Give each holiday a name.')).toBeInTheDocument();
    await user.type(screen.getByRole('textbox', {name: 'Holiday name'}), 'F');
    expect(shownText('Give each holiday a name.')).toHaveLength(0);
  });
});

describe('stale plan (review 4)', () => {
  it('says next to the Plan button that the inputs changed', async () => {
    const user = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: 'Your plan'});
    expect(screen.queryByText('Your inputs changed since the plan below was made.')).toBeNull();
    await user.click(screen.getByRole('radio', {name: /Big trips/}));
    expect(
      screen.getByText('Your inputs changed since the plan below was made.'),
    ).toBeInTheDocument();
  });
});

describe('calendar days: visible label in the name (browser check, axe label-content-name-mismatch)', () => {
  it("every day button's visible text is part of its spoken name (WCAG 2.5.3)", async () => {
    const user = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: 'Your plan'});
    const days = screen.getAllByRole('button', {name: /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun), \d/});
    expect(days.length).toBeGreaterThan(300);
    const mismatched = days.filter(
      (b) => !(b.getAttribute('aria-label') ?? '').includes((b.textContent ?? '').trim()),
    );
    expect(mismatched.map((b) => `${b.textContent} / ${b.getAttribute('aria-label')}`)).toEqual([]);
  });
});
