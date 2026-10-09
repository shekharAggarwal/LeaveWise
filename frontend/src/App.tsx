import {Theme} from '@astryxdesign/core/theme';
import {stoneTheme} from '@astryxdesign/theme-stone/built';
import {VStack} from '@astryxdesign/core/Layout';
import {Heading, Text} from '@astryxdesign/core/Text';

/**
 * Phase 1 shell: proves the Astryx + Stone setup builds. The real page is scaffolded from an
 * Astryx template in Phase 3 (see .claude/skills/astryx-ui/SKILL.md).
 */
export function App() {
  return (
    <Theme theme={stoneTheme}>
      <VStack gap={2}>
        <Heading level={1}>LeaveWise</Heading>
        <Text type="body" color="secondary">
          Plan your leave days around weekends and holidays.
        </Text>
      </VStack>
    </Theme>
  );
}
