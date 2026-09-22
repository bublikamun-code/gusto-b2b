import { describe, expect, it, vi, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmModal } from './ConfirmModal';

function setup(overrides: Partial<Parameters<typeof ConfirmModal>[0]> = {}) {
  const onConfirm = vi.fn();
  const onClose = vi.fn();
  render(
    <ConfirmModal
      open
      title="Провести документ"
      children={<p>Сформируются движения склада</p>}
      confirmLabel="Провести"
      onConfirm={onConfirm}
      onClose={onClose}
      {...overrides}
    />,
  );
  return { onConfirm, onClose };
}

afterEach(cleanup);

describe('ConfirmModal', () => {
  it('renders nothing when closed', () => {
    render(
      <ConfirmModal
        open={false}
        title="Заголовок"
        children={<p>Текст</p>}
        confirmLabel="Ок"
        onConfirm={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('renders title, body text and buttons when open', () => {
    setup();
    expect(screen.getByRole('dialog', { name: 'Провести документ' })).toBeInTheDocument();
    expect(screen.getByText('Сформируются движения склада')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Провести' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Отмена' })).toBeInTheDocument();
  });

  it('confirms by click and by Enter', async () => {
    const user = userEvent.setup();
    const { onConfirm } = setup();
    await user.click(screen.getByRole('button', { name: 'Провести' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    await user.keyboard('{Enter}');
    expect(onConfirm).toHaveBeenCalledTimes(2);
  });

  it('closes by cancel button, Escape and focus lands on cancel', async () => {
    const user = userEvent.setup();
    const { onClose } = setup();
    expect(screen.getByRole('button', { name: 'Отмена' })).toHaveFocus();
    await user.click(screen.getByRole('button', { name: 'Отмена' }));
    expect(onClose).toHaveBeenCalledTimes(1);
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('blocks confirm/close while loading', async () => {
    const user = userEvent.setup();
    const { onConfirm, onClose } = setup({ loading: true });
    await user.keyboard('{Enter}');
    await user.click(screen.getByRole('button', { name: 'Отмена' }));
    await user.keyboard('{Escape}');
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Отмена' })).toBeDisabled();
  });

  it('invokes onConfirm once per Enter even with focus on a footer button', async () => {
    const user = userEvent.setup();
    const { onConfirm } = setup();
    await user.click(screen.getByRole('button', { name: 'Отмена' }));
    await user.keyboard('{Enter}');
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});
