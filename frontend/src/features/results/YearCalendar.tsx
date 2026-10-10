import {useMemo, useRef, useState, type KeyboardEvent} from 'react';
import * as stylex from '@stylexjs/stylex';
import {Grid} from '@astryxdesign/core/Grid';
import {Heading, Text} from '@astryxdesign/core/Text';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {
  colorVars,
  focusVars,
  fontWeightVars,
  radiusVars,
  sizeVars,
  spacingVars,
  textSizeVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import type {CalendarDay, IsoDate, PlanResponse} from '../../api/types';
import {dayOfMonth, monthName, monthOf, weekdayIndex} from '../../lib/dates';
import {describeDay} from './describeDay';

const WEEKDAY_LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

/** Short visible code per kind; colour is never the only signal (PRD, Year calendar). */
const CODE: Record<CalendarDay['kind'], string> = {
  LEAVE: 'L',
  HOLIDAY: 'H',
  WEEKLY_OFF: 'W',
  WORKING: '',
};

const styles = stylex.create({
  cell: {
    appearance: 'none',
    borderStyle: 'solid',
    borderWidth: spacingVars['--spacing-0-5'],
    borderColor: 'transparent',
    borderRadius: radiusVars['--radius-inner'],
    backgroundColor: 'transparent',
    color: colorVars['--color-text-primary'],
    cursor: 'pointer',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: sizeVars['--size-element-lg'],
    padding: 0,
    fontFamily: 'inherit',
    fontSize: textSizeVars['--font-size-sm'],
    outline: {
      default: 'none',
      ':focus-visible': `${focusVars['--focus-outline-width']} ${focusVars['--focus-outline-style']} ${focusVars['--focus-outline-color']}`,
    },
    outlineOffset: focusVars['--focus-outline-offset'],
  },
  leave: {
    backgroundColor: colorVars['--color-accent'],
    color: colorVars['--color-on-accent'],
  },
  holiday: {
    backgroundColor: colorVars['--color-background-orange'],
    color: colorVars['--color-text-orange'],
  },
  weeklyOff: {
    backgroundColor: colorVars['--color-background-muted'],
    color: colorVars['--color-text-secondary'],
  },
  inBreak: {
    borderColor: colorVars['--color-accent'],
  },
  freeWeekend: {
    borderColor: colorVars['--color-border-emphasized'],
    borderStyle: 'dashed',
  },
  past: {
    // Readable (secondary, not disabled): these days are still focusable and describable.
    color: colorVars['--color-text-secondary'],
    backgroundColor: colorVars['--color-background-body'],
  },
  selected: {
    boxShadow: `0 0 0 ${spacingVars['--spacing-0-5']} ${colorVars['--color-text-primary']}`,
  },
  code: {
    fontSize: textSizeVars['--font-size-sm'],
    fontWeight: fontWeightVars['--font-weight-semibold'],
  },
  legendSwatch: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: sizeVars['--size-element-sm'],
    height: sizeVars['--size-element-sm'],
    borderRadius: radiusVars['--radius-inner'],
    borderStyle: 'solid',
    borderWidth: spacingVars['--spacing-0-5'],
    borderColor: 'transparent',
    fontSize: textSizeVars['--font-size-sm'],
    fontWeight: fontWeightVars['--font-weight-semibold'],
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
  styles.start1,
  styles.start2,
  styles.start3,
  styles.start4,
  styles.start5,
  styles.start6,
  styles.start7,
];

function addDays(date: IsoDate, n: number): IsoDate {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y ?? 1970, (m ?? 1) - 1, (d ?? 1) + n)).toISOString().slice(0, 10);
}

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
    const step: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      ArrowUp: -7,
      ArrowDown: 7,
    };
    let target: IsoDate | undefined;
    if (event.key in step) target = addDays(from, step[event.key] ?? 0);
    if (event.key === 'Home') target = plan.days[0]?.date;
    if (event.key === 'End') target = plan.days[plan.days.length - 1]?.date;
    if (!target || !byDate.has(target)) return;
    event.preventDefault();
    setFocused(target);
    buttons.current.get(target)?.focus();
  };

  const selectedDay = selected ? byDate.get(selected) : undefined;
  // Exactly one day is always reachable with Tab, even if the remembered one isn't in this plan.
  const tabStop = byDate.has(focused) ? focused : firstPlanned;

  return (
    <VStack gap={4}>
      <VStack gap={2}>
        <Heading level={3}>{plan.year} calendar</Heading>
        <HStack gap={3} wrap="wrap">
          <Legend code="L" label="Leave day" xstyle={styles.leave} />
          <Legend code="H" label="Holiday" xstyle={styles.holiday} />
          <Legend code="W" label="Weekly off" xstyle={styles.weeklyOff} />
          <Legend code="" label="Break (outlined)" xstyle={styles.inBreak} />
          <Legend code="" label="Free long weekend (dashed)" xstyle={styles.freeWeekend} />
        </HStack>
        <Text type="supporting" color="secondary">
          Tap a day to see what it is. Arrow keys move between days.
        </Text>
        <p role="status" aria-live="polite">
          <Text type="body" weight="medium">
            {selectedDay ? describeDay(selectedDay, plan) : ''}
          </Text>
        </p>
      </VStack>
      <Grid columns={{minWidth: 230}} gap={5}>
        {months.map((days, index) => (
          <VStack key={index} gap={1}>
            <Heading level={4}>{monthName(index + 1)}</Heading>
            <Grid columns={7} gap={0.5}>
              {WEEKDAY_LETTERS.map((letter, i) => (
                <Text key={`h${i}`} type="supporting" color="secondary" justify="center">
                  <span aria-hidden="true">{letter}</span>
                </Text>
              ))}
              {days.map((day) => {
                const label = describeDay(day, plan);
                return (
                  <button
                    key={day.date}
                    type="button"
                    ref={(el) => {
                      if (el) buttons.current.set(day.date, el);
                      else buttons.current.delete(day.date);
                    }}
                    tabIndex={day.date === tabStop ? 0 : -1}
                    aria-label={label}
                    onClick={() => {
                      setSelected(day.date);
                      setFocused(day.date);
                    }}
                    onKeyDown={(e) => move(e, day.date)}
                    {...stylex.props(
                      styles.cell,
                      day.kind === 'LEAVE' && styles.leave,
                      day.kind === 'HOLIDAY' && styles.holiday,
                      day.kind === 'WEEKLY_OFF' && styles.weeklyOff,
                      day.breakIndex !== null && styles.inBreak,
                      day.freeLongWeekendIndex !== null && styles.freeWeekend,
                      !day.planned && styles.past,
                      day.date === selected && styles.selected,
                      day === days[0] && COLUMN_START[weekdayIndex(day.date)],
                    )}
                  >
                    <span aria-hidden="true">{dayOfMonth(day.date)}</span>
                    {CODE[day.kind] && (
                      <span aria-hidden="true" {...stylex.props(styles.code)}>
                        {CODE[day.kind]}
                      </span>
                    )}
                  </button>
                );
              })}
            </Grid>
          </VStack>
        ))}
      </Grid>
    </VStack>
  );
}

function Legend({code, label, xstyle}: {code: string; label: string; xstyle: stylex.StyleXStyles}) {
  return (
    <HStack gap={1} vAlign="center">
      <span aria-hidden="true" {...stylex.props(styles.legendSwatch, xstyle)}>
        {code}
      </span>
      <Text type="supporting">{label}</Text>
    </HStack>
  );
}
