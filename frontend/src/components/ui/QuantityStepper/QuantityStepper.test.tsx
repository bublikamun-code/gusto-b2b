import { describe, expect, it, vi, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QuantityStepper } from './QuantityStepper';

function setup(overrides: Partial<Parameters<typeof QuantityStepper>[0]> = {}) {
  const onChange = vi.fn();
  const onStep = vi.fn();
  render(<QuantityStepper value="1" onChange={onChange} onStep={onStep} {...overrides} />);
  return { onChange, onStep };
}

afterEach(cleanup);

describe('QuantityStepper', () => {
  it('«+» увеличивает на шаг и зовёт onChange + onStep с итоговым значением', async () => {
    const { onChange, onStep } = setup({ step: 1 });
    await userEvent.click(screen.getByRole('button', { name: 'Увеличить количество' }));
    expect(onChange).toHaveBeenCalledWith('2');
    expect(onStep).toHaveBeenCalledWith('2');
  });

  it('«−» уменьшает, но не ниже min (кнопка задизейплена на минимуме)', async () => {
    const { onChange } = setup({ value: '0.001', min: 0.001, step: 0.001 });
    const minus = screen.getByRole('button', { name: 'Уменьшить количество' });
    expect(minus).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Увеличить количество' }));
    expect(onChange).toHaveBeenCalledWith('0.002');
  });

  it('дробный шаг даёт точное значение без хвостов', async () => {
    const { onChange } = setup({ value: '1.2', step: 0.3 });
    await userEvent.click(screen.getByRole('button', { name: 'Увеличить количество' }));
    expect(onChange).toHaveBeenCalledWith('1.5');
  });

  it('ввод в поле просто пробрасывается в onChange', async () => {
    const { onChange } = setup();
    await userEvent.type(screen.getByRole('spinbutton'), '3');
    expect(onChange).toHaveBeenLastCalledWith('13');
  });
});
