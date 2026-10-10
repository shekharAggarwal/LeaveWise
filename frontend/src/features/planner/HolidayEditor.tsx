import {useEffect, useRef, useState} from 'react';
import * as stylex from '@stylexjs/stylex';
import {Badge} from '@astryxdesign/core/Badge';
import {Banner} from '@astryxdesign/core/Banner';
import {Button} from '@astryxdesign/core/Button';
import {CheckboxList, CheckboxListItem} from '@astryxdesign/core/CheckboxList';
import {DateInput} from '@astryxdesign/core/DateInput';
import {Grid} from '@astryxdesign/core/Grid';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {Link} from '@astryxdesign/core/Link';
import {SegmentedControl, SegmentedControlItem} from '@astryxdesign/core/SegmentedControl';
import {Skeleton} from '@astryxdesign/core/Skeleton';
import {Text} from '@astryxdesign/core/Text';
import {TextArea} from '@astryxdesign/core/TextArea';
import {TextInput} from '@astryxdesign/core/TextInput';
import {
  borderVars,
  colorVars,
  radiusVars,
  spacingVars,
} from '@astryxdesign/core/theme/tokens.stylex';
import {messageOf, parseHolidays} from '../../api/client';
import type {HolidaysResponse, IsoDate} from '../../api/types';
import {formatDay} from '../../lib/dates';
import {hasControlCharacter} from '../../lib/validation';
import {DaySquare} from '../shared/days';
import {countOn, type HolidayRow} from './holidayRows';

const MAX_NAME_LENGTH = 80;
const CONTROL_CHARACTER_MESSAGE = "Holiday names can't contain line breaks or control characters.";

/** Astryx DateInput's date type (not exported publicly): YYYY-MM-DD as a template literal. */
type StrictIsoDate = `${number}${number}${number}${number}-${number}${number}-${number}${number}`;
const strictDate = (date: string) => date as StrictIsoDate;

export type HolidayLoad =
  | {state: 'loading'}
  | {state: 'error'; message: string}
  | {state: 'ready'; value: HolidaysResponse};

export type AddMode = 'one' | 'paste';

const styles = stylex.create({
  list: {
    borderWidth: borderVars['--border-width'],
    borderStyle: 'solid',
    borderColor: colorVars['--color-border'],
    borderRadius: radiusVars['--radius-container'],
    paddingInline: spacingVars['--spacing-3'],
  },
  // Canvas "No bundled holiday list" and paste-result boxes: a marigold-tinted panel.
  marigoldPanel: {
    borderRadius: radiusVars['--radius-container'],
    borderWidth: borderVars['--border-width'],
    borderStyle: 'solid',
    borderColor: colorVars['--color-border-orange'],
    backgroundColor: colorVars['--color-warning-muted'],
    padding: spacingVars['--spacing-4'],
  },
});

/** "6 of 6 on" plus where the list came from, and the PRD reminder to check the company's list. */
export function HolidaySource({
  load,
  rows,
  ticked,
  showCount = false,
}: {
  load: HolidayLoad;
  rows: HolidayRow[];
  ticked: Set<IsoDate>;
  /** The phone sheet repeats "6 of 6 on" here; on desktop it is in the Holidays header. */
  showCount?: boolean;
}) {
  const source = load.state === 'ready' ? load.value : null;
  if (!source) return null;
  return (
    <Text type="supporting" color="secondary">
      {source.available && source.source ? (
        <>
          Preloaded from{' '}
          <Link href={source.source.url} isExternalLink type="supporting">
            {source.source.title}
          </Link>{' '}
          ({source.source.publisher}).{' '}
        </>
      ) : null}
      {showCount && rows.length > 0 ? `${countOn(rows, ticked)}. ` : ''}
      {source.reminder}
    </Text>
  );
}

/** The ticked list (bundled holidays plus the user's own), or its loading and error states. */
export function HolidayList({
  year,
  load,
  rows,
  ticked,
  onTickedChange,
  onRetry,
}: {
  year: number;
  load: HolidayLoad;
  rows: HolidayRow[];
  ticked: Set<IsoDate>;
  onTickedChange: (ticked: Set<IsoDate>) => void;
  onRetry: () => void;
}) {
  if (load.state === 'loading') {
    return (
      <VStack gap={2} aria-label="Loading the holiday list">
        <Skeleton width="100%" height={40} />
        <Skeleton width="100%" height={40} index={1} />
      </VStack>
    );
  }
  if (load.state === 'error') {
    return (
      <Banner
        status="error"
        title={load.message}
        description="Planning needs the holiday list. Try again, or check your connection."
        endContent={
          <Button label="Load the holiday list again" variant="secondary" onClick={onRetry} />
        }
      />
    );
  }
  if (rows.length === 0) return null;
  return (
    <VStack xstyle={styles.list}>
      <CheckboxList
        label={`Holidays in ${year}`}
        isLabelHidden
        hasDividers
        density="compact"
        value={rows.filter((r) => ticked.has(r.date)).map((r) => r.date)}
        onChange={(values) => onTickedChange(new Set(values))}
      >
        {rows.map((row) => (
          <CheckboxListItem
            key={row.date}
            value={row.date}
            label={
              <Text textWrap="wrap" weight="semibold">
                {row.names.join(' / ')}
              </Text>
            }
            description={formatDay(row.date)}
            endContent={
              row.added ? (
                <Badge variant="neutral" label="Added" />
              ) : row.optional ? (
                <Badge variant="neutral" label="Floater" />
              ) : undefined
            }
          />
        ))}
      </CheckboxList>
    </VStack>
  );
}

/** Canvas state "No bundled holiday list": the year has no official list yet. */
export function NoListPanel({year, onStart}: {year: number; onStart: (mode: AddMode) => void}) {
  return (
    <VStack gap={3} xstyle={styles.marigoldPanel}>
      <DaySquare code="H" size="lg" />
      <VStack gap={1}>
        <Text weight="bold">No official list for {year} is bundled yet.</Text>
        <Text type="supporting" color="secondary">
          Add your company&apos;s holidays below, one by one or as a pasted list.
        </Text>
      </VStack>
      <HStack gap={2} wrap="wrap">
        <Button label="Add one" variant="outline" onClick={() => onStart('one')} />
        <Button label="Paste a list" variant="outline" onClick={() => onStart('paste')} />
      </HStack>
    </VStack>
  );
}

interface AdderProps {
  year: number;
  mode: AddMode;
  onModeChange: (mode: AddMode) => void;
  /** Adds holidays to the latest rows (functional update in the parent) and ticks their dates. */
  onAdd: (holidays: {date: IsoDate; name: string}[], year: number) => void;
}

/**
 * "Add one" or "Paste a list". Rendered with key={year}: a year switch starts a fresh adder; a paste still in flight
 * then reports its holidays for the old year, which the parent drops.
 */
export function HolidayAdder({year, mode, onModeChange, onAdd}: AdderProps) {
  const [date, setDate] = useState<StrictIsoDate | undefined>(undefined);
  const [name, setName] = useState('');
  const [addError, setAddError] = useState<{date?: string; name?: string}>({});
  const [pasteText, setPasteText] = useState('');
  const [pasteResult, setPasteResult] = useState<{
    added: number;
    errors: {line: number; reason: string}[];
  } | null>(null);
  const [pasteFailure, setPasteFailure] = useState<string | null>(null);
  const [isPasting, setIsPasting] = useState(false);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const addOne = () => {
    const trimmed = name.trim();
    const errors = {
      date: date && date.startsWith(`${year}-`) ? undefined : `Pick a date in ${year}.`,
      name: !trimmed
        ? 'Give each holiday a name.'
        : hasControlCharacter(trimmed)
          ? CONTROL_CHARACTER_MESSAGE
          : [...trimmed].length > MAX_NAME_LENGTH
            ? 'Keep holiday names to 80 characters or fewer.'
            : undefined,
    };
    setAddError(errors);
    if (errors.date || errors.name || !date) return;
    onAdd([{date, name: trimmed}], year);
    setDate(undefined);
    setName('');
  };

  const addPasted = async () => {
    setIsPasting(true);
    setPasteFailure(null);
    try {
      const result = await parseHolidays(year, pasteText);
      // Applied even if the adder closed meanwhile; the parent drops it if the year changed.
      onAdd(
        result.holidays.map((h) => ({date: h.date, name: h.name})),
        year,
      );
      if (!alive.current) return;
      setPasteResult({added: result.holidays.length, errors: result.errors});
      if (result.errors.length === 0) setPasteText('');
    } catch (e) {
      if (alive.current) setPasteFailure(messageOf(e));
    } finally {
      if (alive.current) setIsPasting(false);
    }
  };

  return (
    <VStack gap={4}>
      <SegmentedControl
        label="Add holidays"
        value={mode}
        onChange={(value) => onModeChange(value as AddMode)}
        layout="fill"
      >
        <SegmentedControlItem value="one" label="Add one" />
        <SegmentedControlItem value="paste" label="Paste a list" />
      </SegmentedControl>

      {mode === 'one' ? (
        <VStack gap={3}>
          <Grid columns={{minWidth: 180}} gap={3}>
            <DateInput
              label="Holiday date"
              value={date}
              onChange={(value) => {
                setDate(value);
                setAddError((prev) => ({...prev, date: undefined}));
              }}
              min={strictDate(`${year}-01-01`)}
              max={strictDate(`${year}-12-31`)}
              status={addError.date ? {type: 'error', message: addError.date} : undefined}
            />
            <TextInput
              label="Holiday name"
              value={name}
              onChange={(value) => {
                setName(value);
                setAddError((prev) => ({...prev, name: undefined}));
              }}
              onEnter={addOne}
              status={addError.name ? {type: 'error', message: addError.name} : undefined}
            />
          </Grid>
          <Button label="Add holiday" variant="outline" onClick={addOne} />
        </VStack>
      ) : (
        <VStack gap={3}>
          <TextArea
            label="One holiday per line: date, then name"
            description={`Up to 50 lines, like ${year}-01-26 Republic Day`}
            value={pasteText}
            onChange={setPasteText}
            rows={3}
          />
          <Button
            label="Load holidays"
            variant="outline"
            isLoading={isPasting}
            isDisabled={pasteText.trim() === ''}
            onClick={() => void addPasted()}
          />
          {pasteResult && (
            <VStack gap={2} xstyle={styles.marigoldPanel} role="status">
              <Text weight="bold">
                {plural(pasteResult.added, 'holiday', 'holidays')} added.
                {pasteResult.errors.length > 0
                  ? ` ${plural(pasteResult.errors.length, 'line', 'lines')} didn't load.`
                  : ''}
              </Text>
              {pasteResult.errors.map((e) => (
                <Text key={e.line} type="supporting">
                  Line {e.line}: {e.reason}
                </Text>
              ))}
            </VStack>
          )}
          {pasteFailure && <Banner status="error" title={pasteFailure} />}
        </VStack>
      )}
    </VStack>
  );
}

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}
