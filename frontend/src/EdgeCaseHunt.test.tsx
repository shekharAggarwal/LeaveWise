// edge-case-hunter, Phase 3 whole-app attack: PRD edge cases with no UI test before.
// Holiday names are fixtures, not real holidays.
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {Theme} from '@astryxdesign/core/theme';
import {leavewiseTheme} from './theme/leavewise';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {App} from './App';
import type {CalendarDay, PlanRequest, PlanResponse} from './api/types';
import {Results} from './features/results/Results';
import {stubApi} from './test/fakeApi';

afterEach(() => {
  vi.unstubAllGlobals();
});

function yearDays(year: number, mark: (date: string) => Partial<CalendarDay>): CalendarDay[] {
  const days: CalendarDay[] = [];
  for (let d = new Date(Date.UTC(year, 0, 1)); d.getUTCFullYear() === year;) {
    const iso = d.toISOString().slice(0, 10);
    const weekday = d.getUTCDay();
    days.push({
      date: iso,
      kind: weekday === 0 || weekday === 6 ? 'WEEKLY_OFF' : 'WORKING',
      planned: true,
      holidayNames: [],
      breakIndex: null,
      freeLongWeekendIndex: null,
      ...mark(iso),
    });
    d = new Date(d.getTime() + 86_400_000);
  }
  return days;
}

const request = (year: number, leaveDays: number): PlanRequest => ({
  year,
  leaveDays,
  weeklyOffs: ['SATURDAY', 'SUNDAY'],
  breakStyle: 'LONG_WEEKENDS',
  holidays: [],
});

function show(plan: PlanResponse, leaveDays: number) {
  render(
    <Theme theme={leavewiseTheme}>
      <Results plan={plan} request={request(plan.year, leaveDays)} isStale={false} />
    </Theme>,
  );
}

describe('PRD edge cases in the results (no UI test before)', () => {
  it('more leave days than working days: summary shows the unused leaves and the note', () => {
    // A 2027 window of three days (Wed 29 to Fri 31 Dec) with every working day a leave.
    const plan: PlanResponse = {
      year: 2027,
      planningFrom: '2027-12-29',
      planningTo: '2027-12-31',
      breakStyle: 'LONG_WEEKENDS',
      minBreakDays: 3,
      summary: {leavesAvailable: 5, leavesUsed: 3, leavesUnused: 2, totalDaysOff: 5, breakCount: 1},
      breaks: [
        {
          start: '2027-12-29',
          end: '2028-01-02',
          days: 5,
          leaveDates: ['2027-12-29', '2027-12-30', '2027-12-31'],
          holidays: [],
          daysOffPerLeave: 1.7,
        },
      ],
      freeLongWeekends: [],
      notes: [
        {
          code: 'LEAVES_LEFT_OVER',
          message: '2 leaves left over: every working day is already a leave day.',
        },
      ],
      days: yearDays(2027, (date) =>
        date < '2027-12-29' ? {planned: false} : {kind: 'LEAVE', breakIndex: 0},
      ),
    };
    show(plan, 5);

    expect(screen.getByText('3 leaves → 5 days off')).toBeInTheDocument();
    expect(screen.getByText('across 1 break. 2 leaves unused.')).toBeInTheDocument();
    expect(
      screen.getByText('2 leaves left over: every working day is already a leave day.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Wed, 29 Dec – Sun, 2 Jan 2028')).toBeInTheDocument();
    expect(screen.getByText(/^5 days off · leave /)).toBeInTheDocument();
  });

  it('current year on 31 December: the no-days-left note, no download, every day already passed', () => {
    const plan: PlanResponse = {
      year: 2026,
      planningFrom: null,
      planningTo: null,
      breakStyle: 'LONG_WEEKENDS',
      minBreakDays: 3,
      summary: {leavesAvailable: 5, leavesUsed: 0, leavesUnused: 5, totalDaysOff: 0, breakCount: 0},
      breaks: [],
      freeLongWeekends: [],
      notes: [{code: 'NO_DAYS_LEFT', message: 'No days left to plan in 2026. Pick 2027.'}],
      days: yearDays(2026, () => ({planned: false})),
    };
    show(plan, 5);

    expect(screen.getByText('No days left to plan in 2026. Pick 2027.')).toBeInTheDocument();
    expect(screen.queryByRole('button', {name: /Add to calendar/})).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Thu, 31 Dec: working day, already passed'}),
    ).toBeInTheDocument();
    // Nothing is planned, so the one Tab stop falls back to 1 January.
    expect(
      screen.getByRole('button', {name: 'Thu, 1 Jan: working day, already passed'}),
    ).toHaveAttribute('tabindex', '0');
  });

  it('leap year 2028: the calendar has 29 February and the keyboard stays inside the year', async () => {
    const user = userEvent.setup();
    const plan: PlanResponse = {
      year: 2028,
      planningFrom: '2028-01-01',
      planningTo: '2028-12-31',
      breakStyle: 'LONG_WEEKENDS',
      minBreakDays: 3,
      summary: {leavesAvailable: 0, leavesUsed: 0, leavesUnused: 0, totalDaysOff: 0, breakCount: 0},
      breaks: [],
      freeLongWeekends: [],
      notes: [],
      days: yearDays(2028, (date) =>
        date === '2028-02-29' ? {kind: 'HOLIDAY', holidayNames: ['Fixture Leap Day']} : {},
      ),
    };
    show(plan, 0);

    expect(
      screen.getAllByRole('button', {name: /^[A-Z][a-z]{2}, \d{1,2} [A-Z][a-z]{2}: /}),
    ).toHaveLength(366);
    const leap = screen.getByRole('button', {name: 'Tue, 29 Feb: holiday, Fixture Leap Day'});
    leap.focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', {name: /^Wed, 1 Mar: /})).toHaveFocus();

    const first = screen.getByRole('button', {name: /^Sat, 1 Jan: /});
    first.focus();
    await user.keyboard('{ArrowLeft}');
    expect(first).toHaveFocus();
    await user.keyboard('{End}');
    const last = screen.getByRole('button', {name: /^Sun, 31 Dec: /});
    expect(last).toHaveFocus();
    await user.keyboard('{ArrowRight}');
    expect(last).toHaveFocus();
    await user.keyboard('{Home}');
    expect(first).toHaveFocus();
  });
});

describe('adding one holiday in the browser', () => {
  it('regression: a pasted name with a tab is refused on the field, as the server would', async () => {
    // PRD: "Every input is checked on the server as well as in the browser". docs/API.md refuses
    // a holiday name with a control character ("Holiday names can't contain line breaks or
    // control characters."). The browser adds the name, and every later plan fails with a
    // page-level error that doesn't say which holiday is wrong.
    stubApi();
    const user = userEvent.setup();
    render(<App />);
    await screen.findByRole('checkbox', {name: /Fixture Holiday 1/});

    await user.click(screen.getByRole('button', {name: 'Edit list'}));
    await user.click(screen.getByRole('textbox', {name: 'Holiday name'}));
    await user.paste('Fixture\tCompany Day');
    await user.click(screen.getByLabelText('Holiday date'));
    await user.keyboard('2027-11-15');
    await user.click(screen.getByRole('button', {name: 'Add holiday'}));

    expect(
      screen.getByText("Holiday names can't contain line breaks or control characters."),
    ).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', {name: /Company Day/})).not.toBeInTheDocument();
  });
});
