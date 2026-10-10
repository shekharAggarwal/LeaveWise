import {useState} from 'react';
import * as stylex from '@stylexjs/stylex';
import {canvasVars} from '../../theme/canvas.stylex';
import {Banner} from '@astryxdesign/core/Banner';
import {Button} from '@astryxdesign/core/Button';
import {Card} from '@astryxdesign/core/Card';
import {Icon} from '@astryxdesign/core/Icon';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {Heading, Text} from '@astryxdesign/core/Text';
import {Theme} from '@astryxdesign/core/theme';
import {
  borderVars,
  colorVars,
  radiusVars,
  spacingVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import {downloadCalendar, messageOf} from '../../api/client';
import type {BreakStyleId, PlanRequest, PlanResponse} from '../../api/types';
import {formatRange} from '../../lib/dates';
import {daysOffPerLeave, plural} from '../../lib/figures';
import {DownloadIcon} from '../../lib/icons';
import {leavewiseTheme} from '../../theme/leavewise';

const styles = stylex.create({
  // The canvas's dark summary card: the nested dark Theme supplies its colours (theme dark values = canvas card).
  dark: {
    borderRadius: canvasVars.radiusCard,
    backgroundColor: colorVars['--color-background-surface'],
  },
  stale: {
    opacity: 0.55,
  },
  figures: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
    gap: spacingVars['--spacing-3'],
    margin: 0,
    padding: 0,
    listStyle: 'none',
  },
  figuresPhone: {
    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
    gap: spacingVars['--spacing-2'],
  },
  figure: {
    borderRadius: canvasVars.radiusTile,
    backgroundColor: colorVars['--color-background-muted'],
    padding: spacingVars['--spacing-4'],
  },
  figurePhone: {
    borderRadius: radiusVars['--radius-container'],
    padding: spacingVars['--spacing-3'],
  },
  tintPanel: {
    borderRadius: canvasVars.radiusPanel,
    backgroundColor: colorVars['--color-accent-muted'],
    padding: spacingVars['--spacing-5'],
  },
  headerText: {
    flex: '1 1 320px',
    minWidth: 0,
  },
});

interface SummaryProps {
  plan: PlanResponse;
  request: PlanRequest;
  isStale: boolean;
  isPhone?: boolean;
  shorterStyles: {id: BreakStyleId; label: string}[];
  onTryStyle: (style: BreakStyleId) => void;
  onPlanAgain: () => void;
  /** True while a plan is being made or the holiday list reloads; the Try buttons wait for it. */
  isBusy: boolean;
}

/**
 * The answer first (PRD, Results): how many days off the leaves buy. The heading names the results region, so its
 * id is "results-heading".
 */
export function Summary({
  plan,
  request,
  isStale,
  isPhone = false,
  shorterStyles,
  onTryStyle,
  onPlanAgain,
  isBusy,
}: SummaryProps) {
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const {summary} = plan;
  const noBreak = plan.notes.find((n) => n.code === 'NO_BREAK_POSSIBLE');
  const noDaysLeft = plan.notes.find((n) => n.code === 'NO_DAYS_LEFT');
  const noLeave = plan.notes.find((n) => n.code === 'NO_LEAVE_USED');
  const leftOver = plan.notes.find((n) => n.code === 'LEAVES_LEFT_OVER');
  const eyebrow = (
    <Heading level={2} type="eyebrow" id="results-heading">
      Your {plan.year} plan
    </Heading>
  );

  const download = async () => {
    setDownloadError(null);
    try {
      await downloadCalendar(request);
    } catch (e) {
      setDownloadError(messageOf(e));
    }
  };

  const staleBanner = isStale && (
    <Banner
      status="warning"
      title="Inputs changed. Tap Plan my leaves to update this plan."
      endContent={<Button label="Plan again" variant="outline" onClick={onPlanAgain} />}
    />
  );

  // No break, or nothing left to plan: a light card with the reason and, for no break, a shorter style to try.
  if (noBreak || noDaysLeft) {
    const note = noBreak ?? noDaysLeft;
    return (
      <VStack gap={4}>
        {staleBanner}
        <Card>
          <VStack gap={4}>
            {eyebrow}
            <VStack gap={4} xstyle={styles.tintPanel}>
              <Icon icon="calendar" color="accent" size="lg" />
              <Text weight="semibold">{note?.message}</Text>
              {noBreak && shorterStyles.length > 0 && (
                <HStack gap={2} wrap="wrap">
                  {shorterStyles.map((s, i) => (
                    <Button
                      key={s.id}
                      label={`Try ${s.label}`}
                      variant={i === 0 ? 'primary' : 'outline'}
                      isDisabled={isBusy}
                      onClick={() => onTryStyle(s.id)}
                    />
                  ))}
                </HStack>
              )}
            </VStack>
            {summary.leavesUnused > 0 && (
              <Text type="small" color="secondary">
                {plural(summary.leavesUnused, 'leave', 'leaves')} unused.
              </Text>
            )}
          </VStack>
        </Card>
      </VStack>
    );
  }

  const headline = noLeave
    ? plan.freeLongWeekends.length > 0
      ? plural(plan.freeLongWeekends.length, 'free long weekend', 'free long weekends')
      : 'No free long weekends'
    : `${plural(summary.leavesUsed, 'leave', 'leaves')} → ${summary.totalDaysOff} days off`;
  const sub = noLeave
    ? noLeave.message
    : `across ${plural(summary.breakCount, 'break', 'breaks')}. ${
        // The LEAVES_LEFT_OVER note below already says how many are left over.
        leftOver
          ? ''
          : summary.leavesUnused > 0
            ? `${plural(summary.leavesUnused, 'leave', 'leaves')} unused.`
            : 'Every leave is used.'
      }`.trimEnd();

  const figures: {label: string; value: string; of?: string}[] = isPhone
    ? [
        {label: 'Breaks', value: String(summary.breakCount)},
        {
          label: 'Days off per leave',
          value: daysOffPerLeave(summary.totalDaysOff, summary.leavesUsed),
        },
      ]
    : [
        {
          label: 'Leaves used',
          value: String(summary.leavesUsed),
          of: ` of ${summary.leavesAvailable}`,
        },
        {label: 'Days off in breaks', value: String(summary.totalDaysOff)},
        {label: 'Breaks', value: String(summary.breakCount)},
        {
          label: 'Days off per leave',
          value: daysOffPerLeave(summary.totalDaysOff, summary.leavesUsed),
        },
      ];

  const downloadButton = plan.breaks.length > 0 && (
    <Button
      label="Add to calendar (.ics)"
      variant="primary"
      size={isPhone ? 'lg' : 'md'}
      width={isPhone ? '100%' : undefined}
      icon={<Icon icon={DownloadIcon} />}
      clickAction={download}
    />
  );

  return (
    <VStack gap={4}>
      {staleBanner}
      <Theme theme={leavewiseTheme} mode="dark">
        <VStack
          gap={isPhone ? 5 : 6}
          padding={isPhone ? 5 : 8}
          xstyle={[styles.dark, isStale && styles.stale]}
        >
          <HStack gap={4} wrap="wrap" vAlign="start" justify="between">
            <VStack gap={2} xstyle={styles.headerText}>
              {eyebrow}
              <Heading level={3} type={isPhone ? 'hero-phone' : 'hero'}>
                {headline}
              </Heading>
              <Text type="lead" color="secondary">
                {sub}
              </Text>
              {leftOver && <Text color="secondary">{leftOver.message}</Text>}
            </VStack>
            {!isPhone && downloadButton}
          </HStack>
          {summary.breakCount > 0 && (
            <ul
              aria-label="Plan figures"
              {...stylex.props(styles.figures, isPhone && styles.figuresPhone)}
            >
              {figures.map((f) => (
                <li key={f.label} {...stylex.props(styles.figure, isPhone && styles.figurePhone)}>
                  <VStack gap={1}>
                    <Text type="small" color="secondary">
                      {f.label}
                    </Text>
                    <Text type="figure" hasTabularNumbers>
                      {f.value}
                      {f.of && (
                        <Text type="lead" color="secondary" weight="semibold">
                          {f.of}
                        </Text>
                      )}
                    </Text>
                  </VStack>
                </li>
              ))}
            </ul>
          )}
          {isPhone && downloadButton}
          {downloadError && <Banner status="error" title={downloadError} />}
        </VStack>
      </Theme>
      {noLeave && plan.freeLongWeekends.length > 0 && (
        <Card>
          <VStack gap={2}>
            <Heading level={3} type="card" id="free-weekends-heading">
              Free long weekends
            </Heading>
            <ul aria-labelledby="free-weekends-heading" {...stylex.props(rowListStyles.list)}>
              {plan.freeLongWeekends.map((w) => (
                <li key={w.start} {...stylex.props(rowListStyles.row)}>
                  <HStack gap={3} justify="between" vAlign="center">
                    <VStack gap={0.5}>
                      <Text weight="semibold">{formatRange(w.start, w.end, plan.year)}</Text>
                      <Text type="small" color="secondary">
                        {w.holidays.length > 0
                          ? w.holidays.map((h) => h.names.join(' / ')).join('; ')
                          : 'Weekly offs'}
                      </Text>
                    </VStack>
                    <Text type="ratio">{w.days} days</Text>
                  </HStack>
                </li>
              ))}
            </ul>
          </VStack>
        </Card>
      )}
    </VStack>
  );
}

const rowListStyles = stylex.create({
  list: {
    margin: 0,
    padding: 0,
    listStyle: 'none',
  },
  row: {
    paddingBlock: spacingVars['--spacing-3'],
    borderTopWidth: borderVars['--border-width'],
    borderTopStyle: 'solid',
    borderTopColor: colorVars['--color-border'],
  },
});
