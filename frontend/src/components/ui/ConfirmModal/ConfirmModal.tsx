import { useEffect, type ReactNode } from 'react';
import { Button } from '../Button/Button';
import { Modal } from '../Modal/Modal';

export interface ConfirmModalProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export function ConfirmModal({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Отмена',
  loading = false,
  onConfirm,
  onClose,
}: ConfirmModalProps) {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Enter' || loading) return;
      event.preventDefault();
      onConfirm();
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open, loading, onConfirm]);

  const requestClose = () => {
    if (!loading) onClose();
  };

  return (
    <Modal
      open={open}
      title={title}
      onClose={requestClose}
      footer={
        <>
          <Button variant="secondary" onClick={requestClose} disabled={loading} autoFocus>
            {cancelLabel}
          </Button>
          <Button onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {children}
    </Modal>
  );
}
