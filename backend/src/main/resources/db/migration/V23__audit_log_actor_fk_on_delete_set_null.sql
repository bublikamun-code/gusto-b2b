-- Аудит 2026-09-30, группа «Админка». Существующие миграции не переписываем.
--
-- Учётные записи и изменения компаний теперь пишутся в audit_log (раньше не писались
-- вовсе), и выяснилось, что внешний ключ audit_log_actor_id_fkey стоит без ON DELETE.
-- Пока пользователь удаляется мягко (deleted_at) это не проявлялось, но жёсткое удаление
-- строки users — а оно есть и в интеграционных темах, и в любом обслуживании БД —
-- падало с ошибкой «violates foreign key constraint audit_log_actor_id_fkey».
--
-- Для журнала аудита ON DELETE SET NULL — единственно верное поведение: след действий
-- обязан пережить удаление учётки, иначе след можно стереть, удалив пользователя.
-- Просмотр журнала уже использует LEFT JOIN на users (AuditQueryService), поэтому
-- записи с actor_id = null отображаются корректно, просто без email актора.

ALTER TABLE audit_log
    DROP CONSTRAINT IF EXISTS audit_log_actor_id_fkey;

ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_actor_id_fkey
    FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL;