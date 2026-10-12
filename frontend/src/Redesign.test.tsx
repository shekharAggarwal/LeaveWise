// Behaviour added by the 2026-10-10 redesign (Claude Design canvas "LeaveWise UI redesign"): the leave-day
// stepper, the summary figures, the empty results panel and the shorter-break-style suggestion.
import {act, render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {App} from './App';
import type {PlanRequest} from './api/types';
import {expectNoAxeViolations} from './test/axe';
import {deferred, holidays2027, noHolidays2026, plan2027, stubApi} from './test/fakeApi';
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

const leaveBox = () => screen.getByRole('textbox', {name: 'Leave days'});

/** Visible copies of a message; Astryx also keeps the last one in its screen-reader announcer. */
describe('leave-day stepper', () => {
  it('steps the leave days by one and keeps the field a text box', async () => {
    stubApi();
    const {user} = await renderLoaded();
    expect(leaveBox()).toHaveValue('12');
    await user.click(screen.getByRole('button', {name: 'One more leave day'}));
    expect(leaveBox()).toHaveValue('13');
    await user.click(screen.getByRole('button', {name: 'One fewer leave day'}));
    await user.click(screen.getByRole('button', {name: 'One fewer leave day'}));
    expect(leaveBox()).toHaveValue('11');
  });

  it('stops at 0 and 60', async () => {
    stubApi();
    const {user} = await renderLoaded();
    await user.clear(leaveBox());
    await user.type(leaveBox(), '60');
    expect(screen.getByRole('button', {name: 'One more leave day'})).toBeDisabled();
    await user.clear(leaveBox());
    await user.type(leaveBox(), '0');
    expect(screen.getByRole('button', {name: 'One fewer leave day'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'One more leave day'})).toBeEnabled();
  });

  it('starts from 0 when the text is not a whole number from 0 to 60, and clears the error', async () => {
    stubApi();
    const {user} = await renderLoaded();
    await user.clear(leaveBox());
    await user.type(leaveBox(), 'abc');
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await waitFor(() => expect(shownText('Enter a whole number from 0 to 60.')).toHaveLength(1));
    await user.click(screen.getByRole('button', {name: 'One more leave day'}));
    expect(leaveBox()).toHaveValue('1');
    expect(shownText('Enter a whole number from 0 to 60.')).toHaveLength(0);

    await user.clear(leaveBox());
    await user.type(leaveBox(), '75');
    await user.click(screen.getByRole('button', {name: 'One fewer leave day'}));
    expect(leaveBox()).toHaveValue('60');
  });
});

describe('results panel', () => {
  it('shows what the panel will hold before the first plan', async () => {
    stubApi();
    const {view} = await renderLoaded();
    expect(screen.getByRole('heading', {name: 'Your plan shows here'})).toBeInTheDocument();
    await expectNoAxeViolations(view.container);
  });

  it('shows the summary figures, including days off per leave', async () => {
    stubApi();
    const {user, view} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    const region = await screen.findByRole('region', {name: /^Your \d{4} plan$/});
    expect(screen.queryByRole('heading', {name: 'Your plan shows here'})).not.toBeInTheDocument();
    const figures = within(region).getByRole('list', {name: 'Plan figures'});
    const items = within(figures)
      .getAllByRole('listitem')
      .map((li) => li.textContent);
    // plan2027: 2 leaves used of 2, 7 days off, 2 breaks.
    expect(items).toEqual([
      'Leaves used2 of 2',
      'Days off in breaks7',
      'Breaks2',
      'Days off per leave3.5',
    ]);
    await expectNoAxeViolations(view.container);
  });
});

describe('no break possible', () => {
  const noBreak = (body: unknown) => {
    const style = (body as PlanRequest).breakStyle;
    if (style === 'LONG_WEEKENDS') return {status: 200, json: plan2027};
    return {
      status: 200,
      json: {
        ...plan2027,
        breakStyle: style,
        minBreakDays: style === 'BIG_TRIPS' ? 9 : 5,
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
            message: `No break of ${style === 'BIG_TRIPS' ? 9 : 5}+ days fits 2 leaves with these holidays and weekly offs. Try a shorter break style.`,
          },
        ],
        days: plan2027.days.map((d) => ({
          ...d,
          breakIndex: null,
          kind: d.kind === 'LEAVE' ? 'WORKING' : d.kind,
        })),
      },
    };
  };

  it('offers each shorter break style, and one tap plans with it', async () => {
    const {calls} = stubApi({'POST /api/plan': (_url, body) => noBreak(body)});
    const {user, view} = await renderLoaded();
    await user.click(screen.getByRole('radio', {name: /Big trips/}));
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByText(/Try a shorter break style\./);
    expect(screen.getByRole('button', {name: 'Try Short trips'})).toBeInTheDocument();
    await expectNoAxeViolations(view.container);

    await user.click(screen.getByRole('button', {name: 'Try Long weekends'}));
    expect(await screen.findByText(/2 leaves → 7 days off/)).toBeInTheDocument();
    const plans = calls.filter((c) => c.url === '/api/plan');
    expect((plans.at(-1)?.body as PlanRequest).breakStyle).toBe('LONG_WEEKENDS');
    expect(screen.getByRole('radio', {name: /Long weekends/})).toBeChecked();
    expect(screen.queryByText(/Inputs changed/)).not.toBeInTheDocument();
    expect(
      screen.queryByText('Your inputs changed since the plan below was made.'),
    ).not.toBeInTheDocument();
  });

  it('waits while a plan is on its way, and an invalid input keeps the break style', async () => {
    const pending = deferred();
    let calls = 0;
    stubApi({
      'POST /api/plan': (_url, body) => (++calls === 2 ? pending.promise : noBreak(body)),
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('radio', {name: /Big trips/}));
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByText(/Try a shorter break style\./);

    // Plan again with the same inputs: while it is in flight the suggestion can't start a second plan.
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    expect(screen.getByRole('button', {name: 'Try Long weekends'})).toBeDisabled();
    await act(async () => {
      pending.release(noBreak({breakStyle: 'BIG_TRIPS'}));
    });
    expect(screen.getByRole('button', {name: 'Try Long weekends'})).toBeEnabled();

    await user.clear(leaveBox());
    await user.type(leaveBox(), 'abc');
    await user.click(screen.getByRole('button', {name: 'Try Long weekends'}));
    expect(await screen.findAllByText('Enter a whole number from 0 to 60.')).not.toHaveLength(0);
    expect(screen.getByRole('radio', {name: /Big trips/})).toBeChecked();
    expect(calls).toBe(2);
  });

  it('offers only styles shorter than the one planned', async () => {
    stubApi({'POST /api/plan': (_url, body) => noBreak(body)});
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('radio', {name: /Short trips/}));
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByText(/Try a shorter break style\./);
    expect(screen.getByRole('button', {name: 'Try Long weekends'})).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Try Short trips'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Try Big trips'})).not.toBeInTheDocument();
  });
});

describe('holiday adder and the list loading (code review)', () => {
  it('a year switch closes the adder, and it is not offered while the list loads', async () => {
    const list = deferred();
    stubApi({
      'GET /api/holidays': (url) =>
        url.includes('2026') ? list.promise : {status: 200, json: holidays2027},
    });
    const {user} = await renderLoaded();
    await user.click(screen.getByRole('button', {name: 'Edit list'}));
    expect(screen.getByRole('button', {name: 'Add holiday'})).toBeInTheDocument();
    await user.click(screen.getByRole('radio', {name: '2026'}));
    expect(screen.queryByRole('button', {name: 'Add holiday'})).toBeNull();
    expect(screen.queryByRole('button', {name: 'Add one'})).toBeNull();
    await act(async () => list.release({status: 200, json: noHolidays2026}));
    expect(await screen.findByRole('button', {name: 'Add one'})).toBeInTheDocument();
  });
});

describe('floater holidays in a company list (ADR 0007 amendment)', () => {
  const companyList = {
    ...holidays2027,
    kind: 'company' as const,
    list: 'Fixture Company holidays 2027',
    holidays: [
      {date: '2027-01-26', names: ['Fixture Holiday 1'], optional: false},
      {date: '2027-03-04', names: ['Fixture Floater 1'], optional: true},
    ],
  };

  it('lists a floater unticked with its tag, and plans without it until ticked', async () => {
    const {calls} = stubApi({'GET /api/holidays': () => ({status: 200, json: companyList})});
    const user = userEvent.setup();
    render(<App />);
    const floater = await screen.findByRole('checkbox', {name: /Fixture Floater 1/});
    expect(floater).not.toBeChecked();
    expect(screen.getByRole('checkbox', {name: /Fixture Holiday 1/})).toBeChecked();
    expect(screen.getByText('Floater')).toBeInTheDocument();
    expect(screen.getByText(/1 of 2 on/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await screen.findByRole('region', {name: /^Your \d{4} plan$/});
    const first = calls.filter((c) => c.url === '/api/plan')[0]?.body as PlanRequest;
    expect(first.holidays.map((h) => h.name)).toEqual(['Fixture Holiday 1']);

    await user.click(floater);
    await user.click(screen.getByRole('button', {name: 'Plan my leaves'}));
    await waitFor(() => expect(calls.filter((c) => c.url === '/api/plan')).toHaveLength(2));
    const second = calls.filter((c) => c.url === '/api/plan')[1]?.body as PlanRequest;
    expect(second.holidays.map((h) => h.name)).toEqual(['Fixture Holiday 1', 'Fixture Floater 1']);
  });
});
