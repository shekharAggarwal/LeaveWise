import {Theme} from '@astryxdesign/core/theme';
import {stoneTheme} from '@astryxdesign/theme-stone/built';
import {PlannerPage} from './features/planner/PlannerPage';

export function App() {
  return (
    <Theme theme={stoneTheme}>
      <PlannerPage />
    </Theme>
  );
}
