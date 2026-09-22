import { describe, expect, it, vi, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Select } from './Select';

const OPTIONS = [
  { value: 'gusto', label: 'Густо' },
  { value: 'zarechye', label: 'Ферма Заречье' },
];

function setup(overrides: Partial<Parameters<typeof Select>[0]> = {}) {
  const onChange = vi.fn();
  render(<Select aria-label="Бренд" options={OPTIONS} onChange={onChange} {...overrides} />);
  return { onChange };
}

afterEach(cleanup);

describe('Select (кастомный дропдаун)', () => {
  it('открывается по клику и показывает все варианты', async () => {
    setup({ value: '', placeholder: 'Все бренды' });
    await userEvent.click(screen.getByRole('combobox'));
    const list = screen.getByRole('listbox');
    expect(list).toBeInTheDocument();
    expect(screen.getAllByRole('option').map((o) => o.textContent?.replace('✓', '').trim())).toEqual([
      'Все бренды',
      'Густо',
      'Ферма Заречье',
    ]);
  });

  it('выбор варианта вызывает onChange с шимом target.value и закрывает список', async () => {
    const { onChange } = setup({ value: '' });
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(screen.getByRole('option', { name: 'Ферма Заречье' }));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect((onChange.mock.calls[0][0] as { target: { value: string } }).target.value).toBe(
      'zarechye',
    );
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('Escape закрывает список, выбор с клавиатуры работает', async () => {
    const { onChange } = setup({ value: '' });
    const trigger = screen.getByRole('combobox');
    await userEvent.click(trigger);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('listbox')).toBeInTheDocument();
    await userEvent.keyboard('{Enter}');
    expect((onChange.mock.calls[0][0] as { target: { value: string } }).target.value).toBe(
      'gusto',
    );
  });

  it('выбранный вариант помечается aria-selected', async () => {
    setup({ value: 'zarechye' });
    await userEvent.click(screen.getByRole('combobox'));
    expect(screen.getByRole('option', { name: 'Ферма Заречье' }).getAttribute('aria-selected')).toBe(
      'true',
    );
    expect(screen.getByRole('option', { name: 'Густо' }).getAttribute('aria-selected')).toBe(
      'false',
    );
  });

  it('disabled не открывает список', async () => {
    setup({ value: '', disabled: true });
    await userEvent.click(screen.getByRole('combobox'));
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });
});
