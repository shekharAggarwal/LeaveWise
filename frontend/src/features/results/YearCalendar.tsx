import {useMemo, useRef, useState, type KeyboardEvent, type Ref} from 'react';
import * as stylex from '@stylexjs/stylex';
import {canvasVars} from '../../theme/canvas.stylex';
import {Card} from '@astryxdesign/core/Card';
import {Grid} from '@astryxdesign/core/Grid';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {Heading, Text} from '@astryxdesign/core/Text';
import {
  colorVars,
  focusVars,
  fontWeightVars,
  spacingVars,
  textSizeVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import type {CalendarDay, IsoDate, PlanResponse} from '../../api/types';
import {dayOfMonth, monthName, monthOf, weekdayIndex} from '../../lib/dates';
import {addDays, kindStyles} from '../shared/dayCodes';
import {DaySquare} from '../shared/days';
import {dayCode, dayDetail, describeDay} from './describeDay';

const WEEKDAY_LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

/**
 * The day's letter, drawn as generated content like an icon: colour is never the only signal (PRD), and the
 * button's text stays its day number, which its spoken name contains (WCAG 2.5.3).
 */
const codeStyles = stylex.create({
  L: {'::after': {content: '"L"'}},
  H: {'::after': {content: '"H"'}},
  W: {'::after': {content: '"W"'}},
});

const cellStyles = stylex.create({
  cell: {
    appearance: 'none',
    borderStyle: 'solid',
    borderWidth: spacingVars['--spacing-0-5'],
    borderColor: 'transparent',
    backgroundColor: 'transparent',
    color: colorVars['--color-text-primary'],
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 0,
    fontFamily: 'inherit',
    boxSizing: 'border-box',
    outline: {
      default: 'none',
      ':focus-visible': `${focusVars['--focus-outline-width']} ${focusVars['--focus-outline-style']} ${focusVars['--focus-outline-color']}`,
    },
    outlineOffset: focusVars['--focus-outline-offset'],
  },
  // Desktop: 28 px cells, number and letter side by side.
  small: {
    height: spacingVars['--spacing-7'],
    gap: canvasVars.hairline,
    borderRadius: canvasVars.radiusCell,
    fontSize: textSizeVars['--font-size-sm'],
  },
  // Phone: 44 px touch targets, the letter under the number (canvas).
  large: {
    height: spacingVars['--spacing-11'],
    flexDirection: 'column',
    borderRadius: canvasVars.radiusChip,
    fontSize: textSizeVars['--font-size-base'],
    lineHeight: 1.1,
  },
  code: {
    fontSize: textSizeVars['--font-size-xs'],
    fontWeight: fontWeightVars['--font-weight-bold'],
  },
  kind: {fontWeight: fontWeightVars['--font-weight-semibold']},
  inBreak: {borderColor: colorVars['--color-accent']},
  freeWeekend: {borderColor: colorVars['--color-border-emphasized'], borderStyle: 'dashed'},
  past: {color: colorVars['--color-text-secondary'], opacity: 0.75},
  selected: {
    boxShadow: `0 0 0 ${spacingVars['--spacing-0-5']} ${colorVars['--color-text-primary']}`,
  },
  // A month's first day starts in its weekday column, so no empty placeholder cells are needed.
  start1: {gridColumnStart: 1},
  start2: {gridColumnStart: 2},
  start3: {gridColumnStart: 3},
  start4: {gridColumnStart: 4},
  start5: {gridColumnStart: 5},
  start6: {gridColumnStart: 6},
  start7: {gridColumnStart: 7},
});

const COLUMN_START = [
  cellStyles.start1,
  cellStyles.start2,
  cellStyles.start3,
  cellStyles.start4,
  cellStyles.start5,
  cellStyles.start6,
  cellStyles.start7,
];

const styles = stylex.create({
  panel: {
    borderRadius: canvasVars.radiusTile,
    backgroundColor: colorVars['--color-accent-muted'],
    paddingBlock: spacingVars['--spacing-3'],
    paddingInline: spacingVars['--spacing-4'],
  },
  legend: {
    margin: 0,
    padding: 0,
    listStyle: 'none',
  },
});

interface DayButtonProps {
  day: CalendarDay;
  plan: PlanResponse;
  size: 'small' | 'large';
  isFirstOfMonth: boolean;
  isSelected: boolean;
  isTabStop: boolean;
  buttonRef: Ref<HTMLButtonElement>;
  onSelect: () => void;
  onKeyDown?: (event: KeyboardEvent) => void;
}

/** One calendar day: colour, letter and spoken name (raw button: no Astryx component is a calendar cell). */
export function DayButton({
  day,
  plan,
  size,
  isFirstOfMonth,
  isSelected,
  isTabStop,
  buttonRef,
  onSelect,
  onKeyDown,
}: DayButtonProps) {
  const code = dayCode(day);
  return (
    <button
      type="button"
      ref={buttonRef}
      tabIndex={isTabStop ? 0 : -1}
      aria-label={describeDay(day, plan)}
      onClick={onSelect}
      onKeyDown={onKeyDown}
      {...stylex.props(
        cellStyles.cell,
        size === 'small' ? cellStyles.small : cellStyles.large,
        code !== null && kindStyles[code],
        code !== null && cellStyles.kind,
        day.breakIndex !== null && cellStyles.inBreak,
        day.freeLongWeekendIndex !== null && cellStyles.freeWeekend,
        !day.planned && cellStyles.past,
        isSelected && cellStyles.selected,
        isFirstOfMonth && COLUMN_START[weekdayIndex(day.date)],
      )}
    >
      <span aria-hidden="true">{dayOfMonth(day.date)}</span>
      {code !== null && (
        <span aria-hidden="true" {...stylex.props(cellStyles.code, codeStyles[code])} />
      )}
    </button>
  );
}

/** The legend: every colour with its letter and its word (PRD: never colour alone). */
export function Legend({plan}: {plan: PlanResponse}) {
  const items: {code: 'L' | 'H' | 'W' | 'outline' | 'dashed'; label: string}[] = [
    {code: 'L', label: 'Leave'},
    {code: 'H', label: 'Holiday'},
    {code: 'W', label: 'Weekly off'},
    {code: 'outline', label: 'In a break'},
  ];
  if (plan.freeLongWeekends.length > 0 && plan.breaks.length === 0) {
    items.push({code: 'dashed', label: 'Free long weekend'});
  }
  return (
    <HStack as="ul" gap={3} wrap="wrap" xstyle={styles.legend} aria-label="Legend">
      {items.map((item) => (
        <HStack as="li" key={item.label} gap={1.5} vAlign="center">
          <DaySquare code={item.code} size="sm" />
          <Text type="small" color="secondary">
            {item.label}
          </Text>
        </HStack>
      ))}
    </HStack>
  );
}

/** What a tapped day is (canvas panel); announced politely when it changes. */
export function DayPanel({day, plan}: {day: CalendarDay | undefined; plan: PlanResponse}) {
  const detail = day ? dayDetail(day, plan) : null;
  const code = day ? dayCode(day) : null;
  return (
    <HStack gap={3} vAlign="center" xstyle={styles.panel} role="status" aria-live="polite">
      {code && <DaySquare code={code} size="md" />}
      <VStack gap={0.5}>
        <Text weight="bold">
          {detail ? `${detail.heading} · ${detail.kind}` : 'Tap a day to see what it is.'}
        </Text>
        <Text type="small" color="secondary">
          {detail ? detail.detail : 'Arrow keys move between days.'}
        </Text>
      </VStack>
    </HStack>
  );
}

/** Desktop: all 12 months at a glance, with the legend and the tapped-day panel (canvas). */
export function YearCalendar({plan}: {plan: PlanResponse}) {
  const byDate = useMemo(() => new Map(plan.days.map((d) => [d.date, d])), [plan.days]);
  const firstPlanned = plan.days.find((d) => d.planned)?.date ?? plan.days[0]?.date ?? '';
  const [focused, setFocused] = useState<IsoDate>(firstPlanned);
  const [selected, setSelected] = useState<IsoDate | null>(null);
  const buttons = useRef(new Map<IsoDate, HTMLButtonElement>());

  const months = useMemo(() => {
    const grouped: CalendarDay[][] = Array.from({length: 12}, () => []);
    for (const day of plan.days) grouped[monthOf(day.date) - 1]?.push(day);
    return grouped;
  }, [plan.days]);

  const move = (event: KeyboardEvent, from: IsoDate) => {
    const step: Record<string, number> = {ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7};
    let target: IsoDate | undefined;
    if (event.key in step) target = addDays(from, step[event.key] ?? 0);
    if (event.key === 'Home') target = plan.days[0]?.date;
    if (event.key === 'End') target = plan.days[plan.days.length - 1]?.date;
    if (!target || !byDate.has(target)) return;
    event.preventDefault();
    setFocused(target);
    buttons.current.get(target)?.focus();
  };

  // Exactly one day is always reachable with Tab, even if the remembered one isn't in this plan.
  const tabStop = byDate.has(focused) ? focused : firstPlanned;

  return (
    <Card>
      <VStack gap={5}>
        <HStack gap={4} wrap="wrap" vAlign="center" justify="between">
          <Heading level={2} type="card">
            {plan.year} at a glance
          </Heading>
          <Legend plan={plan} />
        </HStack>
        <DayPanel day={selected ? byDate.get(selected) : undefined} plan={plan} />
        <Grid columns={{minWidth: 210}} rowGap={8} columnGap={6}>
          {months.map((days, index) => (
            <VStack key={index} gap={1.5}>
              <Heading level={3} type="month">
                {monthName(index + 1)}
              </Heading>
              <Grid columns={7} gap={0.5}>
                {WEEKDAY_LETTERS.map((letter, i) => (
                  <Text key={`h${i}`} type="supporting" color="secondary" justify="center">
                    <span aria-hidden="true">{letter}</span>
                  </Text>
                ))}
                {days.map((day) => (
                  <DayButton
                    key={day.date}
                    day={day}
                    plan={plan}
                    size="small"
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
            </VStack>
          ))}
        </Grid>
      </VStack>
    </Card>
  );
}
