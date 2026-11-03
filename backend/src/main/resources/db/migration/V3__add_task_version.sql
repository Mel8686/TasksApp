-- Optimistic concurrency. Existing rows are all at version 0.
alter table task add column version bigint not null default 0;
