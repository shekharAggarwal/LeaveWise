// The canvas's phone screens (Claude Design "LeaveWise UI redesign"): inputs, then results with "Edit inputs", the
// Breaks / Calendar switch and one month at a time. One page, one URL (PRD).
import {render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {App} from './App';
import type {PlanRequest} from './api/types';
import {PHONE_QUERY} from './lib/useIsPhone';
import {expectNoAxeViolations} from './test/axe';
import {stubApi} from './test/fakeApi';

const original = window.matchMedia;

beforeEach(() => {
  window.matchMedia = (query: string): MediaQueryList => ({
    matches: query === PHONE_QUERY,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  });
});

afterEach(() => {
  window.matchMedia = original;
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function renderPhone() {
  const user = userEvent.setup();
  const view = render(<App />);
  await screen.findByRole('button', {name: /^Holidays: 3 of 3 on/});
  return {user, view};
}

describe('phone: inputs screen', () => {
  it('shows the inputs with a holidays row and the plan bar, not the results panel', async () => {
    stubApi();
    const {view} = await renderPhone();
    expect(
      screen.getByRole('heading', {level: 1, name: 'Turn your leaves into the longest breaks'}),
    ).toBeInTheDocument();
    expect(screen.getByText('2027 · 12 leaves · Sat, Sun off · Long weekends')).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: 'Your plan shows here'})).toBeNull();
    // The holiday checkboxes live in the sheet until it opens.
    expect(screen.queryByRole('checkbox', {name: /Fixture Holiday 1/})).toBeNull();
    await expectNoAxeViolations(view.container);
  });

  it('a refused input stays on the inputs screen with its PRD message', async () => {
    const {calls} = stubApi();
    const {user} = await renderPhone();
    await user.clear(screen.getByRole('textbox', {name: 'Leave days'}));
    await user.type(screen.getByRole('textbox', {name: 'Leave days'}), '61');
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(await screen.findAllByText('Enter a whole number from 0 to 60.')).not.toHaveLength(0);
    expect(screen.queryByRole('button', {name: 'Edit inputs'})).toBeNull();
    expect(calls.filter((c) => c.url === '/api/plan')).toHaveLength(0);
  });
});

describe('phone: results screen', () => {
  it('plans, shows the results screen, and Edit inputs goes back with the inputs kept', async () => {
    const {calls} = stubApi();
    const {user, view} = await renderPhone();
    await user.clear(screen.getByRole('textbox', {name: 'Leave days'}));
    await user.type(screen.getByRole('textbox', {name: 'Leave days'}), '2');
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));

    const results = await screen.findByRole('region', {name: 'Your 2027 plan'});
    expect(within(results).getByText('2 leaves → 7 days off')).toBeInTheDocument();
    const figures = within(results).getByRole('list', {name: 'Plan figures'});
    expect(
      within(figures)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual(['Breaks2', 'Days off per leave3.5']);
    expect(within(results).getByRole('list', {name: 'Your breaks'})).toBeInTheDocument();
    expect(within(results).getByRole('button', {name: 'Add to calendar (.ics)'})).toBeEnabled();
    expect((calls.find((c) => c.url === '/api/plan')?.body as PlanRequest).leaveDays).toBe(2);
    await expectNoAxeViolations(view.container);

    await user.click(screen.getByRole('button', {name: 'Edit inputs'}));
    expect(screen.getByRole('textbox', {name: 'Leave days'})).toHaveValue('2');
    await user.click(screen.getByRole('button', {name: 'See your plan'}));
    expect(await screen.findByRole('region', {name: 'Your 2027 plan'})).toBeInTheDocument();
  });

  it('the Calendar switch shows one month at a time with month chips and labelled days', async () => {
    stubApi();
    const {user} = await renderPhone();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: 'Your 2027 plan'});
    await user.click(screen.getByRole('radio', {name: 'Calendar'}));

    // plan2027's first break is in March, so the calendar opens there.
    expect(screen.getByRole('heading', {name: 'March 2027'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /^Fri, 5 Mar: leave day/})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /^Fri, 1 Jan: /})).toBeNull();
    await user.click(screen.getByRole('button', {name: 'Previous month'}));
    expect(screen.getByRole('heading', {name: 'February 2027'})).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /^December: /}));
    expect(screen.getByRole('heading', {name: 'December 2027'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Next month'})).toBeDisabled();
  });
});
