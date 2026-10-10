import {useEffect, useRef} from 'react';
import * as stylex from '@stylexjs/stylex';
import {Card} from '@astryxdesign/core/Card';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {Heading, Text} from '@astryxdesign/core/Text';
import {
  borderVars,
  colorVars,
  fontWeightVars,
  radiusVars,
  spacingVars,
  textSizeVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import type {BreakStyleId, PlanRequest, PlanResponse} from '../../api/types';
import {formatRange} from '../../lib/dates';
import {holidayNames, leaveList} from './format';
import {breakDays} from '../shared/dayCodes';
import {DayStrip} from '../shared/days';
import {Summary} from './Summary';
import {YearCalendar} from './YearCalendar';

const styles = stylex.create({
  // Canvas: the table scrolls sideways in its own box below 680 px instead of squeezing.
  scroller: {
    overflowX: 'auto',
  },
  table: {
    margin: 0,
    padding: 0,
    listStyle: 'none',
    minWidth: '680px',
  },
  row: {
    display: 'grid',
    gridTemplateColumns: '36px 1.3fr 1.6fr 1fr 80px',
    gap: spacingVars['--spacing-4'],
    alignItems: 'center',
    paddingBlock: spacingVars['--spacing-3'],
    borderTopWidth: borderVars['--border-width'],
    borderTopStyle: 'solid',
    borderTopColor: colorVars['--color-border'],
  },
  number: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: spacingVars['--spacing-8'],
    height: spacingVars['--spacing-8'],
    borderRadius: radiusVars['--radius-full'],
    backgroundColor: colorVars['--color-accent-muted'],
    color: colorVars['--color-text-accent'],
    fontSize: textSizeVars['--font-size-sm'],
    fontWeight: fontWeightVars['--font-weight-bold'],
  },
  ratio: {
    textAlign: 'end',
  },
});

interface Props {
  plan: PlanResponse;
  request: PlanRequest;
  /** True when an input changed after this plan was made. */
  isStale: boolean;
  /** Break styles shorter than the planned one, offered when no break fits. */
  shorterStyles?: {id: BreakStyleId; label: string}[];
  onTryStyle?: (style: BreakStyleId) => void;
  onPlanAgain?: () => void;
  /** True while a plan is being made or the holiday list reloads; the Try buttons wait for it. */
  isBusy?: boolean;
}

/**
 * Desktop results (canvas): summary card, breaks table, the year at a glance. Mounted once per plan (keyed by the
 * parent), so it takes focus each time a plan arrives.
 */
export function Results({
  plan,
  request,
  isStale,
  shorterStyles = [],
  onTryStyle,
  onPlanAgain = () => undefined,
  isBusy = false,
}: Props) {
  const region = useRef<HTMLElement | null>(null);

  useEffect(() => {
    region.current?.focus();
  }, []);

  return (
    <section aria-labelledby="results-heading" tabIndex={-1} ref={region}>
      <VStack gap={6}>
        <Summary
          plan={plan}
          request={request}
          isStale={isStale}
          shorterStyles={onTryStyle ? shorterStyles : []}
          onTryStyle={onTryStyle ?? (() => undefined)}
          onPlanAgain={onPlanAgain}
          isBusy={isBusy}
        />
        {plan.breaks.length > 0 && <BreaksTable plan={plan} />}
        <YearCalendar plan={plan} />
      </VStack>
    </section>
  );
}

function BreaksTable({plan}: {plan: PlanResponse}) {
  return (
    <Card>
      <VStack gap={4}>
        <HStack gap={3} wrap="wrap" vAlign="end" justify="between">
          <Heading level={2} type="card" id="breaks-heading">
            Your breaks
          </Heading>
          <Text color="secondary">In date order</Text>
        </HStack>
        <VStack xstyle={styles.scroller}>
          <ol aria-labelledby="breaks-heading" {...stylex.props(styles.table)}>
            {plan.breaks.map((b, i) => (
              <li key={b.start} {...stylex.props(styles.row)}>
                <span {...stylex.props(styles.number)}>{i + 1}</span>
                <VStack gap={0.5}>
                  <Text weight="semibold">{formatRange(b.start, b.end, plan.year)}</Text>
                  <Text type="small" color="secondary">
                    {b.days} days off · leave {leaveList(b, plan.year)}
                  </Text>
                </VStack>
                <DayStrip codes={breakDays(b)} />
                <Text type="small" color="secondary">
                  {b.holidays.length > 0 ? holidayNames(b.holidays) : '—'}
                </Text>
                <VStack gap={0} xstyle={styles.ratio}>
                  <Text type="ratio" hasTabularNumbers>
                    {b.daysOffPerLeave.toFixed(1)}
                  </Text>
                  <Text type="supporting" color="secondary">
                    per leave
                  </Text>
                </VStack>
              </li>
            ))}
          </ol>
        </VStack>
      </VStack>
    </Card>
  );
}
