import {useState} from 'react';
import {Badge} from '@astryxdesign/core/Badge';
import {Banner} from '@astryxdesign/core/Banner';
import {Button} from '@astryxdesign/core/Button';
import {CheckboxList, CheckboxListItem} from '@astryxdesign/core/CheckboxList';
import {Collapsible} from '@astryxdesign/core/Collapsible';
import {DateInput} from '@astryxdesign/core/DateInput';
import {Grid} from '@astryxdesign/core/Grid';
import {HStack, VStack} from '@astryxdesign/core/Layout';
import {Link} from '@astryxdesign/core/Link';
import {Text} from '@astryxdesign/core/Text';
import {TextArea} from '@astryxdesign/core/TextArea';
import {TextInput} from '@astryxdesign/core/TextInput';
import {ApiError, parseHolidays} from '../../api/client';
import type {HolidaysResponse, IsoDate} from '../../api/types';
import {formatDay} from '../../lib/dates';
import {addHoliday, type HolidayRow} from './holidayRows';

const MAX_NAME_LENGTH = 80;

/** Astryx DateInput's date type (not exported publicly): YYYY-MM-DD as a template literal. */
type StrictIsoDate = `${number}${number}${number}${number}-${number}${number}-${number}${number}`;
const strictDate = (date: string) => date as StrictIsoDate;

interface Props {
  year: number;
  source: HolidaysResponse | null;
  rows: HolidayRow[];
  ticked: Set<IsoDate>;
  onRowsChange: (rows: HolidayRow[], newlyTicked: IsoDate[]) => void;
  onTickedChange: (ticked: Set<IsoDate>) => void;
}

export function HolidayEditor({year, source, rows, ticked, onRowsChange, onTickedChange}: Props) {
  const [date, setDate] = useState<StrictIsoDate | undefined>(undefined);
  const [name, setName] = useState('');
  const [addError, setAddError] = useState<{date?: string; name?: string}>({});
  const [pasteText, setPasteText] = useState('');
  const [pasteErrors, setPasteErrors] = useState<{line: number; reason: string}[]>([]);
  const [pasteFailure, setPasteFailure] = useState<string | null>(null);
  const [isPasting, setIsPasting] = useState(false);

  const addOne = () => {
    const trimmed = name.trim();
    const errors = {
      date: date && date.startsWith(`${year}-`) ? undefined : `Pick a date in ${year}.`,
      name: !trimmed
        ? 'Give each holiday a name.'
        : [...trimmed].length > MAX_NAME_LENGTH
          ? 'Keep holiday names to 80 characters or fewer.'
          : undefined,
    };
    setAddError(errors);
    if (errors.date || errors.name || !date) return;
    onRowsChange(addHoliday(rows, date, trimmed), [date]);
    setDate(undefined);
    setName('');
  };

  const addPasted = async () => {
    setIsPasting(true);
    setPasteFailure(null);
    try {
      const result = await parseHolidays(year, pasteText);
      let next = rows;
      for (const h of result.holidays) next = addHoliday(next, h.date, h.name);
      onRowsChange(
        next,
        result.holidays.map((h) => h.date),
      );
      setPasteErrors(result.errors);
      if (result.errors.length === 0) setPasteText('');
    } catch (e) {
      setPasteFailure(e instanceof ApiError ? e.message : String(e));
    } finally {
      setIsPasting(false);
    }
  };

  const tickedCount = rows.filter((r) => ticked.has(r.date)).length;

  return (
    <VStack gap={4}>
      <VStack gap={1}>
        <Text type="large" weight="semibold">
          Holidays
        </Text>
        {source?.available && source.source ? (
          <Text type="supporting" color="secondary">
            {tickedCount} of {rows.length} apply. Preloaded from{' '}
            <Link href={source.source.url} isExternalLink type="supporting">
              {source.source.title}
            </Link>{' '}
            ({source.source.publisher}). {source.reminder}
          </Text>
        ) : (
          <Text type="supporting" color="secondary">
            {source?.reminder ?? ''}
          </Text>
        )}
      </VStack>

      {source && !source.available && (
        <Banner
          status="info"
          title={`No official list for ${year} is bundled yet.`}
          description="Add your company's holidays below, one by one or as a pasted list."
        />
      )}

      {rows.length > 0 && (
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
              label={<Text textWrap="wrap">{row.names.join(' / ')}</Text>}
              description={formatDay(row.date)}
              endContent={row.added ? <Badge variant="neutral" label="Added" /> : undefined}
            />
          ))}
        </CheckboxList>
      )}

      <VStack gap={2}>
        <Grid columns={{minWidth: 200}} gap={3}>
          <DateInput
            label="Holiday date"
            value={date}
            onChange={setDate}
            min={strictDate(`${year}-01-01`)}
            max={strictDate(`${year}-12-31`)}
            status={addError.date ? {type: 'error', message: addError.date} : undefined}
          />
          <TextInput
            label="Holiday name"
            value={name}
            onChange={setName}
            onEnter={addOne}
            status={addError.name ? {type: 'error', message: addError.name} : undefined}
          />
        </Grid>
        <HStack>
          <Button label="Add holiday" variant="secondary" onClick={addOne} />
        </HStack>
      </VStack>

      <Collapsible trigger={<Text type="body">Paste a list</Text>} defaultIsOpen={false}>
        <VStack gap={2}>
          <TextArea
            label="Paste holidays"
            description={`One per line, like ${year}-01-26 Name. Up to 50 lines.`}
            value={pasteText}
            onChange={setPasteText}
            rows={4}
          />
          <HStack>
            <Button
              label="Add pasted lines"
              variant="secondary"
              isLoading={isPasting}
              isDisabled={pasteText.trim() === ''}
              onClick={() => void addPasted()}
            />
          </HStack>
          {pasteErrors.length > 0 && (
            <Banner
              status="warning"
              title={`${pasteErrors.length} ${pasteErrors.length === 1 ? 'line' : 'lines'} not loaded`}
              description={
                <VStack gap={0.5}>
                  {pasteErrors.map((e) => (
                    <Text key={e.line} type="supporting">
                      Line {e.line}: {e.reason}
                    </Text>
                  ))}
                </VStack>
              }
            />
          )}
          {pasteFailure && <Banner status="error" title={pasteFailure} />}
        </VStack>
      </Collapsible>
    </VStack>
  );
}
