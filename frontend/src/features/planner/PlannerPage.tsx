// Laid out to the Claude Design canvas "LeaveWise UI redesign" (ADR 0008 amendments): desktop is the inputs card
// beside the results; on a phone the same page shows the inputs, then the results with "Edit inputs" to go back,
// and the holidays in a bottom sheet. One URL, one page (PRD).

import {useCallback, useEffect, useMemo, useRef, useState, type ReactNode} from 'react';
import * as stylex from '@stylexjs/stylex';
import {Banner} from '@astryxdesign/core/Banner';
import {BottomSheet} from '@astryxdesign/core/BottomSheet';
import {Button} from '@astryxdesign/core/Button';
import {Card} from '@astryxdesign/core/Card';
import {EmptyState} from '@astryxdesign/core/EmptyState';
import {Icon} from '@astryxdesign/core/Icon';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {RadioList, RadioListItem} from '@astryxdesign/core/RadioList';
import {SegmentedControl, SegmentedControlItem} from '@astryxdesign/core/SegmentedControl';
import {Skeleton} from '@astryxdesign/core/Skeleton';
import {Heading, Text} from '@astryxdesign/core/Text';
import {TextInput} from '@astryxdesign/core/TextInput';
import {Token} from '@astryxdesign/core/Token';
import {ToggleButton, ToggleButtonGroup} from '@astryxdesign/core/ToggleButton';
import {
  borderVars,
  colorVars,
  radiusVars,
  spacingVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import {canvasVars} from '../../theme/canvas.stylex';
import {ApiError, getHolidays, getOptions, messageOf, planLeaves} from '../../api/client';
import type {
  BreakStyleId,
  IsoDate,
  Options,
  PlanRequest,
  PlanResponse,
  Weekday,
} from '../../api/types';
import {MinusIcon, PlusIcon} from '../../lib/icons';
import {useIsPhone} from '../../lib/useIsPhone';
import {parseLeaveDays, stepLeaveDays} from '../../lib/validation';
import {PhoneResults} from '../results/PhoneResults';
import {Results} from '../results/Results';
import {DaySquare} from '../shared/days';
import {Logo} from '../shared/Logo';
import {
  HolidayAdder,
  HolidayList,
  HolidaySource,
  NoListPanel,
  type AddMode,
  type HolidayLoad,
} from './HolidayEditor';
import {addHoliday, countOn, type HolidayRow} from './holidayRows';

// Layout widths are raw px: Astryx 0.6.6 has no size tokens at this scale. The canvas frame is 1280 px; the inputs
// card is 340 to 400 px beside a results column of at least 560 px.
const styles = stylex.create({
  page: {
    minHeight: '100vh',
    backgroundColor: colorVars['--color-background-body'],
  },
  frame: {
    width: '100%',
    maxWidth: '1280px',
    marginInline: 'auto',
    boxSizing: 'border-box',
    paddingInline: {default: spacingVars['--spacing-8'], '@media (max-width: 799px)': 0},
  },
  header: {
    paddingBlock: {
      default: spacingVars['--spacing-6'],
      '@media (max-width: 799px)': spacingVars['--spacing-4'],
    },
    paddingInline: {default: 0, '@media (max-width: 799px)': spacingVars['--spacing-4']},
  },
  main: {
    alignItems: 'flex-start',
    paddingBlockEnd: spacingVars['--spacing-12'],
  },
  inputs: {
    flex: '1 1 340px',
    maxWidth: '400px',
    minWidth: 0,
  },
  results: {
    flex: '999 1 560px',
    minWidth: 0,
  },
  phoneIntro: {
    paddingInline: spacingVars['--spacing-4'],
    paddingBlockEnd: spacingVars['--spacing-5'],
  },
  phoneCard: {
    marginInline: spacingVars['--spacing-4'],
  },
  leaveInput: {
    width: canvasVars.leaveInput,
  },
  leaveInputPhone: {
    flex: '1 1 auto',
    minWidth: 0,
  },
  stepperRow: {
    minHeight: spacingVars['--spacing-12'],
  },
  weekdays: {
    display: 'grid',
    gridTemplateColumns: 'repeat(7, minmax(0, 1fr))',
    gap: spacingVars['--spacing-1-5'],
  },
  bars: {
    display: 'flex',
    gap: spacingVars['--spacing-0-5'],
  },
  bar: {
    width: spacingVars['--spacing-1-5'],
    height: canvasVars.barHeight,
    borderRadius: radiusVars['--radius-inner'],
    backgroundColor: colorVars['--color-accent'],
  },
  barThin: {
    width: spacingVars['--spacing-1'],
  },
  barFaint: {
    opacity: 0.45,
  },
  holidaysRow: {
    borderWidth: borderVars['--border-width'],
    borderStyle: 'solid',
    borderColor: colorVars['--color-border-emphasized'],
    borderRadius: radiusVars['--radius-container'],
    padding: spacingVars['--spacing-3'],
  },
  // On a phone the Plan bar stays at the bottom of the screen (canvas); index.css adds scroll padding so a focused
  // field is never hidden behind it (WCAG 2.4.11).
  planBar: {
    position: 'sticky',
    bottom: 0,
    zIndex: 1,
    backgroundColor: colorVars['--color-background-body'],
    borderTopWidth: borderVars['--border-width'],
    borderTopStyle: 'solid',
    borderTopColor: colorVars['--color-border'],
    paddingInline: spacingVars['--spacing-4'],
    paddingBlockStart: spacingVars['--spacing-3'],
    paddingBlockEnd: spacingVars['--spacing-5'],
    marginBlockStart: spacingVars['--spacing-5'],
  },
  emptyPanel: {
    borderRadius: canvasVars.radiusCard,
    borderStyle: 'dashed',
    borderWidth: borderVars['--border-width'],
    borderColor: colorVars['--color-border-emphasized'],
  },
});

// PRD messages, word for word.
const LEAVE_DAYS_MESSAGE = 'Enter a whole number from 0 to 60.';
const WORKING_DAY_MESSAGE = 'Keep at least one working day in the week.';

const WEEKDAYS: {id: Weekday; label: string; short: string}[] = [
  {id: 'MONDAY', label: 'Mon', short: 'Mo'},
  {id: 'TUESDAY', label: 'Tue', short: 'Tu'},
  {id: 'WEDNESDAY', label: 'Wed', short: 'We'},
  {id: 'THURSDAY', label: 'Thu', short: 'Th'},
  {id: 'FRIDAY', label: 'Fri', short: 'Fr'},
  {id: 'SATURDAY', label: 'Sat', short: 'Sa'},
  {id: 'SUNDAY', label: 'Sun', short: 'Su'},
];

const BREAK_STYLE_HINTS: Record<BreakStyleId, string> = {
  LONG_WEEKENDS: '3 or more days off',
  SHORT_TRIPS: '5 or more days off',
  BIG_TRIPS: '9 or more days off',
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
  const isPhone = useIsPhone();
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
  // Holidays: the add panel (desktop: under the list; phone: inside the sheet).
  const [addMode, setAddMode] = useState<AddMode | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  // Phone only: which screen of the page is showing.
  const [phoneView, setPhoneView] = useState<'inputs' | 'results'>('inputs');
  // A sheet left open on a phone must not reopen when a narrow window comes back (adjusted during render, as React
  // recommends for state that follows a changed input).
  const [wasPhone, setWasPhone] = useState(isPhone);
  if (wasPhone !== isPhone) {
    setWasPhone(isPhone);
    if (!isPhone) setSheetOpen(false);
  }
  const planning = useRef(false); // guards a second Enter before React re-renders
  const yearRef = useRef<number | null>(null); // the year the holiday rows belong to, for late paste results
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

  useEffect(() => {
    yearRef.current = year;
  }, [year]);

  // Each year starts from its bundled list, all ticked; added holidays belong to one year.
  useEffect(() => {
    if (year === null) return;
    let current = true;
    getHolidays(year).then(
      (value) => {
        if (!current) return;
        setHolidays({state: 'ready', value});
        setRows(
          value.holidays.map((h) => ({
            date: h.date,
            names: h.names,
            added: false,
            optional: h.optional,
          })),
        );
        // Floaters start unticked: they are days off only for people who pick them.
        setTicked(new Set(value.holidays.filter((h) => !h.optional).map((h) => h.date)));
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
    setAddMode(null);
    setHolidays({state: 'loading'});
    setRows([]);
    setTicked(new Set());
    setErrors({});
  };

  const retryHolidays = () => {
    setHolidays({state: 'loading'});
    setHolidayReload((n) => n + 1);
  };

  /** Holidays from the adder for `forYear`; dropped if the year changed meanwhile (a paste still in flight). */
  const addHolidays = (added: {date: IsoDate; name: string}[], forYear: number) => {
    if (forYear !== yearRef.current) return;
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
      <Frame isPhone={isPhone}>
        <VStack gap={4} padding={4}>
          <Skeleton width="60%" height={32} />
          <Skeleton width="100%" height={120} index={1} />
          <Skeleton width="100%" height={200} index={2} />
        </VStack>
      </Frame>
    );
  }
  if (options.state === 'error') {
    return (
      <Frame isPhone={isPhone}>
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
  const max = opts.leaveDays.max;
  const typed = leaveText.trim();
  const typedNumber = /^\d+$/.test(typed) ? Number(typed) : null;

  /** Plans with the current inputs; `style` replaces the break style first (the no-break suggestion). */
  const plan = async (style: BreakStyleId = breakStyle) => {
    if (planning.current || !holidaysReady || year === null) return;
    const leaveDays = parseLeaveDays(leaveText, max);
    const next = {
      leaveDays: leaveDays === null ? LEAVE_DAYS_MESSAGE : undefined,
      weeklyOffs: weeklyOffs.length >= 7 ? WORKING_DAY_MESSAGE : undefined,
    };
    setErrors(next);
    if (next.leaveDays || next.weeklyOffs || leaveDays === null) {
      if (isPhone) setPhoneView('inputs');
      return;
    }
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
    if (isPhone) setPhoneView('results');
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
      if (isPhone) setPhoneView('inputs');
    } finally {
      planning.current = false;
      setIsPlanning(false);
    }
  };

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
  const isStale = result !== null && result.inputKey !== inputKey;

  const yearField = (
    <VStack gap={2}>
      <Text type="field">Year</Text>
      <SegmentedControl
        label="Year"
        value={String(year)}
        onChange={(value) => changeYear(Number(value))}
        layout="fill"
        size="lg"
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
  );

  const leaveField = (
    <VStack gap={2}>
      <Text type="field" aria-hidden="true">
        Leave days
      </Text>
      <HStack gap={2} vAlign="start">
        <Button
          label="One fewer leave day"
          isIconOnly
          variant="stepper"
          icon={<Icon icon={MinusIcon} />}
          isDisabled={typedNumber !== null && typedNumber <= 0}
          onClick={() => step(-1)}
        />
        <VStack xstyle={isPhone ? styles.leaveInputPhone : styles.leaveInput}>
          <TextInput
            label="Leave days"
            isLabelHidden
            size="lg"
            value={leaveText}
            onChange={(value) => {
              setLeaveText(value);
              setErrors((prev) => ({...prev, leaveDays: undefined}));
            }}
            onEnter={() => void plan()}
            status={errors.leaveDays ? {type: 'error', message: errors.leaveDays} : undefined}
          />
        </VStack>
        <Button
          label="One more leave day"
          isIconOnly
          variant="stepper"
          icon={<Icon icon={PlusIcon} />}
          isDisabled={typedNumber !== null && typedNumber >= max}
          onClick={() => step(1)}
        />
        {!isPhone && (
          <HStack vAlign="center" xstyle={styles.stepperRow}>
            <Text type="supporting" color="secondary">
              0 to 60
            </Text>
          </HStack>
        )}
      </HStack>
      {isPhone && (
        <Text type="supporting" color="secondary">
          Whole number from 0 to 60.
        </Text>
      )}
    </VStack>
  );

  const weeklyOffField = (
    <VStack gap={2}>
      <Text type="field">Weekly offs</Text>
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
          <ToggleButton key={d.id} value={d.id} label={d.label}>
            {isPhone ? d.short : d.label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
      {errors.weeklyOffs && <Banner status="error" title={errors.weeklyOffs} />}
    </VStack>
  );

  const breakStyleField = (
    <RadioList
      label="Break style"
      value={breakStyle}
      onChange={(value) => setBreakStyle(value as BreakStyleId)}
    >
      {opts.breakStyles.map((s) => (
        <RadioListItem
          key={s.id}
          value={s.id}
          label={<Text weight="semibold">{s.label}</Text>}
          description={BREAK_STYLE_HINTS[s.id]}
          endContent={isPhone ? undefined : <Bars days={s.minDays} isOn={s.id === breakStyle} />}
        />
      ))}
    </RadioList>
  );

  const noList = holidays.state === 'ready' && rows.length === 0;
  const holidayHeader = (
    <HStack gap={2} vAlign="center" justify="between">
      <Text type="field">
        Holidays{' '}
        {rows.length > 0 && (
          <Text color="secondary" weight="medium">
            · {countOn(rows, ticked)}
          </Text>
        )}
      </Text>
      {rows.length > 0 && (
        <Button
          label={addMode ? 'Done' : 'Edit list'}
          variant="link"
          onClick={() => setAddMode(addMode ? null : 'one')}
        />
      )}
    </HStack>
  );

  const holidayList =
    year === null ? null : (
      <HolidayList
        year={year}
        load={holidays}
        rows={rows}
        ticked={ticked}
        onTickedChange={setTicked}
        onRetry={retryHolidays}
      />
    );
  const adder =
    year === null || addMode === null || holidays.state !== 'ready' ? null : (
      <HolidayAdder
        key={year}
        year={year}
        mode={addMode}
        onModeChange={setAddMode}
        onAdd={addHolidays}
      />
    );

  const desktopHolidays = (
    <VStack gap={3}>
      {holidayHeader}
      {holidayList}
      {noList && year !== null && <NoListPanel year={year} onStart={setAddMode} />}
      {adder}
      <HolidaySource load={holidays} rows={rows} ticked={ticked} />
    </VStack>
  );

  const phoneHolidays = (
    <>
      <Button
        label={`Holidays ${rows.length > 0 ? countOn(rows, ticked) : 'None yet'} · tap to review or add`}
        variant="secondary"
        width="100%"
        onClick={() => {
          setSheetOpen(true);
          if (addMode === null && noList) setAddMode('one');
        }}
      >
        <HStack as="span" gap={3} vAlign="center" xstyle={styles.holidaysRow} width="100%">
          <DaySquare code="H" size="lg" />
          <VStack as="span" gap={0.5}>
            <Text weight="semibold">Holidays</Text>
            <Text type="supporting" color="secondary">
              {rows.length > 0 ? countOn(rows, ticked) : 'None yet'} · tap to review or add
            </Text>
          </VStack>
        </HStack>
      </Button>
      <BottomSheet
        isOpen={sheetOpen}
        onOpenChange={setSheetOpen}
        label={`Holidays in ${year}`}
        purpose="form"
        height="tall"
        padding={4}
      >
        <VStack gap={4}>
          <HStack vAlign="center" justify="between">
            <Heading level={2}>Holidays in {year}</Heading>
            <Button label="Done" variant="link" onClick={() => setSheetOpen(false)} />
          </HStack>
          <HolidaySource load={holidays} rows={rows} ticked={ticked} showCount />
          {holidayList}
          {noList && year !== null && <NoListPanel year={year} onStart={setAddMode} />}
          {year !== null && holidays.state === 'ready' && (
            <HolidayAdder
              key={year}
              year={year}
              mode={addMode ?? 'one'}
              onModeChange={setAddMode}
              onAdd={addHolidays}
            />
          )}
        </VStack>
      </BottomSheet>
    </>
  );

  const planButton = (
    <Button
      label="Plan my leaves"
      variant="plan"
      isLoading={isPlanning}
      isDisabled={!holidaysReady}
      onClick={() => void plan()}
    />
  );

  const formError = errors.form && <Banner status="error" title={errors.form} />;
  const loadingNote = holidays.state === 'loading' && (
    <Text type="supporting" color="secondary" justify="center">
      Loading the holiday list for {year}…
    </Text>
  );

  const resultsPanel = result ? (
    <Results
      key={result.seq}
      plan={result.plan}
      request={result.request}
      isStale={isStale}
      shorterStyles={shorterStyles}
      onTryStyle={(style) => void plan(style)}
      onPlanAgain={() => void plan()}
      isBusy={isPlanning || !holidaysReady}
    />
  ) : isPlanning ? (
    <PlanningSkeleton />
  ) : (
    <VStack padding={8} xstyle={styles.emptyPanel}>
      <EmptyState
        headingLevel={2}
        icon={<Icon icon="calendar" size="lg" color="secondary" />}
        title="Your plan shows here"
        description="Tap Plan my leaves to see your breaks, the days off they give and the whole year on a calendar."
      />
    </VStack>
  );

  if (isPhone) {
    if (phoneView === 'results' && (result || isPlanning)) {
      return (
        <Frame isPhone header={false}>
          {result ? (
            <PhoneResults
              key={result.seq}
              plan={result.plan}
              request={result.request}
              isStale={isStale}
              shorterStyles={shorterStyles}
              onTryStyle={(style) => void plan(style)}
              onPlanAgain={() => void plan()}
              onEditInputs={() => setPhoneView('inputs')}
              isBusy={isPlanning || !holidaysReady}
            />
          ) : (
            <VStack padding={4}>
              <PlanningSkeleton />
            </VStack>
          )}
        </Frame>
      );
    }
    const summary = `${year} · ${typed || '–'} leaves · ${
      WEEKDAYS.filter((d) => weeklyOffs.includes(d.id))
        .map((d) => d.label)
        .join(', ') || 'no weekly offs'
    } off · ${opts.breakStyles.find((s) => s.id === breakStyle)?.label ?? ''}`;
    return (
      <Frame isPhone>
        <VStack gap={2} xstyle={styles.phoneIntro}>
          <Heading level={1} type="title-phone">
            Turn your leaves into the longest breaks
          </Heading>
          <Text color="secondary">
            Every input has a default. Enter your leave days and tap Plan.
          </Text>
        </VStack>
        <VStack xstyle={styles.phoneCard}>
          <Card padding={5}>
            <VStack gap={6}>
              {yearField}
              {leaveField}
              {weeklyOffField}
              {breakStyleField}
              {phoneHolidays}
              {formError}
            </VStack>
          </Card>
        </VStack>
        <VStack gap={1.5} xstyle={styles.planBar}>
          {result && (
            <Button label="See your plan" variant="link" onClick={() => setPhoneView('results')} />
          )}
          {planButton}
          {loadingNote || (
            <Text type="supporting" color="secondary" justify="center">
              {summary}
            </Text>
          )}
        </VStack>
      </Frame>
    );
  }

  return (
    <Frame isPhone={false}>
      <HStack gap={8} wrap="wrap" xstyle={styles.main} as="main">
        <VStack xstyle={styles.inputs}>
          <Card>
            <VStack gap={6} as="form" onSubmit={(e) => e.preventDefault()}>
              <VStack gap={2}>
                <Heading level={1} type="title">
                  Turn your leaves into the longest breaks
                </Heading>
                <Text color="secondary">
                  Enter your balance. We place every leave day for the most days off.
                </Text>
              </VStack>
              {yearField}
              {leaveField}
              {weeklyOffField}
              {breakStyleField}
              {desktopHolidays}
              {formError}
              {planButton}
              {loadingNote}
            </VStack>
          </Card>
        </VStack>
        <VStack gap={6} xstyle={styles.results}>
          {resultsPanel}
        </VStack>
      </HStack>
    </Frame>
  );
}

/** The canvas's mini bars beside each break style: one bar per day of the shortest break. */
function Bars({days, isOn}: {days: number; isOn: boolean}) {
  return (
    <span aria-hidden="true" {...stylex.props(styles.bars)}>
      {Array.from({length: days}, (_, i) => (
        <span
          key={i}
          {...stylex.props(styles.bar, days > 5 && styles.barThin, !isOn && styles.barFaint)}
        />
      ))}
    </span>
  );
}

function PlanningSkeleton() {
  return (
    <VStack gap={4} aria-busy="true" aria-label="Planning">
      <Skeleton width="100%" height={180} />
      <Skeleton width="100%" height={52} index={1} />
      <Skeleton width="100%" height={52} index={2} />
      <Skeleton width="100%" height={52} index={3} />
    </VStack>
  );
}

/** The page frame: the canvas header, then the content. */
function Frame({
  children,
  isPhone,
  header = true,
}: {
  children: ReactNode;
  isPhone: boolean;
  header?: boolean;
}) {
  return (
    <VStack xstyle={styles.page}>
      <VStack xstyle={styles.frame}>
        {header && (
          <HStack
            gap={3}
            wrap="wrap"
            vAlign="center"
            justify="between"
            xstyle={styles.header}
            as="header"
          >
            <HStack gap={3} vAlign="center">
              <Logo size={isPhone ? 30 : 36} />
              <Text type="wordmark">LeaveWise</Text>
            </HStack>
            {isPhone ? (
              <Text type="supporting" color="secondary">
                No login · nothing stored
              </Text>
            ) : (
              <HStack gap={2} wrap="wrap">
                <Token label="No login" />
                <Token label="Nothing stored" />
              </HStack>
            )}
          </HStack>
        )}
        {children}
      </VStack>
    </VStack>
  );
}
