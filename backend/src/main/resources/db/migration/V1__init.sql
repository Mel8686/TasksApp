create table task (
    id         bigserial primary key,
    title      varchar(200) not null,
    status     varchar(20)  not null default 'TODO',
    due_date   date,
    created_at timestamptz  not null default now()
);
