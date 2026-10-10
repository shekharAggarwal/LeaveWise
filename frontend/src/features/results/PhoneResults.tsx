import {useEffect, useMemo, useRef, useState, type KeyboardEvent} from 'react';
import * as stylex from '@stylexjs/stylex';
import {BottomSheet} from '@astryxdesign/core/BottomSheet';
import {Button} from '@astryxdesign/core/Button';
import {Card} from '@astryxdesign/core/Card';
import {Grid} from '@astryxdesign/core/Grid';
import {Icon} from '@astryxdesign/core/Icon';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {SegmentedControl, SegmentedControlItem} from '@astryxdesign/core/SegmentedControl';
import {Heading, Text} from '@astryxdesign/core/Text';
import {
  borderVars,
  colorVars,
  fontWeightVars,
  radiusVars,
  spacingVars,
  textSizeVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import {canvasVars} from '../../theme/canvas.stylex';
import type {BreakStyleId, CalendarDay, IsoDate, PlanRequest, PlanResponse} from '../../api/types';
import {formatRange, monthOf} from '../../lib/dates';
import {addDays, breakDays} from '../shared/dayCodes';
import {DaySquare, DayStrip} from '../shared/days';
import {dayCode, dayDetail} from './describeDay';
import {holidayNames, leaveList} from './format';
import {Summary} from './Summary';
import {DayButton, Legend} from './YearCalendar';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const FIRST_BREAKS = 6;

const styles = stylex.create({
  body: {
    paddingInline: spacingVars['--spacing-4'],
    paddingBlockEnd: spacingVars['--spacing-6'],
  },
  topBar: {
    paddingInline: spacingVars['--spacing-2'],
    paddingBlock: spacingVars['--spacing-3'],
  },
  list: {
    margin: 0,
    padding: 0,
    listStyle: 'none',
  },
  row: {
    paddingBlock: spacingVars['--spacing-3'],
    borderBottomWidth: borderVars['--border-width'],
    borderBottomStyle: 'solid',
    borderBottomColor: colorVars['--color-border'],
  },
  days: {
    textAlign: 'end',
    flexShrink: 0,
  },
  chips: {
    display: 'flex',
    gap: spacingVars['--spacing-1-5'],
    overflowX: 'auto',
    paddingBlockEnd: spacingVars['--spacing-1'],
  },
  // Month chips: a month with its break count (raw buttons: no Astryx chip shows two lines).
  chip: {
    appearance: 'none',
    flex: 'none',
    minWidth: canvasVars.chipMinWidth,
    minHeight: spacingVars['--spacing-11'],
    paddingBlock: spacingVars['--spacing-1-5'],
    borderRadius: canvasVars.radiusChip,
    borderWidth: borderVars['--border-width'],
    borderStyle: 'solid',
    borderColor: colorVars['--color-border'],
    backgroundColor: colorVars['--color-background-surface'],
    color: colorVars['--color-text-primary'],
    fontFamily: 'inherit',
    fontSize: textSizeVars['--font-size-sm'],
    fontWeight: fontWeightVars['--font-weight-semibold'],
    textAlign: 'center',
    cursor: 'pointer',
  },
  chipOn: {
    backgroundColor: colorVars['--color-text-primary'],
    borderColor: colorVars['--color-text-primary'],
    color: colorVars['--color-background-surface'],
  },
  chipCount: {
    display: 'block',
    fontSize: textSizeVars['--font-size-xs'],
    fontWeight: fontWeightVars['--font-weight-medium'],
  },
  tint: {
    borderRadius: radiusVars['--radius-container'],
    backgroundColor: colorVars['--color-accent-muted'],
    paddingBlock: spacingVars['--spacing-3'],
    paddingInline: spacingVars['--spacing-4'],
  },
});

interface Props {
  plan: PlanResponse;
  request: PlanRequest;
  isStale: boolean;
  shorterStyles: {id: BreakStyleId; label: string}[];
  onTryStyle: (style: BreakStyleId) => void;
  onPlanAgain: () => void;
  onEditInputs: () => void;
  isBusy: boolean;
}

/** The canvas's phone results screen. Mounted once per plan, so it takes focus when a plan arrives. */
export function PhoneResults({
  plan,
  request,
  isStale,
  shorterStyles,
  onTryStyle,
  onPlanAgain,
  onEditInputs,
  isBusy,
}: Props) {
  const [tab, setTab] = useState<'breaks' | 'calendar'>('breaks');
  const [showAll, setShowAll] = useState(false);
  const region = useRef<HTMLElement | null>(null);

  // A new plan opens at the top, with "Edit inputs" in view; focus moves to the results without scrolling past it.
  useEffect(() => {
    window.scrollTo(0, 0);
    region.current?.focus({preventScroll: true});
  }, []);

  const breaks = showAll ? plan.breaks : plan.breaks.slice(0, FIRST_BREAKS);

  return (
    <VStack>
      <HStack vAlign="center" justify="between" xstyle={styles.topBar}>
        <Button
          label="Edit inputs"
          variant="link"
          icon={<Icon icon="chevronLeft" />}
          onClick={onEditInputs}
        />
        <Text type="wordmark">LeaveWise</Text>
      </HStack>
      <section aria-labelledby="results-heading" tabIndex={-1} ref={region}>
        <VStack gap={4} xstyle={styles.body}>
          <Summary
            plan={plan}
            request={request}
            isStale={isStale}
            isPhone
            shorterStyles={shorterStyles}
            onTryStyle={onTryStyle}
            onPlanAgain={onPlanAgain}
            isBusy={isBusy}
          />
          {plan.breaks.length > 0 && (
            <SegmentedControl
              label="Results sections"
              value={tab}
              onChange={(value) => setTab(value as 'breaks' | 'calendar')}
              layout="fill"
              size="lg"
            >
              <SegmentedControlItem value="breaks" label="Breaks" />
              <SegmentedControlItem value="calendar" label="Calendar" />
            </SegmentedControl>
          )}
          {plan.breaks.length > 0 && tab === 'breaks' ? (
            <Card padding={4}>
              <VStack>
                <Heading level={2} type="card" id="breaks-heading">
                  Your breaks
                </Heading>
                <ol aria-labelledby="breaks-heading" {...stylex.props(styles.list)}>
                  {breaks.map((b) => (
                    <li key={b.start} {...stylex.props(styles.row)}>
                      <VStack gap={2}>
                        <HStack gap={3} justify="between" vAlign="start">
                          <VStack gap={0.5}>
                            <Text weight="bold">{formatRange(b.start, b.end, plan.year)}</Text>
                            <Text type="small" color="secondary">
                              Leave {leaveList(b, plan.year)}
                              {b.holidays.length > 0 ? ` · ${holidayNames(b.holidays)}` : ''} ·{' '}
                              {b.daysOffPerLeave.toFixed(1)} days off per leave
                            </Text>
                          </VStack>
                          <VStack xstyle={styles.days}>
                            <Text type="ratio">{b.days}</Text>
                            <Text type="supporting" color="secondary">
                              days off
                            </Text>
                          </VStack>
                        </HStack>
                        <DayStrip codes={breakDays(b)} size="sm" />
                      </VStack>
                    </li>
                  ))}
                </ol>
                {plan.breaks.length > FIRST_BREAKS && (
                  <Button
                    label={showAll ? 'Show fewer breaks' : `Show all ${plan.breaks.length} breaks`}
                    variant="link"
                    width="100%"
                    onClick={() => setShowAll(!showAll)}
                  />
                )}
              </VStack>
            </Card>
          ) : (
            <MonthCalendar plan={plan} />
          )}
        </VStack>
      </section>
    </VStack>
  );
}

/** One month at a time with month chips, 44 px days and a sheet that says what a tapped day is (canvas). */
function MonthCalendar({plan}: {plan: PlanResponse}) {
  const firstBreakMonth = plan.breaks[0] ? monthOf(plan.breaks[0].start) - 1 : 0;
  const [month, setMonth] = useState(Math.max(0, firstBreakMonth));
  const [selected, setSelected] = useState<IsoDate | null>(null);
  const byDate = useMemo(() => new Map(plan.days.map((d) => [d.date, d])), [plan.days]);
  const days = useMemo(
    () => plan.days.filter((d) => monthOf(d.date) === month + 1),
    [plan.days, month],
  );
  const counts = useMemo(
    () => MONTHS.map((_, i) => plan.breaks.filter((b) => monthOf(b.start) === i + 1).length),
    [plan.breaks],
  );
  const selectedDay: CalendarDay | undefined = selected ? byDate.get(selected) : undefined;
  const detail = selectedDay ? dayDetail(selectedDay, plan) : null;
  const code = selectedDay ? dayCode(selectedDay) : null;
  // Roving focus like the year view: one day is the Tab stop; arrow keys move, across months too.
  const [focused, setFocused] = useState<IsoDate | null>(null);
  const buttons = useRef(new Map<IsoDate, HTMLButtonElement>());
  const pendingFocus = useRef<IsoDate | null>(null);
  const chips = useRef(new Map<number, HTMLButtonElement>());

  // Keep the shown month's chip in view (October would otherwise sit off-screen to the right).
  useEffect(() => {
    chips.current.get(month)?.scrollIntoView?.({inline: 'center', block: 'nearest'});
  }, [month]);
  const tabStop =
    focused && monthOf(focused) === month + 1
      ? focused
      : (days.find((d) => d.planned)?.date ?? days[0]?.date);

  useEffect(() => {
    if (pendingFocus.current) {
      buttons.current.get(pendingFocus.current)?.focus();
      pendingFocus.current = null;
    }
  });

  const move = (event: KeyboardEvent, from: IsoDate) => {
    const step: Record<string, number> = {ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7};
    if (!(event.key in step)) return;
    const target = addDays(from, step[event.key] ?? 0);
    if (!byDate.has(target)) return;
    event.preventDefault();
    setFocused(target);
    if (monthOf(target) !== month + 1) {
      setMonth(monthOf(target) - 1);
      pendingFocus.current = target;
    } else {
      buttons.current.get(target)?.focus();
    }
  };

  return (
    <Card padding={4}>
      <VStack gap={3}>
        <HStack vAlign="center" justify="between">
          <Button
            label="Previous month"
            isIconOnly
            variant="stepper"
            icon={<Icon icon="chevronLeft" />}
            isDisabled={month === 0}
            onClick={() => setMonth(month - 1)}
          />
          <Heading level={2} type="card">
            {MONTH_NAMES[month]} {plan.year}
          </Heading>
          <Button
            label="Next month"
            isIconOnly
            variant="stepper"
            icon={<Icon icon="chevronRight" />}
            isDisabled={month === 11}
            onClick={() => setMonth(month + 1)}
          />
        </HStack>
        <span {...stylex.props(styles.chips)} role="group" aria-label="Months">
          {MONTHS.map((m, i) => (
            <button
              key={m}
              type="button"
              aria-pressed={i === month}
              ref={(el) => {
                if (el) chips.current.set(i, el);
                else chips.current.delete(i);
              }}
              aria-label={`${MONTH_NAMES[i]}: ${counts[i] ?? 0} ${counts[i] === 1 ? 'break' : 'breaks'}`}
              onClick={() => setMonth(i)}
              {...stylex.props(styles.chip, i === month && styles.chipOn)}
            >
              <span aria-hidden="true">{m}</span>
              <span aria-hidden="true" {...stylex.props(styles.chipCount)}>
                {counts[i] ? `${counts[i]} ${counts[i] === 1 ? 'break' : 'breaks'}` : '—'}
              </span>
            </button>
          ))}
        </span>
        <Grid columns={7} gap={1}>
          {WEEKDAYS.map((d) => (
            <Text key={d} type="supporting" color="secondary" justify="center">
              <span aria-hidden="true">{d}</span>
            </Text>
          ))}
          {days.map((day) => (
            <DayButton
              key={day.date}
              day={day}
              plan={plan}
              size="large"
              isFirstOfMonth={day === days[0]}
              isSelected={day.date === selected}
              isTabStop={day.date === tabStop}
              buttonRef={(el) => {
                if (el) buttons.current.set(day.date, el);
                else buttons.current.delete(day.date);
              }}
              onSelect={() => {
                setSelected(day.date);
                setFocused(day.date);
              }}
              onKeyDown={(e) => move(e, day.date)}
            />
          ))}
        </Grid>
        <Legend plan={plan} />
      </VStack>
      <BottomSheet
        isOpen={selectedDay !== undefined}
        onOpenChange={(open) => !open && setSelected(null)}
        label={detail ? `${detail.heading}: ${detail.kind}` : 'Day'}
        height="hug"
        padding={5}
      >
        {detail && (
          <VStack gap={3}>
            <HStack gap={3} vAlign="center">
              {code && <DaySquare code={code} size="lg" />}
              <VStack gap={0.5}>
                <Heading level={2} type="card">
                  {detail.heading}
                </Heading>
                <Text color="secondary">{detail.kind}</Text>
              </VStack>
            </HStack>
            <VStack xstyle={styles.tint}>
              <Text>{detail.detail}</Text>
            </VStack>
            <Button label="Close" variant="secondary" onClick={() => setSelected(null)} />
          </VStack>
        )}
      </BottomSheet>
    </Card>
  );
}
