// Scaffolded from the Astryx "contact-form" page template (npm run astryx -- template contact-form);
// its frame (centred Section, stacked groups with dividers, one full-width primary action) is kept.

import {useCallback, useEffect, useMemo, useRef, useState, type ReactNode} from 'react';
import * as stylex from '@stylexjs/stylex';
import {Banner} from '@astryxdesign/core/Banner';
import {Button} from '@astryxdesign/core/Button';
import {Card} from '@astryxdesign/core/Card';
import {Center} from '@astryxdesign/core/Center';
import {Divider} from '@astryxdesign/core/Divider';
import {EmptyState} from '@astryxdesign/core/EmptyState';
import {Icon} from '@astryxdesign/core/Icon';
import {InputGroup} from '@astryxdesign/core/InputGroup';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {RadioList, RadioListItem} from '@astryxdesign/core/RadioList';
import {Section} from '@astryxdesign/core/Section';
import {SegmentedControl, SegmentedControlItem} from '@astryxdesign/core/SegmentedControl';
import {Skeleton} from '@astryxdesign/core/Skeleton';
import {Heading, Text} from '@astryxdesign/core/Text';
import {TextInput} from '@astryxdesign/core/TextInput';
import {Token} from '@astryxdesign/core/Token';
import {borderVars, colorVars, radiusVars} from '@astryxdesign/core/theme/tokens.stylex';
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
import {parseLeaveDays, stepLeaveDays} from '../../lib/validation';
import {MinusIcon, PlusIcon} from '../../lib/icons';
import {Results} from '../results/Results';
import {HolidayEditor, type HolidayLoad} from './HolidayEditor';
import {addHoliday, type HolidayRow} from './holidayRows';

// Layout widths below are raw px because Astryx 0.6.6 has no size tokens at this scale: the inputs card is
// 340 to 420 px beside a results column of at least 520 px, and below 800 px they stack (the canvas's phone
// layout). The weekly offs are always one row of seven equal columns; the theme trims the toggle padding so
// "Wed" fits at 360 px.
const styles = stylex.create({
  // The page paints the theme's body colour itself, so text outside the cards (the header) sits on a known
  // background in light and dark mode.
  page: {
    minHeight: '100vh',
    backgroundColor: colorVars['--color-background-body'],
  },
  columns: {
    width: '100%',
    alignItems: 'flex-start',
  },
  inputs: {
    flex: '1 1 340px',
    maxWidth: {default: '420px', '@media (max-width: 799px)': '100%'},
  },
  stepper: {
    maxWidth: '220px',
  },
  results: {
    flex: '999 1 520px',
    minWidth: 0,
  },
  emptyPanel: {
    borderRadius: radiusVars['--radius-page'],
    borderStyle: 'dashed',
    borderWidth: borderVars['--border-width'],
    borderColor: colorVars['--color-border-emphasized'],
  },
  weekdays: {
    display: 'grid',
    gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
  },
});

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

const BREAK_STYLE_ORDER: BreakStyleId[] = ['LONG_WEEKENDS', 'SHORT_TRIPS', 'BIG_TRIPS'];

function inputKeyOf(parts: {
  year: number | null;
  leaveText: string;
  weeklyOffs: Weekday[];
  breakStyle: BreakStyleId;
  rows: HolidayRow[];
  ticked: Set<IsoDate>;
}): string {
  return JSON.stringify({
    year: parts.year,
    leaveText: parts.leaveText.trim(),
    weeklyOffs: [...parts.weeklyOffs].sort(),
    breakStyle: parts.breakStyle,
    rows: parts.rows,
    ticked: [...parts.ticked].sort(),
  });
}

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
    () => inputKeyOf({year, leaveText, weeklyOffs, breakStyle, rows, ticked}),
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

  /** Plans with the current inputs; `style` replaces the break style first (the no-break suggestion). */
  const plan = async (style: BreakStyleId = breakStyle) => {
    if (planning.current || !holidaysReady || year === null) return;
    const leaveDays = parseLeaveDays(leaveText, opts.leaveDays.max);
    const next = {
      leaveDays: leaveDays === null ? LEAVE_DAYS_MESSAGE : undefined,
      weeklyOffs: weeklyOffs.length >= 7 ? WORKING_DAY_MESSAGE : undefined,
    };
    setErrors(next);
    if (next.leaveDays || next.weeklyOffs || leaveDays === null) return;
    // Only a plan that will be sent switches the break style (a refused one leaves the inputs as they were).
    if (style !== breakStyle) setBreakStyle(style);
    const request: PlanRequest = {
      year,
      leaveDays,
      weeklyOffs: WEEKDAYS.map((d) => d.id).filter((d) => weeklyOffs.includes(d)),
      breakStyle: style,
      holidays: rows
        .filter((r) => ticked.has(r.date))
        .flatMap((r) => r.names.map((name) => ({date: r.date, name}))),
    };
    const seq = ++planSeq.current;
    const keyAtSubmit = inputKeyOf({year, leaveText, weeklyOffs, breakStyle: style, rows, ticked});
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

  const max = opts.leaveDays.max;
  const typed = leaveText.trim();
  const typedNumber = /^\d+$/.test(typed) ? Number(typed) : null;
  const step = (delta: 1 | -1) => {
    setLeaveText(String(stepLeaveDays(leaveText, delta, max)));
    setErrors((prev) => ({...prev, leaveDays: undefined}));
  };
  const shorterStyles = BREAK_STYLE_ORDER.slice(
    0,
    BREAK_STYLE_ORDER.indexOf(result?.request.breakStyle ?? 'LONG_WEEKENDS'),
  )
    .reverse()
    .flatMap((id) => opts.breakStyles.filter((s) => s.id === id));

  return (
    <Frame>
      <HStack gap={6} wrap="wrap" xstyle={styles.columns} as="main">
        <VStack xstyle={styles.inputs}>
          <Card padding={6}>
            <VStack gap={6}>
              <VStack gap={2}>
                <Heading level={2} type="display-3">
                  Turn your leaves into the longest breaks
                </Heading>
                <Text type="body" color="secondary">
                  Enter your leave days. Every other input has a default.
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
                <InputGroup
                  label="Leave days"
                  description="Whole number from 0 to 60."
                  size="lg"
                  status={errors.leaveDays ? {type: 'error', message: errors.leaveDays} : undefined}
                  xstyle={styles.stepper}
                >
                  <Button
                    label="One fewer leave day"
                    isIconOnly
                    variant="ghost"
                    icon={<Icon icon={MinusIcon} />}
                    isDisabled={typedNumber !== null && typedNumber <= 0}
                    onClick={() => step(-1)}
                  />
                  {/* In a group the input's name is the group label plus its own; an empty own label keeps the PRD
                name "Leave days" instead of "Leave days Leave days". */}
                  <TextInput
                    label=""
                    value={leaveText}
                    onChange={(value) => {
                      setLeaveText(value);
                      setErrors((prev) => ({...prev, leaveDays: undefined}));
                    }}
                    onEnter={() => void plan()}
                  />
                  <Button
                    label="One more leave day"
                    isIconOnly
                    variant="ghost"
                    icon={<Icon icon={PlusIcon} />}
                    isDisabled={typedNumber !== null && typedNumber >= max}
                    onClick={() => step(1)}
                  />
                </InputGroup>
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
                    xstyle={styles.weekdays}
                    label="Weekly offs"
                    type="multiple"
                    value={weeklyOffs}
                    onChange={(value) => {
                      setWeeklyOffs((Array.isArray(value) ? value : []) as Weekday[]);
                      setErrors((prev) => ({...prev, weeklyOffs: undefined}));
                    }}
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
                {result && result.inputKey !== inputKey && (
                  <Text type="supporting" color="secondary" justify="center">
                    Your inputs changed since the plan below was made.
                  </Text>
                )}
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
            </VStack>
          </Card>
        </VStack>

        <VStack gap={6} xstyle={styles.results}>
          {result ? (
            <Results
              key={result.seq}
              plan={result.plan}
              request={result.request}
              isStale={result.inputKey !== inputKey}
              shorterStyles={shorterStyles}
              onTryStyle={(style) => void plan(style)}
              isBusy={isPlanning || !holidaysReady}
            />
          ) : (
            <Section variant="transparent" padding={8} xstyle={styles.emptyPanel}>
              <EmptyState
                headingLevel={2}
                icon={<Icon icon="calendar" size="lg" color="secondary" />}
                title="Your plan shows here"
                description="Tap Plan my leaves to see your breaks, the days off they give and the whole year on a calendar."
              />
            </Section>
          )}
        </VStack>
      </HStack>
    </Frame>
  );
}

/** The page frame: the header, then the content, centred up to 1200 px. */
function Frame({children}: {children: ReactNode}) {
  return (
    <Center axis="horizontal" xstyle={styles.page}>
      <VStack width="100%" maxWidth={1200} gap={4} paddingInline={4} paddingBlockEnd={10}>
        <HStack gap={3} wrap="wrap" vAlign="center" justify="between" paddingBlock={5} as="header">
          <HStack gap={2} vAlign="center">
            <Icon icon="calendar" color="accent" size="lg" />
            <Heading level={1} type="display-3">
              LeaveWise
            </Heading>
          </HStack>
          <HStack gap={2} wrap="wrap">
            <Token label="No login" size="sm" />
            <Token label="Nothing stored" size="sm" />
          </HStack>
        </HStack>
        {children}
      </VStack>
    </Center>
  );
}
