// Regression tests for the Phase 3 UI reviews (code-reviewer and ui-reviewer). Each one failed
// against the first version of the page.
import {act, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {App} from './App';
import type {PlanRequest, PlanResponse} from './api/types';
import {deferred, holidays2027, plan2027, problem, stubApi} from './test/fakeApi';
import {shownText} from './test/shownText';

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

async function renderLoaded() {
  const user = userEvent.setup();
  const view = render(<App />);
  await screen.findByRole('checkbox', {name: /Fixture Holiday 1/});
  return {user, view};
}

const planCalls = <T extends {url: string}>(calls: T[]) =>
  calls.filter((c) => c.url === '/api/plan');

describe('stale state and races (code review 1 to 4)', () => {
  it('a paste that finishes after a year switch does not overwrite the new year', async () => {
    const paste = deferred();
    stubApi({'POST /api/holidays/parse': () => paste.promise});
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Edit list'}));
    await user.click(screen.getByRole('radio', {name: 'Paste a list'}));
    await user.type(
      screen.getByRole('textbox', {name: /One holiday per line/}),
      '2027-11-15 Fixture Company Day',
    );
    await user.click(screen.getByRole('button', {name: 'Load holidays'}));
    await user.click(screen.getByRole('radio', {name: '2026'}));
    await screen.findByText(/No official list for 2026 is bundled/);

    await act(async () => {
      paste.release({
        status: 200,
        json: {holidays: [{line: 1, date: '2027-11-15', name: 'Fixture Company Day'}], errors: []},
      });
    });

    expect(screen.queryByRole('checkbox', {name: /Fixture Company Day/})).toBeNull();
    expect(screen.queryByRole('checkbox', {name: /Fixture Holiday 1/})).toBeNull();
  });

  it('re-planning for another year leaves exactly one calendar day reachable by Tab', async () => {
    stubApi({
      'POST /api/plan': (_url, body) => {
        const year = (body as PlanRequest).year;
        if (year === 2027) return {status: 200, json: plan2027};
        const days = plan2027.days.map((d) => ({
          ...d,
          date: `2026${d.date.slice(4)}`,
          planned: d.date.slice(5) > '10-09',
        }));
        const plan2026: PlanResponse = {...plan2027, year: 2026, planningFrom: '2026-10-10', days};
        return {status: 200, json: plan2026};
      },
    });
    const {user, view} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: /^Your \d{4} plan$/});
    await user.click(screen.getByRole('radio', {name: '2026'}));
    await screen.findByText(/No official list for 2026 is bundled/);
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await waitFor(() =>
      expect(screen.getByRole('heading', {name: '2026 at a glance'})).toBeInTheDocument(),
    );
    const dayButtons = [...view.container.querySelectorAll('button[aria-label]')].filter((b) =>
      /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun), \d{1,2} [A-Z][a-z]{2}/.test(
        b.getAttribute('aria-label') ?? '',
      ),
    );
    expect(dayButtons.length).toBeGreaterThan(300);
    expect(dayButtons.filter((b) => b.getAttribute('tabindex') === '0')).toHaveLength(1);
  });

  it('pressing Enter twice sends one plan request', async () => {
    const reply = deferred();
    const {calls} = stubApi({'POST /api/plan': () => reply.promise});
    const {user} = await renderLoaded();
    const input = screen.getByRole('textbox', {name: 'Leave days'});
    await user.click(input);
    await user.keyboard('{Enter}{Enter}');
    expect(planCalls(calls)).toHaveLength(1);
    await act(async () => reply.release({status: 200, json: plan2027}));
  });

  it('planning waits for the holiday list after a year switch', async () => {
    const list = deferred();
    const {calls} = stubApi({
      'GET /api/holidays': (url) =>
        url.includes('2026') ? list.promise : {status: 200, json: holidays2027},
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('radio', {name: '2026'}));
    expect(screen.getByRole('button', {name: 'Plan my leaves'})).toBeDisabled();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(planCalls(calls)).toHaveLength(0);
    await act(async () =>
      list.release({
        status: 200,
        json: {...holidays2027, year: 2026, available: false, source: null, holidays: []},
      }),
    );
    expect(screen.getByRole('button', {name: 'Plan my leaves'})).toBeEnabled();
  });

  it('a failed holiday list shows a retry next to the holidays and blocks planning', async () => {
    let fail = true;
    stubApi({
      'GET /api/holidays': () =>
        fail
          ? {status: 500, json: problem(500, 'Something went wrong on our side. Try again.')}
          : {status: 200, json: holidays2027},
    });
    const user = userEvent.setup();
    render(<App />);
    const retry = await screen.findByRole('button', {name: 'Load the holiday list again'});
    expect(screen.getByRole('button', {name: 'Plan my leaves'})).toBeDisabled();
    fail = false;
    await user.click(retry);
    expect(await screen.findByRole('checkbox', {name: /Fixture Holiday 1/})).toBeChecked();
    expect(screen.getByRole('button', {name: 'Plan my leaves'})).toBeEnabled();
  });

  it('results are marked out of date when an input changes', async () => {
    stubApi();
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    const results = await screen.findByRole('region', {name: /^Your \d{4} plan$/});
    expect(within(results).queryByText(/Inputs changed/)).toBeNull();
    await user.click(screen.getByRole('radio', {name: /Big trips/}));
    expect(
      within(results).getByText('Inputs changed. Tap Plan my leaves to update this plan.'),
    ).toBeInTheDocument();
  });
});

describe('focus and keyboard (reviews: focus after planning, roving tabindex)', () => {
  it('moves focus to the results region after planning', async () => {
    stubApi();
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    const results = await screen.findByRole('region', {name: /^Your \d{4} plan$/});
    await waitFor(() => expect(results).toHaveFocus());
  });

  it('arrow keys move between days', async () => {
    stubApi();
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: /^Your \d{4} plan$/});
    const first = screen.getByRole('button', {name: /^Fri, 1 Jan: /});
    first.focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', {name: /^Sat, 2 Jan: /})).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('button', {name: /^Sat, 9 Jan: /})).toHaveFocus();
  });
});

describe('errors say what to do next (reviews: raw error text)', () => {
  it('a network failure shows a connection message, not a TypeError', async () => {
    stubApi({
      'POST /api/plan': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(
      await screen.findByText("Couldn't reach LeaveWise. Check your connection and try again."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/TypeError/)).toBeNull();
  });

  it('an error body without an errors list still shows its detail', async () => {
    stubApi({
      'POST /api/plan': () => ({status: 502, json: {detail: 'Bad gateway from the host.'}}),
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(await screen.findByText('Bad gateway from the host.')).toBeInTheDocument();
  });

  it('a field error from the server is shown once, on the field', async () => {
    stubApi({
      'POST /api/plan': () => ({
        status: 400,
        json: problem(400, 'Enter a whole number from 0 to 60.'),
      }),
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await waitFor(() => expect(shownText('Enter a whole number from 0 to 60.')).toHaveLength(1));
  });

  it('shows the no-break note with its suggestion', async () => {
    stubApi({
      'POST /api/plan': () => ({
        status: 200,
        json: {
          ...plan2027,
          summary: {
            ...plan2027.summary,
            leavesUsed: 0,
            totalDaysOff: 0,
            breakCount: 0,
            leavesUnused: 2,
          },
          breaks: [],
          notes: [
            {
              code: 'NO_BREAK_POSSIBLE',
              message:
                'No break of 9+ days fits 2 leaves with these holidays and weekly offs. Try a shorter break style.',
            },
          ],
        },
      }),
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(await screen.findByText(/Try a shorter break style\./)).toBeInTheDocument();
  });
});

describe('adding one holiday', () => {
  it('needs a name, merges a second name into an existing date and ticks it', async () => {
    const {calls} = stubApi();
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Edit list'}));
    await user.click(screen.getByRole('button', {name: 'Add holiday'}));
    expect(screen.getByText('Give each holiday a name.')).toBeInTheDocument();
    expect(screen.getByText('Pick a date in 2027.')).toBeInTheDocument();

    // Untick, then add a second name on the same date: one row, both names, ticked.
    await user.click(screen.getByRole('checkbox', {name: /Fixture Holiday 2/}));
    await user.type(screen.getByRole('textbox', {name: 'Holiday name'}), 'Fixture Company Day');
    const dateInput = screen.getByLabelText('Holiday date');
    await user.click(dateInput);
    await user.keyboard('2027-03-04');
    await user.click(screen.getByRole('button', {name: 'Add holiday'}));
    const row = await screen.findByRole('checkbox', {
      name: /Fixture Holiday 2 \/ Fixture Company Day/,
    });
    expect(row).toBeChecked();

    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: /^Your \d{4} plan$/});
    const sent = (planCalls(calls)[0]?.body as PlanRequest).holidays;
    expect(sent).toContainEqual({date: '2027-03-04', name: 'Fixture Holiday 2'});
    expect(sent).toContainEqual({date: '2027-03-04', name: 'Fixture Company Day'});
  });
});

describe('.ics download (code review 6)', () => {
  it('names the file and releases the object URL only after the click', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    stubApi();
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:fixture');
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clicked: string[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clicked.push(this.download);
    });
    const user = userEvent.setup({advanceTimers: vi.advanceTimersByTime});
    render(<App />);
    await screen.findByRole('checkbox', {name: /Fixture Holiday 1/});
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await user.click(await screen.findByRole('button', {name: 'Add to calendar (.ics)'}));
    await waitFor(() => expect(clicked).toEqual(['leavewise-2027.ics']));
    expect(create).toHaveBeenCalledTimes(1);
    expect(revoke).not.toHaveBeenCalled();
    await act(async () => {
      vi.advanceTimersByTime(60_000);
    });
    expect(revoke).toHaveBeenCalledWith('blob:fixture');
    vi.useRealTimers();
  });
});
