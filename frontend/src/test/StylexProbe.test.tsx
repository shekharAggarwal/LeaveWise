import * as stylex from '@stylexjs/stylex';
import {render, screen} from '@testing-library/react';
import {Text} from '@astryxdesign/core/Text';
import {expect, it} from 'vitest';

const styles = stylex.create({probe: {textAlign: 'center'}});

// Guards the test setup: product stylex.create() calls must compile under Vitest.
it('compiles product StyleX styles in tests', () => {
  render(
    <Text type="body" xstyle={styles.probe}>
      probe
    </Text>,
  );
  expect(screen.getByText('probe').className).not.toBe('');
});
