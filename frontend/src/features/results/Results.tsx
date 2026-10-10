import {useEffect, useRef, useState} from 'react';
import * as stylex from '@stylexjs/stylex';
import {Banner} from '@astryxdesign/core/Banner';
import {Button} from '@astryxdesign/core/Button';
import {Card} from '@astryxdesign/core/Card';
import {Icon} from '@astryxdesign/core/Icon';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {List, ListItem} from '@astryxdesign/core/List';
import {Section} from '@astryxdesign/core/Section';
import {Heading, Text} from '@astryxdesign/core/Text';
import {Theme} from '@astryxdesign/core/theme';
import {leavewiseTheme} from '../../theme/leavewise';
import {
  borderVars,
  colorVars,
  fontWeightVars,
  radiusVars,
  sizeVars,
  spacingVars,
  textSizeVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import {downloadCalendar, messageOf} from '../../api/client';
import type {
  BreakStyleId,
  HolidayDay,
  IsoDate,
  LeaveBreak,
  PlanRequest,
  PlanResponse,
} from '../../api/types';
import {formatDay, formatRange} from '../../lib/dates';
import {daysOffPerLeave} from '../../lib/figures';
import {YearCalendar} from './YearCalendar';

const styles = stylex.create({
  // The summary is the one dark surface (design: dark card) in light and dark mode alike. The nested dark Theme sets
  // the tokens; the card paints that theme's surface, with a border so it still stands apart on a dark page.
  summary: {
    borderRadius: radiusVars['--radius-page'],
    backgroundColor: colorVars['--color-background-surface'],
    borderStyle: 'solid',
    borderWidth: borderVars['--border-width'],
    borderColor: colorVars['--color-border'],
  },
  figures: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
    gap: spacingVars['--spacing-3'],
    margin: 0,
    padding: 0,
    listStyle: 'none',
  },
  figure: {
    borderRadius: radiusVars['--radius-container'],
    backgroundColor: colorVars['--color-neutral'],
    padding: spacingVars['--spacing-4'],
  },
  number: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    width: sizeVars['--size-element-md'],
    height: sizeVars['--size-element-md'],
    borderRadius: radiusVars['--radius-full'],
    backgroundColor: colorVars['--color-accent-muted'],
    color: colorVars['--color-text-accent'],
    fontSize: textSizeVars['--font-size-sm'],
    fontWeight: fontWeightVars['--font-weight-bold'],
  },
  strip: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: spacingVars['--spacing-1'],
  },
  square: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: sizeVars['--size-element-sm'],
    height: sizeVars['--size-element-sm'],
    borderRadius: radiusVars['--radius-inner'],
    fontSize: textSizeVars['--font-size-xs'],
    fontWeight: fontWeightVars['--font-weight-bold'],
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
});

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

function holidayText(holidays: HolidayDay[]): string {
  return holidays.map((h) => h.names.join(' / ')).join('; ');
}

function addDays(date: IsoDate, n: number): IsoDate {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y ?? 1970, (m ?? 1) - 1, (d ?? 1) + n)).toISOString().slice(0, 10);
}

type DayCode = 'L' | 'H' | 'W';

/**
 * Each day of a break as a letter: a break holds only days off, so a day that is neither a leave nor a holiday
 * is a weekly off. Built from the break itself, so days in the next or previous year are included.
 */
function breakDays(b: LeaveBreak): DayCode[] {
  const leaves = new Set(b.leaveDates);
  const holidays = new Set(b.holidays.map((h) => h.date));
  const codes: DayCode[] = [];
  for (let day = b.start; day <= b.end; day = addDays(day, 1)) {
    codes.push(leaves.has(day) ? 'L' : holidays.has(day) ? 'H' : 'W');
  }
  return codes;
}

const SQUARE_STYLE = {L: styles.leave, H: styles.holiday, W: styles.weeklyOff};

/**
 * The break drawn as day squares; decorative (aria-hidden), since the row's text says the same. Raw spans like the
 * calendar's day cells: no Astryx component draws a fixed-size coloured square with a letter.
 */
function DayStrip({codes}: {codes: DayCode[]}) {
  return (
    <span aria-hidden="true" {...stylex.props(styles.strip)}>
      {codes.map((code, i) => (
        <span key={i} {...stylex.props(styles.square, SQUARE_STYLE[code])}>
          {code}
        </span>
      ))}
    </span>
  );
}

/** List rows don't wrap by default; long holiday names must wrap on a 360 px phone (PRD). */
function Wrapping({children}: {children: string}) {
  return (
    <Text type="supporting" color="secondary" textWrap="wrap">
      {children}
    </Text>
  );
}

interface Props {
  plan: PlanResponse;
  request: PlanRequest;
  /** True when an input changed after this plan was made. */
  isStale: boolean;
  /** Break styles shorter than the planned one, offered when no break fits. */
  shorterStyles?: {id: BreakStyleId; label: string}[];
  onTryStyle?: (style: BreakStyleId) => void;
  /** True while a plan is being made or the holiday list reloads; the Try buttons wait for it. */
  isBusy?: boolean;
}

/** Mounted once per plan (keyed by the parent), so it takes focus each time a plan arrives. */
export function Results({
  plan,
  request,
  isStale,
  shorterStyles = [],
  onTryStyle,
  isBusy = false,
}: Props) {
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const {summary} = plan;
  const noLeaveUsed = plan.notes.some((n) => n.code === 'NO_LEAVE_USED');
  const region = useRef<HTMLElement | null>(null);

  useEffect(() => {
    region.current?.focus();
  }, []);

  const download = async () => {
    setDownloadError(null);
    try {
      await downloadCalendar(request);
    } catch (e) {
      setDownloadError(messageOf(e));
    }
  };

  const figures: [string, string][] = [
    ['Leaves used', `${summary.leavesUsed} of ${summary.leavesAvailable}`],
    ['Days off in breaks', String(summary.totalDaysOff)],
    ['Breaks', String(summary.breakCount)],
    // Shown only when there is a break, so at least one leave is used.
    ['Days off per leave', daysOffPerLeave(summary.totalDaysOff, summary.leavesUsed)],
  ];

  return (
    <section aria-labelledby="results-heading" tabIndex={-1} ref={region}>
      <VStack gap={6}>
        {isStale && (
          <Banner
            status="warning"
            title="Inputs changed. Tap Plan my leaves to update this plan."
          />
        )}

        <Theme theme={leavewiseTheme} mode="dark">
          <Section variant="transparent" padding={6} xstyle={styles.summary}>
            <VStack gap={5}>
              <VStack gap={2}>
                <Heading level={2} id="results-heading" color="secondary">
                  Your plan
                </Heading>
                {summary.breakCount > 0 && (
                  <Text type="display-2" weight="bold" textWrap="balance">
                    {plural(summary.leavesUsed, 'leave', 'leaves')} → {summary.totalDaysOff} days
                    off across {plural(summary.breakCount, 'break', 'breaks')}
                    {summary.leavesUnused > 0
                      ? `, ${plural(summary.leavesUnused, 'leave', 'leaves')} unused`
                      : ''}
                  </Text>
                )}
              </VStack>
              {summary.breakCount > 0 && (
                <ul aria-label="Plan figures" {...stylex.props(styles.figures)}>
                  {figures.map(([label, value]) => (
                    <li key={label} {...stylex.props(styles.figure)}>
                      <VStack gap={1}>
                        <Text type="supporting" color="secondary">
                          {label}
                        </Text>
                        <Text type="large" size="2xl" weight="bold" hasTabularNumbers>
                          {value}
                        </Text>
                      </VStack>
                    </li>
                  ))}
                </ul>
              )}
              {plan.notes.map((note) => (
                <Banner
                  key={note.code}
                  status="info"
                  title={note.message}
                  endContent={
                    note.code === 'NO_BREAK_POSSIBLE' && onTryStyle && shorterStyles.length > 0 ? (
                      <HStack gap={2} wrap="wrap">
                        {shorterStyles.map((s) => (
                          <Button
                            key={s.id}
                            label={`Try ${s.label}`}
                            variant="secondary"
                            isDisabled={isBusy}
                            onClick={() => onTryStyle?.(s.id)}
                          />
                        ))}
                      </HStack>
                    ) : undefined
                  }
                />
              ))}
              {plan.breaks.length > 0 && (
                <VStack gap={2}>
                  <HStack wrap="wrap">
                    <Button
                      label="Download calendar file (.ics)"
                      variant="secondary"
                      icon={<Icon icon="calendar" />}
                      clickAction={download}
                    />
                  </HStack>
                  <Text type="supporting" color="secondary">
                    One all-day event per break, with its leave dates in the notes.
                  </Text>
                  {downloadError && <Banner status="error" title={downloadError} />}
                </VStack>
              )}
            </VStack>
          </Section>
        </Theme>

        {plan.breaks.length > 0 && (
          <Card padding={6}>
            <List header={<Heading level={3}>Breaks</Heading>} hasDividers density="balanced">
              {plan.breaks.map((b, i) => (
                <ListItem
                  key={b.start}
                  startContent={<span {...stylex.props(styles.number)}>{i + 1}</span>}
                  label={`${formatRange(b.start, b.end, plan.year)} · ${b.days} days off`}
                  description={
                    <VStack gap={2}>
                      <Wrapping>
                        {[
                          `Leave: ${b.leaveDates.map((d) => formatDay(d, plan.year)).join('; ')}`,
                          b.holidays.length > 0 ? `Holidays: ${holidayText(b.holidays)}` : null,
                          `${b.daysOffPerLeave.toFixed(1)} days off per leave`,
                        ]
                          .filter(Boolean)
                          .join(' · ')}
                      </Wrapping>
                      <DayStrip codes={breakDays(b)} />
                    </VStack>
                  }
                />
              ))}
            </List>
          </Card>
        )}

        {noLeaveUsed && plan.freeLongWeekends.length > 0 && (
          <Card padding={6}>
            <List header={<Heading level={3}>Free long weekends</Heading>} hasDividers>
              {plan.freeLongWeekends.map((w) => (
                <ListItem
                  key={w.start}
                  label={`${formatRange(w.start, w.end, plan.year)} · ${w.days} days off`}
                  description={
                    <Wrapping>
                      {w.holidays.length > 0
                        ? `Holidays: ${holidayText(w.holidays)}`
                        : 'Weekly offs'}
                    </Wrapping>
                  }
                />
              ))}
            </List>
          </Card>
        )}

        <Card padding={6}>
          <YearCalendar plan={plan} />
        </Card>
      </VStack>
    </section>
  );
}
