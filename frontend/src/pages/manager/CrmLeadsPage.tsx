import { useCallback, useEffect, useState } from 'react';
import { Badge, Button, Card, ConfirmModal, useToast } from '../../components/ui';
import {
  LEAD_STATUSES,
  LEAD_TRANSITIONS,
  changeLeadStatus,
  listLeads,
  type Lead,
  type LeadStatus,
} from '../../api/crm';
import styles from './CrmLeadsPage.module.scss';

const STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'Новый',
  IN_PROGRESS: 'В работе',
  QUALIFIED: 'Квалифицирован',
  WON: 'Успешно',
  LOST: 'Потерян',
};

/** Воронка лидов (S30): колонки по статусам, перевод на следующую стадию. */
export default function CrmLeadsPage() {
  const { push } = useToast();
  const [leads, setLeads] = useState<Lead[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [losing, setLosing] = useState<Lead | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    listLeads({ scope: 'pool', size: 100 })
      .then((page) => setLeads(page.items))
      .catch((err) => push((err as Error).message, 'error'))
      .finally(() => setLoading(false));
  }, [push]);

  useEffect(() => {
    load();
  }, [load]);

  const advance = async (lead: Lead, status: LeadStatus) => {
    if (status === 'LOST') {
      setLosing(lead);
      return;
    }
    setBusy(lead.id);
    try {
      await changeLeadStatus(lead.id, status);
      push(`Лид «${lead.name}» → ${STATUS_LABELS[status]}`, 'success');
      load();
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setBusy(null);
    }
  };

  const confirmLost = async () => {
    if (!losing) return;
    const lead = losing;
    setBusy(lead.id);
    try {
      await changeLeadStatus(lead.id, 'LOST');
      push(`Лид «${lead.name}» → ${STATUS_LABELS.LOST}`, 'success');
      setLosing(null);
      load();
    } catch (err) {
      push((err as Error).message, 'error');
    } finally {
      setBusy(null);
    }
  };

  const byStatus = (status: LeadStatus) => leads.filter((l) => l.status === status);

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1>Воронка лидов</h1>
        <p className={styles.hint}>
          Взятие лида из пула закрепляет его за вами; LOST закрывает лид окончательно.
        </p>
      </header>

      {loading ? (
        <p className={styles.hint}>Загрузка…</p>
      ) : (
        <div className={styles.columns}>
          {LEAD_STATUSES.map((status) => {
            const items = byStatus(status);
            const next = LEAD_TRANSITIONS[status];
            return (
              <div key={status} className={styles.column}>
                <div className={styles.columnHead}>
                  <span>{STATUS_LABELS[status]}</span>
                  <Badge
                    variant={
                      status === 'WON' ? 'success' : status === 'LOST' ? 'outline' : 'neutral'
                    }
                  >
                    {items.length}
                  </Badge>
                </div>
                {items.length === 0 && <div className={styles.emptyCard}>пусто</div>}
                {items.map((lead) => (
                  <Card key={lead.id} className={styles.leadCard}>
                    <strong>{lead.name}</strong>
                    {lead.companyName && <span className={styles.meta}>{lead.companyName}</span>}
                    {lead.phone && <span className={styles.meta}>{lead.phone}</span>}
                    <div className={styles.leadActions}>
                      {next
                        .filter((s) => s !== 'LOST')
                        .map((s) => (
                          <Button
                            key={s}
                            size="sm"
                            variant="accent"
                            loading={busy === lead.id}
                            onClick={() => advance(lead, s)}
                          >
                            {s === 'WON' ? 'Выиграть' : '→ ' + STATUS_LABELS[s]}
                          </Button>
                        ))}
                      {next.includes('LOST') && (
                        <Button
                          size="sm"
                          variant="secondary"
                          loading={busy === lead.id}
                          onClick={() => setLosing(lead)}
                        >
                          Потерян
                        </Button>
                      )}
                    </div>
                  </Card>
                ))}
              </div>
            );
          })}
        </div>
      )}

      <ConfirmModal
        open={losing !== null}
        title="Потерять лид"
        confirmLabel="Потерять"
        loading={busy === losing?.id}
        onClose={() => setLosing(null)}
        onConfirm={confirmLost}
      >
        <p>
          Перевести лид «{losing?.name}» в статус «Потерян»? Это окончательный статус, лид закроется
          навсегда.
        </p>
      </ConfirmModal>
    </div>
  );
}
