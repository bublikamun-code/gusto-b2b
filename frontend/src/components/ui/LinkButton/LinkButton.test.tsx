import { describe, expect, it, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LinkButton } from './LinkButton';

function setup(props: Partial<Parameters<typeof LinkButton>[0]> = {}) {
  render(
    <MemoryRouter>
      <LinkButton to="/catalog" {...props}>
        В каталог
      </LinkButton>
    </MemoryRouter>,
  );
}

afterEach(cleanup);

describe('LinkButton', () => {
  it('renders a single link (no nested interactive elements)', () => {
    setup();
    const link = screen.getByRole('link', { name: 'В каталог' });
    expect(link.tagName).toBe('A');
    expect(link.querySelector('button')).toBeNull();
  });

  it('keeps button look via Button classes and supports variant/size', () => {
    setup({ variant: 'secondary', size: 'sm' });
    const link = screen.getByRole('link', { name: 'В каталог' });
    expect(link.className).toContain('button');
    expect(link.className).toContain('secondary');
    expect(link.className).toContain('sm');
  });
});
