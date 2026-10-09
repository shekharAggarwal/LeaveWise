import axe from 'axe-core';
import {expect} from 'vitest';

/**
 * Runs axe-core on a rendered container and fails with a readable list of violations.
 * Colour contrast is checked in a real browser (jsdom cannot compute it), so it is disabled here.
 */
export async function expectNoAxeViolations(container: Element): Promise<void> {
  const results = await axe.run(container, {
    rules: {'color-contrast': {enabled: false}},
  });
  const summary = results.violations.map(
    (v) => `${v.id} (${v.impact ?? 'unknown'}): ${v.help} on ${v.nodes.length} node(s)`,
  );
  expect(summary).toEqual([]);
}
