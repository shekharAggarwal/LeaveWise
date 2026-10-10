import {Theme} from '@astryxdesign/core/theme';
import {leavewiseTheme} from './theme/leavewise';
import {PlannerPage} from './features/planner/PlannerPage';

export function App() {
  return (
    <Theme theme={leavewiseTheme} mode="light">
      <PlannerPage />
    </Theme>
  );
}
