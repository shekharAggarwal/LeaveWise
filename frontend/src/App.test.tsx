import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {App} from './App';
import type {PlanRequest} from './api/types';
import {expectNoAxeViolations} from './test/axe';
import {problem, stubApi} from './test/fakeApi';

afterEach(() => {
  vi.unstubAllGlobals();
});

async function renderLoaded() {
  const user = userEvent.setup();
  const view = render(<App />);
  await screen.findByRole('checkbox', {name: /Fixture Holiday 1/});
  return {user, view};
}

describe('LeaveWise journey (PRD, User journey)', () => {
  it('opens with the PRD defaults and no login', async () => {
    stubApi();
    await renderLoaded();

    expect(screen.getByRole('heading', {level: 1, name: 'LeaveWise'})).toBeInTheDocument();
    expect(screen.getByRole('textbox', {name: 'Leave days'})).toHaveValue('12');
    expect(screen.getByRole('radio', {name: '2027'})).toBeChecked();
    expect(screen.getByRole('button', {name: 'Sat'})).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', {name: 'Sun'})).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', {name: 'Mon'})).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('radio', {name: /Long weekends/})).toBeChecked();
    expect(screen.getByRole('checkbox', {name: /Fixture Holiday 1/})).toBeChecked();
    expect(screen.getByRole('button', {name: 'Plan my leaves'})).toBeInTheDocument();
  });

  it('shows the holiday source and the reminder to check the company list', async () => {
    stubApi();
    await renderLoaded();
    expect(screen.getByText(/official holiday list/)).toBeInTheDocument();
    expect(screen.getByRole('link', {name: /Fixture memorandum/})).toHaveAttribute(
      'href',
      'https://fixture.gov.in/list.pdf',
    );
  });

  it('plans with the ticked holidays, then shows summary, breaks, calendar and download', async () => {
    const {calls} = stubApi();
    const {user} = await renderLoaded();

    await user.clear(screen.getByRole('textbox', {name: 'Leave days'}));
    await user.type(screen.getByRole('textbox', {name: 'Leave days'}), '2');
    await user.click(screen.getByRole('checkbox', {name: /Fixture Holiday 1/}));
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));

    const results = await screen.findByRole('region', {name: 'Your plan'});
    const request = calls.find((c) => c.url === '/api/plan')?.body as PlanRequest;
    expect(request.leaveDays).toBe(2);
    expect(request.year).toBe(2027);
    expect(request.weeklyOffs).toEqual(['SATURDAY', 'SUNDAY']);
    expect(request.holidays.map((h) => h.name)).toEqual([
      'Fixture Holiday 2',
      'Fixture Holiday 3',
      'Fixture Holiday 4',
    ]);

    // Summary first: numbers before words (PRD, Results).
    expect(within(results).getByText(/2 leaves → 7 days off/)).toBeInTheDocument();
    expect(within(results).getByText(/across 2 breaks/)).toBeInTheDocument();

    // Break list: dates, days off, leave dates, holidays inside, days off per leave.
    const breaks = within(results).getByRole('list', {name: 'Breaks'});
    const first = within(breaks).getAllByRole('listitem')[0]!;
    expect(first).toHaveTextContent('Thu, 4 Mar – Sun, 7 Mar');
    expect(first).toHaveTextContent('4 days off');
    expect(first).toHaveTextContent('Leave: Fri, 5 Mar');
    expect(first).toHaveTextContent('Fixture Holiday 2');
    expect(first).toHaveTextContent('4.0 days off per leave');

    // Year calendar: all 12 months, each day labelled in words, not colour alone.
    for (const month of ['January', 'June', 'December']) {
      expect(within(results).getByRole('heading', {name: month})).toBeInTheDocument();
    }
    const leaveDay = within(results).getByRole('button', {name: /^Fri, 5 Mar: leave day/});
    await user.click(leaveDay);
    expect(within(results).getByRole('status')).toHaveTextContent(
      'Fri, 5 Mar: leave day, part of a 4-day break (Thu, 4 Mar – Sun, 7 Mar)',
    );
    expect(within(results).getAllByText('Leave day').length).toBeGreaterThan(0); // legend

    // Calendar file.
    expect(
      within(results).getByRole('button', {name: 'Download calendar file (.ics)'}),
    ).toBeEnabled();
  });

  it('downloads the .ics for the same inputs', async () => {
    const {calls} = stubApi();
    const {user} = await renderLoaded();
    const createObjectURL = vi.fn(() => 'blob:fixture');
    vi.stubGlobal('URL', Object.assign(URL, {createObjectURL, revokeObjectURL: vi.fn()}));

    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await user.click(await screen.findByRole('button', {name: 'Download calendar file (.ics)'}));

    await waitFor(() => expect(calls.some((c) => c.url === '/api/plan/ics')).toBe(true));
    const plan = calls.find((c) => c.url === '/api/plan')?.body;
    const ics = calls.find((c) => c.url === '/api/plan/ics')?.body;
    expect(ics).toEqual(plan);
    expect(createObjectURL).toHaveBeenCalled();
  });

  it('a changed input and a new tap gives a fresh plan', async () => {
    const {calls} = stubApi();
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: 'Your plan'});
    await user.click(screen.getByRole('radio', {name: /Short trips/}));
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await waitFor(() => expect(calls.filter((c) => c.url === '/api/plan')).toHaveLength(2));
    expect((calls.filter((c) => c.url === '/api/plan')[1]?.body as PlanRequest).breakStyle).toBe(
      'SHORT_TRIPS',
    );
  });
});

describe('Validation and errors (PRD table)', () => {
  it('leave days outside 0 to 60 or not whole: PRD message, no request sent', async () => {
    const {calls} = stubApi();
    const {user} = await renderLoaded();
    const input = screen.getByRole('textbox', {name: 'Leave days'});
    for (const bad of ['61', '-1', '12.5', 'abc']) {
      await user.clear(input);
      await user.type(input, bad);
      await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
      expect(await screen.findAllByText('Enter a whole number from 0 to 60.')).not.toHaveLength(0);
    }
    expect(calls.some((c) => c.url === '/api/plan')).toBe(false);
  });

  it('all seven weekly offs: PRD message, no request sent', async () => {
    const {calls} = stubApi();
    const {user} = await renderLoaded();
    for (const day of ['Mon', 'Tue', 'Wed', 'Thu', 'Fri']) {
      await user.click(screen.getByRole('button', {name: day}));
    }
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(
      await screen.findAllByText('Keep at least one working day in the week.'),
    ).not.toHaveLength(0);
    expect(calls.some((c) => c.url === '/api/plan')).toBe(false);
  });

  it('a server error shows its message and keeps the inputs', async () => {
    stubApi({
      'POST /api/plan': () => ({status: 400, json: problem(400, 'Pick 2026 or 2027.', 'year')}),
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(await screen.findByText('Pick 2026 or 2027.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', {name: 'Leave days'})).toHaveValue('12');
  });

  it('0 leave days: free long weekends only, with the no-leave note', async () => {
    stubApi();
    const {user} = await renderLoaded();
    await user.clear(screen.getByRole('textbox', {name: 'Leave days'}));
    await user.type(screen.getByRole('textbox', {name: 'Leave days'}), '0');
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    const results = await screen.findByRole('region', {name: 'Your plan'});
    expect(
      within(results).getByText('No leave used. These are the long weekends you already get.'),
    ).toBeInTheDocument();
    const weekends = within(results).getByRole('list', {name: 'Free long weekends'});
    expect(weekends).toHaveTextContent('Fri, 27 Aug – Sun, 29 Aug');
  });

  it('pasted lines load and bad lines show their line number and reason', async () => {
    const {calls} = stubApi();
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: /Paste a list/}));
    await user.type(
      screen.getByRole('textbox', {name: 'Paste holidays'}),
      '2027-11-15 Fixture Company Day',
    );
    await user.click(screen.getByRole('button', {name: 'Add pasted lines'}));
    expect(await screen.findByRole('checkbox', {name: /Fixture Company Day/})).toBeChecked();
    expect(screen.getByText(/Line 2: 2027-02-30 is not a real date\./)).toBeInTheDocument();
    expect(calls.find((c) => c.url === '/api/holidays/parse')?.body).toEqual({
      year: 2027,
      text: '2027-11-15 Fixture Company Day',
    });
  });

  it('a year without a bundled list says so and still lets you add holidays', async () => {
    stubApi();
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('radio', {name: '2026'}));
    expect(await screen.findByText(/No official list for 2026 is bundled/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Add holiday'})).toBeInTheDocument();
  });

  it('options failing to load shows an error with a retry', async () => {
    stubApi({
      'GET /api/options': () => ({
        status: 500,
        json: problem(500, 'Something went wrong on our side. Try again.'),
      }),
    });
    render(<App />);
    expect(await screen.findByText(/Couldn't load the planner/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Try again'})).toBeInTheDocument();
  });
});

describe('Accessibility (axe)', () => {
  it('has no violations on the form', async () => {
    stubApi();
    const {view} = await renderLoaded();
    await expectNoAxeViolations(view.container);
  });

  it('has no violations with results shown', async () => {
    stubApi();
    const {user, view} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: 'Your plan'});
    await expectNoAxeViolations(view.container);
  });
});
