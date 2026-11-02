-- SplitSmart central repository schema. Paste this whole file into the Supabase dashboard SQL editor and run it once.
-- All money is stored as integer cents; dates are the device-local YYYY-MM-DD date sent by the app.

create table users (
    user_id       bigserial primary key,
    name          text not null check (length(trim(name)) > 0),
    email         text not null unique check (email like '%@usc.edu'),
    usc_id        char(10) not null unique check (usc_id ~ '^[0-9]{10}$'),
    password_hash text not null
);

create table expense_groups (
    group_id     bigserial primary key,
    name         text not null check (length(trim(name)) > 0),
    description  text,
    owner_id     bigint not null references users (user_id),
    created_date date not null
);

create table group_memberships (
    group_id    bigint not null references expense_groups (group_id) on delete cascade,
    user_id     bigint not null references users (user_id),
    joined_date date not null,
    primary key (group_id, user_id)
);

create table expenses (
    expense_id   bigserial primary key,
    group_id     bigint not null references expense_groups (group_id) on delete cascade,
    title        text not null check (length(trim(title)) > 0),
    amount_cents integer not null check (amount_cents > 0),
    category     text not null default 'OTHER'
                 check (category in ('FOOD', 'HOUSING', 'TRANSPORTATION', 'ENTERTAINMENT', 'OTHER')),
    payer_id     bigint not null references users (user_id),
    created_by   bigint not null references users (user_id),
    split_method text not null check (split_method in ('EQUAL', 'EXACT_AMOUNT', 'PERCENTAGE')),
    created_date date not null,
    note         text,
    created_at   timestamptz not null default now()
);

create table expense_splits (
    expense_id  bigint not null references expenses (expense_id) on delete cascade,
    user_id     bigint not null references users (user_id),
    share_cents integer not null check (share_cents >= 0),
    input_value text,
    primary key (expense_id, user_id)
);

create table settlements (
    settlement_id bigserial primary key,
    group_id      bigint not null references expense_groups (group_id) on delete cascade,
    payer_id      bigint not null references users (user_id),
    payee_id      bigint not null references users (user_id),
    amount_cents  integer not null check (amount_cents > 0),
    date          date not null,
    note          text,
    created_at    timestamptz not null default now(),
    created_by    bigint not null references users (user_id),
    check (payer_id <> payee_id)
);

create index on group_memberships (user_id);
create index on expenses (group_id, created_date);
create index on settlements (group_id, date);

-- Creates a group and its owner's membership in one transaction.
create function create_group_with_owner(p_name text, p_description text, p_owner_id bigint, p_created_date date)
returns setof expense_groups
language plpgsql as $$
declare
    new_id bigint;
begin
    insert into expense_groups (name, description, owner_id, created_date)
    values (p_name, p_description, p_owner_id, p_created_date)
    returning group_id into new_id;

    insert into group_memberships (group_id, user_id, joined_date)
    values (new_id, p_owner_id, p_created_date);

    return query select * from expense_groups where group_id = new_id;
end;
$$;

-- Saves an expense and all of its splits in one transaction (design: ExpenseRepository.saveWithSplits).
create function save_expense_with_splits(p_expense jsonb, p_splits jsonb)
returns bigint
language plpgsql as $$
declare
    new_id bigint;
begin
    insert into expenses (group_id, title, amount_cents, category, payer_id, created_by, split_method, created_date, note)
    values ((p_expense ->> 'group_id')::bigint,
            p_expense ->> 'title',
            (p_expense ->> 'amount_cents')::integer,
            p_expense ->> 'category',
            (p_expense ->> 'payer_id')::bigint,
            (p_expense ->> 'created_by')::bigint,
            p_expense ->> 'split_method',
            (p_expense ->> 'created_date')::date,
            p_expense ->> 'note')
    returning expense_id into new_id;

    insert into expense_splits (expense_id, user_id, share_cents, input_value)
    select new_id, (s ->> 'user_id')::bigint, (s ->> 'share_cents')::integer, s ->> 'input_value'
    from jsonb_array_elements(p_splits) as s;

    return new_id;
end;
$$;

-- Replaces an expense's fields and splits in one transaction (design: ExpenseRepository.updateWithSplits).
-- The creation date and creator are never changed by an edit.
create function update_expense_with_splits(p_expense_id bigint, p_expense jsonb, p_splits jsonb)
returns void
language plpgsql as $$
begin
    update expenses set
        title        = p_expense ->> 'title',
        amount_cents = (p_expense ->> 'amount_cents')::integer,
        category     = p_expense ->> 'category',
        payer_id     = (p_expense ->> 'payer_id')::bigint,
        split_method = p_expense ->> 'split_method',
        note         = p_expense ->> 'note'
    where expense_id = p_expense_id;

    delete from expense_splits where expense_id = p_expense_id;

    insert into expense_splits (expense_id, user_id, share_cents, input_value)
    select p_expense_id, (s ->> 'user_id')::bigint, (s ->> 'share_cents')::integer, s ->> 'input_value'
    from jsonb_array_elements(p_splits) as s;
end;
$$;

-- The app talks to PostgREST with the anon key and does its own login (PasswordHasher),
-- so the anon role gets full access to these tables. Acceptable for a course project.
alter table users enable row level security;
alter table expense_groups enable row level security;
alter table group_memberships enable row level security;
alter table expenses enable row level security;
alter table expense_splits enable row level security;
alter table settlements enable row level security;

create policy anon_all on users for all to anon using (true) with check (true);
create policy anon_all on expense_groups for all to anon using (true) with check (true);
create policy anon_all on group_memberships for all to anon using (true) with check (true);
create policy anon_all on expenses for all to anon using (true) with check (true);
create policy anon_all on expense_splits for all to anon using (true) with check (true);
create policy anon_all on settlements for all to anon using (true) with check (true);

grant usage on schema public to anon;
grant all on all tables in schema public to anon;
grant all on all sequences in schema public to anon;
grant execute on all functions in schema public to anon;

-- Make the REST API pick up the new tables and functions immediately.
notify pgrst, 'reload schema';
