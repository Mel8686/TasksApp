-- Bring existing rows into the closed set before constraining it.
update task set status = 'TODO'
where status not in ('TODO', 'DOING', 'DONE');

-- The database is the cheapest place to make an invalid state impossible.
alter table task
    add constraint task_status_check check (status in ('TODO', 'DOING', 'DONE'));
