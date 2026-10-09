import {render, screen} from '@testing-library/react';
import {describe, expect, it} from 'vitest';
import {App} from './App';
import {expectNoAxeViolations} from './test/axe';

describe('App shell', () => {
  it('renders the product name as the page heading', () => {
    render(<App />);
    expect(screen.getByRole('heading', {level: 1, name: 'LeaveWise'})).toBeInTheDocument();
  });

  it('has no axe accessibility violations', async () => {
    const {container} = render(<App />);
    await expectNoAxeViolations(container);
  });
});
