-- Soft delete. Null means active; a timestamp records when it was archived.
alter table task add column archived_at timestamptz;

-- The default listing is active rows, newest first. Give it a partial index so
-- archived rows never enter the scan that serves most requests.
create index task_active_recent_idx on task (created_at desc, id desc)
    where archived_at is null;
