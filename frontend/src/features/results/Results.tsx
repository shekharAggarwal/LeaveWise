import {useState} from 'react';
import {Banner} from '@astryxdesign/core/Banner';
import {Button} from '@astryxdesign/core/Button';
import {Divider} from '@astryxdesign/core/Divider';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {List, ListItem} from '@astryxdesign/core/List';
import {Heading, Text} from '@astryxdesign/core/Text';
import {ApiError, downloadCalendar} from '../../api/client';
import type {HolidayDay, PlanRequest, PlanResponse} from '../../api/types';
import {formatDay, formatRange} from '../../lib/dates';
import {YearCalendar} from './YearCalendar';

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

function holidayText(holidays: HolidayDay[]): string {
  return holidays.map((h) => h.names.join(' / ')).join('; ');
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
}

export function Results({plan, request}: Props) {
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const {summary} = plan;
  const noLeaveUsed = plan.notes.some((n) => n.code === 'NO_LEAVE_USED');

  const download = async () => {
    setDownloadError(null);
    try {
      await downloadCalendar(request);
    } catch (e) {
      setDownloadError(e instanceof ApiError ? e.message : String(e));
    }
  };

  return (
    <section aria-labelledby="results-heading">
      <VStack gap={6}>
        <VStack gap={2}>
          <Heading level={2} id="results-heading">
            Your plan
          </Heading>
          {summary.breakCount > 0 && (
            <VStack gap={1}>
              <Text type="display-2" weight="bold">
                {plural(summary.leavesUsed, 'leave', 'leaves')} → {summary.totalDaysOff} days off
              </Text>
              <Text type="large" color="secondary">
                across {plural(summary.breakCount, 'break', 'breaks')}
                {summary.leavesUnused > 0
                  ? `, ${plural(summary.leavesUnused, 'leave', 'leaves')} unused`
                  : ''}
              </Text>
            </VStack>
          )}
          {plan.notes.map((note) => (
            <Banner key={note.code} status="info" title={note.message} />
          ))}
        </VStack>

        {plan.breaks.length > 0 && (
          <List header={<Heading level={3}>Breaks</Heading>} hasDividers density="balanced">
            {plan.breaks.map((b) => (
              <ListItem
                key={b.start}
                label={`${formatRange(b.start, b.end, plan.year)} · ${b.days} days off`}
                description={
                  <Wrapping>
                    {[
                      `Leave: ${b.leaveDates.map((d) => formatDay(d, plan.year)).join('; ')}`,
                      b.holidays.length > 0 ? `Holidays: ${holidayText(b.holidays)}` : null,
                      `${b.daysOffPerLeave.toFixed(1)} days off per leave`,
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </Wrapping>
                }
              />
            ))}
          </List>
        )}

        {noLeaveUsed && plan.freeLongWeekends.length > 0 && (
          <List header={<Heading level={3}>Free long weekends</Heading>} hasDividers>
            {plan.freeLongWeekends.map((w) => (
              <ListItem
                key={w.start}
                label={`${formatRange(w.start, w.end, plan.year)} · ${w.days} days off`}
                description={
                  <Wrapping>
                    {w.holidays.length > 0 ? `Holidays: ${holidayText(w.holidays)}` : 'Weekly offs'}
                  </Wrapping>
                }
              />
            ))}
          </List>
        )}

        {plan.breaks.length > 0 && (
          <VStack gap={2}>
            <HStack>
              <Button
                label="Download calendar file (.ics)"
                variant="secondary"
                clickAction={download}
              />
            </HStack>
            <Text type="supporting" color="secondary">
              One all-day event per break, with its leave dates in the notes.
            </Text>
            {downloadError && <Banner status="error" title={downloadError} />}
          </VStack>
        )}

        <Divider />
        <YearCalendar plan={plan} />
      </VStack>
    </section>
  );
}
