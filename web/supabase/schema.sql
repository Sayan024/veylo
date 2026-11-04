-- =====================================================================
-- VEYLO – Supabase schema (run once in the Supabase SQL editor)
--
-- Security model (no user accounts):
--   * RLS is ON for every table and there are NO policies, so the public
--     anon key cannot read or write any table directly.
--   * All access goes through SECURITY DEFINER functions below.
--   * Joining needs a 6-character invite code. A successful join returns a
--     random 192-bit member token. Only its SHA-256 hash is stored.
--   * Every read/write requires that token and is scoped to its group.
--   * The server validates amounts, members and split totals, and uses
--     revision numbers to reject stale edits (with an audit record).
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- tables

create table if not exists groups (
  id           uuid primary key,
  name         text not null check (char_length(name) between 1 and 60),
  type         text not null check (type in ('trip','outing')),
  destination  text not null default '' check (char_length(destination) <= 80),
  currency     text not null check (currency ~ '^[A-Z]{3}$'),
  start_date   date,
  end_date     date,
  invite_code  text not null unique,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table if not exists members (
  id          uuid primary key,
  group_id    uuid not null references groups(id) on delete cascade,
  name        text not null check (char_length(name) between 1 and 40),
  device_id   text,
  upi_id      text check (upi_id is null or upi_id ~ '^[a-zA-Z0-9._-]{2,200}@[a-zA-Z]{2,64}$'),
  joined_at   timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  revision    int not null default 1,
  deleted     boolean not null default false
);
create index if not exists members_group on members(group_id, updated_at);

create table if not exists member_tokens (
  token_hash  text primary key,
  member_id   uuid not null references members(id) on delete cascade,
  group_id    uuid not null references groups(id) on delete cascade,
  created_at  timestamptz not null default now()
);

create table if not exists expenses (
  id            uuid primary key,
  group_id      uuid not null references groups(id) on delete cascade,
  title         text not null check (char_length(title) between 1 and 80),
  amount        bigint not null check (amount > 0 and amount < 100000000000),
  currency      text not null,
  payer_id      uuid not null references members(id),
  category      text not null,
  date          date not null,
  notes         text not null default '' check (char_length(notes) <= 500),
  split_method  text not null check (split_method in ('equal','exact','percent','shares')),
  shares        jsonb not null,
  created_by    uuid not null references members(id),
  updated_by    uuid references members(id),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  revision      int not null default 1,
  deleted       boolean not null default false
);
create index if not exists expenses_group on expenses(group_id, updated_at);

create table if not exists settlements (
  id          uuid primary key,
  group_id    uuid not null references groups(id) on delete cascade,
  from_id     uuid not null references members(id),
  to_id       uuid not null references members(id),
  amount      bigint not null check (amount > 0),
  created_by  uuid not null references members(id),
  updated_by  uuid references members(id),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  revision    int not null default 1,
  deleted     boolean not null default false,
  check (from_id <> to_id)
);
create index if not exists settlements_group on settlements(group_id, updated_at);

create table if not exists audit_log (
  id         bigserial primary key,
  group_id   uuid not null references groups(id) on delete cascade,
  entity     text not null,
  entity_id  uuid not null,
  action     text not null,          -- insert | update | conflict_rejected
  actor      uuid,
  old_row    jsonb,
  new_row    jsonb,
  at         timestamptz not null default now()
);

alter table groups        enable row level security;
alter table members       enable row level security;
alter table member_tokens enable row level security;
alter table expenses      enable row level security;
alter table settlements   enable row level security;
alter table audit_log     enable row level security;
revoke all on groups, members, member_tokens, expenses, settlements, audit_log from anon, authenticated;

-- ---------------------------------------------------------------- helpers

create or replace function _hash(p text) returns text language sql immutable set search_path = public, extensions as
$$ select encode(digest(p, 'sha256'), 'hex') $$;

create or replace function _new_token() returns text language sql volatile set search_path = public, extensions as
$$ select encode(gen_random_bytes(24), 'hex') $$;

create or replace function _new_invite_code() returns text language plpgsql volatile set search_path = public, extensions as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  b bytea; code text; i int;
begin
  loop
    b := gen_random_bytes(6); code := '';
    for i in 0..5 loop
      code := code || substr(alphabet, (get_byte(b, i) % 32) + 1, 1);
    end loop;
    exit when not exists (select 1 from groups where invite_code = code);
  end loop;
  return code;
end $$;

-- Resolves a member token to its member row, or raises.
create or replace function _me(p_token text) returns members
language plpgsql stable security definer set search_path = public, extensions as $$
declare m members;
begin
  select mem.* into m from member_tokens t join members mem on mem.id = t.member_id
   where t.token_hash = _hash(coalesce(p_token, '')) and not mem.deleted;
  if m.id is null then raise exception 'Not a member of this group' using errcode = '42501'; end if;
  return m;
end $$;

create or replace function _issue_token(p_member uuid, p_group uuid) returns text
language plpgsql volatile security definer set search_path = public, extensions as $$
declare tok text := _new_token();
begin
  insert into member_tokens(token_hash, member_id, group_id) values (_hash(tok), p_member, p_group);
  return tok;
end $$;

-- ---------------------------------------------------------------- create / join

create or replace function create_group(p_group jsonb, p_members jsonb, p_device text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  gid uuid := (p_group->>'id')::uuid;
  code text; me uuid; m jsonb;
begin
  if jsonb_array_length(p_members) not between 1 and 50 then raise exception 'A group needs 1–50 members'; end if;
  if exists (select 1 from groups where id = gid) then raise exception 'Group already exists'; end if;
  code := _new_invite_code();
  insert into groups(id, name, type, destination, currency, start_date, end_date, invite_code)
  values (gid, trim(p_group->>'name'), p_group->>'type', coalesce(trim(p_group->>'destination'), ''), p_group->>'currency',
          nullif(p_group->>'start_date','')::date, nullif(p_group->>'end_date','')::date, code);
  for m in select * from jsonb_array_elements(p_members) loop
    insert into members(id, group_id, name, device_id, upi_id)
    values ((m->>'id')::uuid, gid, trim(m->>'name'), nullif(m->>'device_id',''), nullif(m->>'upi_id',''));
    if m->>'device_id' = p_device then me := (m->>'id')::uuid; end if;
  end loop;
  if me is null then raise exception 'Creator must be a member'; end if;
  return jsonb_build_object('invite_code', code, 'member_token', _issue_token(me, gid), 'member_id', me, 'now', now());
end $$;

-- Shows a group's name and member list for an invite code (no expenses, no amounts).
create or replace function preview_invite(p_code text)
returns jsonb language plpgsql stable security definer set search_path = public, extensions as $$
declare g groups;
begin
  select * into g from groups where invite_code = upper(trim(p_code));
  if g.id is null then raise exception 'Invite code not found'; end if;
  return jsonb_build_object(
    'group_id', g.id, 'name', g.name, 'type', g.type, 'destination', g.destination,
    'currency', g.currency, 'start_date', g.start_date,
    'members', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'name', name, 'claimed', device_id is not null) order by joined_at)
                           from members where group_id = g.id and not deleted), '[]'::jsonb));
end $$;

create or replace function join_group(p_code text, p_name text, p_device text, p_upi text, p_member_id uuid)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare g groups; m members;
begin
  if coalesce(trim(p_name), '') = '' or coalesce(p_device, '') = '' then raise exception 'Name and device are required'; end if;
  select * into g from groups where invite_code = upper(trim(p_code));
  if g.id is null then raise exception 'Invite code not found'; end if;

  -- Same device rejoining?
  select * into m from members where group_id = g.id and device_id = p_device and not deleted limit 1;
  -- Claim a name-only member?
  if m.id is null and p_member_id is not null then
    select * into m from members where id = p_member_id and group_id = g.id and device_id is null and not deleted for update;
    if m.id is null then raise exception 'That member is already linked to another phone'; end if;
    update members set device_id = p_device, upi_id = coalesce(nullif(p_upi,''), upi_id),
           updated_at = now(), revision = revision + 1 where id = m.id;
  end if;
  if m.id is null then
    if (select count(*) from members where group_id = g.id) >= 50 then raise exception 'Group is full'; end if;
    insert into members(id, group_id, name, device_id, upi_id)
    values (gen_random_uuid(), g.id, trim(p_name), p_device, nullif(p_upi,'')) returning * into m;
  end if;
  return jsonb_build_object('group_id', g.id, 'member_id', m.id, 'member_token', _issue_token(m.id, g.id));
end $$;

-- ---------------------------------------------------------------- push

create or replace function push_member(p_token text, p_member jsonb, p_base_revision int)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare me members := _me(p_token); cur members; mid uuid := (p_member->>'id')::uuid;
begin
  select * into cur from members where id = mid for update;
  if cur.id is null then
    insert into members(id, group_id, name, device_id, upi_id)
    values (mid, me.group_id, trim(p_member->>'name'), null, null) returning * into cur;
    return jsonb_build_object('status','ok','revision',cur.revision,'updated_at',cur.updated_at);
  end if;
  if cur.group_id <> me.group_id then raise exception 'Member belongs to another group' using errcode = '42501'; end if;
  -- You may edit yourself or a name-only member, never another member's phone-linked profile.
  if cur.device_id is not null and cur.id <> me.id then raise exception 'You can only edit your own profile' using errcode = '42501'; end if;
  if cur.revision <> p_base_revision then return jsonb_build_object('status','conflict','server',to_jsonb(cur)); end if;
  update members set name = trim(p_member->>'name'),
         upi_id = case when cur.id = me.id then nullif(p_member->>'upi_id','') else upi_id end,
         updated_at = now(), revision = revision + 1
   where id = mid returning * into cur;
  return jsonb_build_object('status','ok','revision',cur.revision,'updated_at',cur.updated_at);
end $$;

create or replace function push_expense(p_token text, p_expense jsonb, p_base_revision int)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  me members := _me(p_token);
  eid uuid := (p_expense->>'id')::uuid;
  cur expenses; amt bigint := (p_expense->>'amount')::bigint;
  is_deleted boolean := coalesce((p_expense->>'deleted')::boolean, false);
  share_sum bigint; bad int;
begin
  if (p_expense->>'group_id')::uuid <> me.group_id then raise exception 'Wrong group' using errcode = '42501'; end if;

  -- Never trust client totals: re-check everything.
  if not exists (select 1 from members where id = (p_expense->>'payer_id')::uuid and group_id = me.group_id) then
    raise exception 'Payer is not in this group';
  end if;
  select coalesce(sum((s->>'share')::bigint), 0),
         count(*) filter (where (s->>'share')::bigint < 0
                          or not exists (select 1 from members x where x.id = (s->>'member_id')::uuid and x.group_id = me.group_id))
    into share_sum, bad
    from jsonb_array_elements(p_expense->'shares') s;
  if bad > 0 then raise exception 'Shares reference unknown members'; end if;
  if share_sum <> amt then raise exception 'Shares (%) do not add up to the total (%)', share_sum, amt; end if;

  select * into cur from expenses where id = eid for update;

  if cur.id is null then
    insert into expenses(id, group_id, title, amount, currency, payer_id, category, date, notes, split_method, shares,
                         created_by, updated_by, deleted)
    values (eid, me.group_id, trim(p_expense->>'title'), amt, p_expense->>'currency', (p_expense->>'payer_id')::uuid,
            p_expense->>'category', (p_expense->>'date')::date, coalesce(p_expense->>'notes',''), p_expense->>'split_method',
            p_expense->'shares', me.id, me.id, is_deleted)
    returning * into cur;
    insert into audit_log(group_id, entity, entity_id, action, actor, new_row) values (me.group_id, 'expense', eid, 'insert', me.id, to_jsonb(cur));
    return jsonb_build_object('status','ok','revision',cur.revision,'updated_at',cur.updated_at);
  end if;

  if cur.group_id <> me.group_id then raise exception 'Wrong group' using errcode = '42501'; end if;

  if cur.revision <> p_base_revision then
    insert into audit_log(group_id, entity, entity_id, action, actor, old_row, new_row)
    values (me.group_id, 'expense', eid, 'conflict_rejected', me.id, to_jsonb(cur), p_expense);
    return jsonb_build_object('status','conflict','server',to_jsonb(cur));
  end if;

  insert into audit_log(group_id, entity, entity_id, action, actor, old_row, new_row)
  values (me.group_id, 'expense', eid, 'update', me.id, to_jsonb(cur), p_expense);
  update expenses set title = trim(p_expense->>'title'), amount = amt, currency = p_expense->>'currency',
         payer_id = (p_expense->>'payer_id')::uuid, category = p_expense->>'category', date = (p_expense->>'date')::date,
         notes = coalesce(p_expense->>'notes',''), split_method = p_expense->>'split_method', shares = p_expense->'shares',
         deleted = is_deleted, updated_by = me.id, updated_at = now(), revision = revision + 1
   where id = eid returning * into cur;
  return jsonb_build_object('status','ok','revision',cur.revision,'updated_at',cur.updated_at);
end $$;

create or replace function push_settlement(p_token text, p_settlement jsonb, p_base_revision int)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  me members := _me(p_token);
  sid uuid := (p_settlement->>'id')::uuid;
  cur settlements;
begin
  if (p_settlement->>'group_id')::uuid <> me.group_id then raise exception 'Wrong group' using errcode = '42501'; end if;
  if (select count(*) from members where group_id = me.group_id
        and id in ((p_settlement->>'from_id')::uuid, (p_settlement->>'to_id')::uuid)) <> 2 then
    raise exception 'Settlement members must be two different people in this group';
  end if;

  select * into cur from settlements where id = sid for update;
  if cur.id is null then
    insert into settlements(id, group_id, from_id, to_id, amount, created_by, updated_by, deleted)
    values (sid, me.group_id, (p_settlement->>'from_id')::uuid, (p_settlement->>'to_id')::uuid,
            (p_settlement->>'amount')::bigint, me.id, me.id, coalesce((p_settlement->>'deleted')::boolean, false))
    returning * into cur;
    insert into audit_log(group_id, entity, entity_id, action, actor, new_row) values (me.group_id, 'settlement', sid, 'insert', me.id, to_jsonb(cur));
    return jsonb_build_object('status','ok','revision',cur.revision,'updated_at',cur.updated_at);
  end if;
  if cur.group_id <> me.group_id then raise exception 'Wrong group' using errcode = '42501'; end if;
  if cur.revision <> p_base_revision then
    insert into audit_log(group_id, entity, entity_id, action, actor, old_row, new_row)
    values (me.group_id, 'settlement', sid, 'conflict_rejected', me.id, to_jsonb(cur), p_settlement);
    return jsonb_build_object('status','conflict','server',to_jsonb(cur));
  end if;
  -- Settlements are immutable except for undo (soft delete).
  update settlements set deleted = coalesce((p_settlement->>'deleted')::boolean, deleted),
         updated_by = me.id, updated_at = now(), revision = revision + 1
   where id = sid returning * into cur;
  insert into audit_log(group_id, entity, entity_id, action, actor, old_row, new_row)
  values (me.group_id, 'settlement', sid, 'update', me.id, null, to_jsonb(cur));
  return jsonb_build_object('status','ok','revision',cur.revision,'updated_at',cur.updated_at);
end $$;

-- ---------------------------------------------------------------- pull

create or replace function pull_group(p_token text, p_since timestamptz)
returns jsonb language plpgsql stable security definer set search_path = public, extensions as $$
declare me members := _me(p_token); since timestamptz := coalesce(p_since, '-infinity'::timestamptz);
begin
  return jsonb_build_object(
    'now', now(),
    'group', (select to_jsonb(g) from groups g where g.id = me.group_id),
    'members', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'group_id', group_id, 'name', name,
                          'device_id', case when device_id is null then null else 'linked' end,
                          'upi_id', upi_id, 'joined_at', joined_at, 'updated_at', updated_at, 'revision', revision, 'deleted', deleted))
                         from members where group_id = me.group_id and updated_at > since), '[]'::jsonb),
    'expenses', coalesce((select jsonb_agg(to_jsonb(e)) from expenses e where e.group_id = me.group_id and e.updated_at > since), '[]'::jsonb),
    'settlements', coalesce((select jsonb_agg(to_jsonb(s)) from settlements s where s.group_id = me.group_id and s.updated_at > since), '[]'::jsonb)
  );
end $$;

-- ---------------------------------------------------------------- grants

revoke all on all functions in schema public from public;
grant execute on function create_group(jsonb, jsonb, text)               to anon, authenticated;
grant execute on function preview_invite(text)                           to anon, authenticated;
grant execute on function join_group(text, text, text, text, uuid)       to anon, authenticated;
grant execute on function push_member(text, jsonb, int)                  to anon, authenticated;
grant execute on function push_expense(text, jsonb, int)                 to anon, authenticated;
grant execute on function push_settlement(text, jsonb, int)              to anon, authenticated;
grant execute on function pull_group(text, timestamptz)                  to anon, authenticated;
