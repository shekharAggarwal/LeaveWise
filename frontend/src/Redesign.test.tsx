// Behaviour added by the 2026-10-10 redesign (Claude Design canvas "LeaveWise UI redesign"): the leave-day
// stepper, the summary figures, the empty results panel and the shorter-break-style suggestion.
import {act, render, screen, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {App} from './App';
import type {PlanRequest} from './api/types';
import {expectNoAxeViolations} from './test/axe';
import {deferred, plan2027, stubApi} from './test/fakeApi';

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
const shown = (text: string) =>
  screen.queryAllByText(text).filter((el) => !el.closest('[data-astryx-live-region]'));

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
    await screen.findByText('Enter a whole number from 0 to 60.');
    expect(shown('Enter a whole number from 0 to 60.')).toHaveLength(1);
    await user.click(screen.getByRole('button', {name: 'One more leave day'}));
    expect(leaveBox()).toHaveValue('1');
    expect(shown('Enter a whole number from 0 to 60.')).toHaveLength(0);

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
    const region = await screen.findByRole('region', {name: 'Your plan'});
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
