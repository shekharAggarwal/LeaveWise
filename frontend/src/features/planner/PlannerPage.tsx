// Scaffolded from the Astryx "contact-form" page template (npm run astryx -- template contact-form);
// its frame (centred Section, stacked groups with dividers, one full-width primary action) is kept.

import {useCallback, useEffect, useMemo, useRef, useState, type ReactNode} from 'react';
import * as stylex from '@stylexjs/stylex';
import {Banner} from '@astryxdesign/core/Banner';
import {Button} from '@astryxdesign/core/Button';
import {Center} from '@astryxdesign/core/Center';
import {Divider} from '@astryxdesign/core/Divider';
import {VStack} from '@astryxdesign/core/Layout';
import {RadioList, RadioListItem} from '@astryxdesign/core/RadioList';
import {Section} from '@astryxdesign/core/Section';
import {SegmentedControl, SegmentedControlItem} from '@astryxdesign/core/SegmentedControl';
import {Skeleton} from '@astryxdesign/core/Skeleton';
import {Heading, Text} from '@astryxdesign/core/Text';
import {TextInput} from '@astryxdesign/core/TextInput';
import {ToggleButton, ToggleButtonGroup} from '@astryxdesign/core/ToggleButton';
import {ApiError, getHolidays, getOptions, messageOf, planLeaves} from '../../api/client';
import type {
  BreakStyleId,
  IsoDate,
  Options,
  PlanRequest,
  PlanResponse,
  Weekday,
} from '../../api/types';
import {parseLeaveDays} from '../../lib/validation';
import {Results} from '../results/Results';
import {HolidayEditor, type HolidayLoad} from './HolidayEditor';
import {addHoliday, type HolidayRow} from './holidayRows';

// Seven day buttons don't fit a 360 px phone in one row; no ToggleButtonGroup prop wraps them.
const styles = stylex.create({wrapRow: {flexWrap: 'wrap'}});

// PRD messages, word for word.
const LEAVE_DAYS_MESSAGE = 'Enter a whole number from 0 to 60.';
const WORKING_DAY_MESSAGE = 'Keep at least one working day in the week.';

const WEEKDAYS: {id: Weekday; label: string}[] = [
  {id: 'MONDAY', label: 'Mon'},
  {id: 'TUESDAY', label: 'Tue'},
  {id: 'WEDNESDAY', label: 'Wed'},
  {id: 'THURSDAY', label: 'Thu'},
  {id: 'FRIDAY', label: 'Fri'},
  {id: 'SATURDAY', label: 'Sat'},
  {id: 'SUNDAY', label: 'Sun'},
];

const BREAK_STYLE_HINTS: Record<BreakStyleId, string> = {
  LONG_WEEKENDS: 'Breaks of 3 or more days off',
  SHORT_TRIPS: 'Breaks of 5 or more days off',
  BIG_TRIPS: 'Breaks of 9 or more days off',
};

type Load<T> = {state: 'loading'} | {state: 'error'; message: string} | {state: 'ready'; value: T};

interface Result {
  seq: number;
  plan: PlanResponse;
  request: PlanRequest;
  inputKey: string;
}

export function PlannerPage() {
  const [options, setOptions] = useState<Load<Options>>({state: 'loading'});
  const [year, setYear] = useState<number | null>(null);
  const [leaveText, setLeaveText] = useState('');
  const [weeklyOffs, setWeeklyOffs] = useState<Weekday[]>([]);
  const [breakStyle, setBreakStyle] = useState<BreakStyleId>('LONG_WEEKENDS');
  const [holidays, setHolidays] = useState<HolidayLoad>({state: 'loading'});
  const [holidayReload, setHolidayReload] = useState(0);
  const [rows, setRows] = useState<HolidayRow[]>([]);
  const [ticked, setTicked] = useState<Set<IsoDate>>(new Set());
  const [errors, setErrors] = useState<{leaveDays?: string; weeklyOffs?: string; form?: string}>(
    {},
  );
  const [isPlanning, setIsPlanning] = useState(false);
  const [result, setResult] = useState<Result | null>(null);
  const planning = useRef(false); // guards a second Enter before React re-renders
  const planSeq = useRef(0); // drops responses that arrive out of order

  const applyOptions = useCallback((value: Options) => {
    setOptions({state: 'ready', value});
    setYear(value.defaultYear);
    setLeaveText(String(value.leaveDays.default));
    setWeeklyOffs(value.defaultWeeklyOffs);
    setBreakStyle(value.defaultBreakStyle);
  }, []);

  const failOptions = useCallback(() => {
    setOptions({
      state: 'error',
      message: "Couldn't load the planner. Check your connection and try again.",
    });
  }, []);

  useEffect(() => {
    let current = true;
    getOptions().then(
      (value) => current && applyOptions(value),
      () => current && failOptions(),
    );
    return () => {
      current = false;
    };
  }, [applyOptions, failOptions]);

  const retryOptions = () => {
    setOptions({state: 'loading'});
    getOptions().then(applyOptions, failOptions);
  };

  // Each year starts from its bundled list, all ticked; added holidays belong to one year.
  useEffect(() => {
    if (year === null) return;
    let current = true;
    getHolidays(year).then(
      (value) => {
        if (!current) return;
        setHolidays({state: 'ready', value});
        setRows(value.holidays.map((h) => ({date: h.date, names: h.names, added: false})));
        setTicked(new Set(value.holidays.map((h) => h.date)));
      },
      (e: unknown) => {
        if (current) setHolidays({state: 'error', message: messageOf(e)});
      },
    );
    return () => {
      current = false;
    };
  }, [year, holidayReload]);

  const changeYear = (next: number) => {
    setYear(next);
    setHolidays({state: 'loading'});
    setRows([]);
    setTicked(new Set());
    setErrors({});
  };

  const retryHolidays = () => {
    setHolidays({state: 'loading'});
    setHolidayReload((n) => n + 1);
  };

  const addHolidays = (added: {date: IsoDate; name: string}[]) => {
    setRows((prev) => added.reduce((acc, h) => addHoliday(acc, h.date, h.name), prev));
    setTicked((prev) => new Set([...prev, ...added.map((h) => h.date)]));
  };

  // Everything that shapes a plan; when it differs from the shown plan's, the plan is out of date.
  const inputKey = useMemo(
    () =>
      JSON.stringify({
        year,
        leaveText: leaveText.trim(),
        weeklyOffs: [...weeklyOffs].sort(),
        breakStyle,
        rows,
        ticked: [...ticked].sort(),
      }),
    [year, leaveText, weeklyOffs, breakStyle, rows, ticked],
  );

  if (options.state === 'loading') {
    return (
      <Frame>
        <VStack gap={4}>
          <Skeleton width="60%" height={32} />
          <Skeleton width="100%" height={120} index={1} />
          <Skeleton width="100%" height={200} index={2} />
        </VStack>
      </Frame>
    );
  }
  if (options.state === 'error') {
    return (
      <Frame>
        <Banner
          status="error"
          title={options.message}
          endContent={<Button label="Try again" variant="secondary" onClick={retryOptions} />}
        />
      </Frame>
    );
  }

  const opts = options.value;
  const currentYear = Number(opts.today.slice(0, 4));
  const holidaysReady = holidays.state === 'ready';

  const plan = async () => {
    if (planning.current || !holidaysReady || year === null) return;
    const leaveDays = parseLeaveDays(leaveText, opts.leaveDays.max);
    const next = {
      leaveDays: leaveDays === null ? LEAVE_DAYS_MESSAGE : undefined,
      weeklyOffs: weeklyOffs.length >= 7 ? WORKING_DAY_MESSAGE : undefined,
    };
    setErrors(next);
    if (next.leaveDays || next.weeklyOffs || leaveDays === null) return;
    const request: PlanRequest = {
      year,
      leaveDays,
      weeklyOffs: WEEKDAYS.map((d) => d.id).filter((d) => weeklyOffs.includes(d)),
      breakStyle,
      holidays: rows
        .filter((r) => ticked.has(r.date))
        .flatMap((r) => r.names.map((name) => ({date: r.date, name}))),
    };
    const seq = ++planSeq.current;
    const keyAtSubmit = inputKey;
    planning.current = true;
    setIsPlanning(true);
    try {
      const response = await planLeaves(request);
      if (seq === planSeq.current) {
        setResult({seq, plan: response, request, inputKey: keyAtSubmit});
      }
    } catch (e) {
      if (seq !== planSeq.current) return;
      const fields =
        e instanceof ApiError
          ? {leaveDays: e.fieldMessage('leaveDays'), weeklyOffs: e.fieldMessage('weeklyOffs')}
          : {};
      setErrors({
        ...fields,
        form: fields.leaveDays || fields.weeklyOffs ? undefined : messageOf(e),
      });
    } finally {
      planning.current = false;
      setIsPlanning(false);
    }
  };

  return (
    <Frame>
      <VStack gap={6}>
        <VStack gap={2}>
          <Heading level={1} type="display-2">
            LeaveWise
          </Heading>
          <Text type="body" color="secondary">
            Pick the leave days that turn weekends and holidays into the longest breaks.
          </Text>
        </VStack>

        <VStack gap={4}>
          <VStack gap={1}>
            <Text type="label" color="secondary">
              Year
            </Text>
            <SegmentedControl
              label="Year"
              value={String(year)}
              onChange={(value) => changeYear(Number(value))}
            >
              {opts.years.map((y) => (
                <SegmentedControlItem key={y} value={String(y)} label={String(y)} />
              ))}
            </SegmentedControl>
            {year === currentYear && (
              <Text type="supporting" color="secondary">
                Only days after today are planned.
              </Text>
            )}
          </VStack>
          <TextInput
            label="Leave days"
            description="Whole number from 0 to 60."
            value={leaveText}
            onChange={setLeaveText}
            onEnter={() => void plan()}
            width={160}
            status={errors.leaveDays ? {type: 'error', message: errors.leaveDays} : undefined}
          />
        </VStack>

        <Divider />

        {year !== null && (
          <HolidayEditor
            key={year}
            year={year}
            load={holidays}
            rows={rows}
            ticked={ticked}
            onAdd={addHolidays}
            onTickedChange={setTicked}
            onRetry={retryHolidays}
          />
        )}

        <Divider />

        <VStack gap={4}>
          <VStack gap={1}>
            <Text type="label" color="secondary">
              Weekly offs
            </Text>
            <ToggleButtonGroup
              xstyle={styles.wrapRow}
              label="Weekly offs"
              type="multiple"
              value={weeklyOffs}
              onChange={(value) => setWeeklyOffs((Array.isArray(value) ? value : []) as Weekday[])}
            >
              {WEEKDAYS.map((d) => (
                <ToggleButton key={d.id} value={d.id} label={d.label} />
              ))}
            </ToggleButtonGroup>
            {errors.weeklyOffs && <Banner status="error" title={errors.weeklyOffs} />}
          </VStack>
          <RadioList
            label="Break style"
            value={breakStyle}
            onChange={(value) => setBreakStyle(value as BreakStyleId)}
          >
            {opts.breakStyles.map((s) => (
              <RadioListItem
                key={s.id}
                value={s.id}
                label={s.label}
                description={BREAK_STYLE_HINTS[s.id]}
              />
            ))}
          </RadioList>
        </VStack>

        <VStack gap={3}>
          {errors.form && <Banner status="error" title={errors.form} />}
          <Button
            label="Plan my leaves"
            variant="primary"
            size="lg"
            width="100%"
            isLoading={isPlanning}
            isDisabled={!holidaysReady}
            onClick={() => void plan()}
          />
          {holidays.state === 'loading' && (
            <Text type="supporting" color="secondary" justify="center">
              Loading the holiday list for {year}…
            </Text>
          )}
        </VStack>

        {result && (
          <>
            <Divider />
            <Results
              key={result.seq}
              plan={result.plan}
              request={result.request}
              isStale={result.inputKey !== inputKey}
            />
          </>
        )}
      </VStack>
    </Frame>
  );
}

/** The template's page frame. */
function Frame({children}: {children: ReactNode}) {
  return (
    <Center axis="horizontal">
      <VStack hAlign="center" width="100%">
        <Section maxWidth={800} padding={6} paddingBlock={10} variant="section">
          {children}
        </Section>
      </VStack>
    </Center>
  );
}
