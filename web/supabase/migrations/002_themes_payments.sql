-- =====================================================================
-- VEYLO migration 002 — trip themes and payment details.
-- Safe to run more than once. Also appended to schema.sql for new projects.
-- =====================================================================

-- Trip style (drives cover art and colours in both apps).
alter table groups add column if not exists theme text not null default 'vacation';
alter table groups drop constraint if exists groups_theme_check;
alter table groups add constraint groups_theme_check check (theme in ('hills','beach','business','vacation','cafe'));

-- How a settlement was paid, the UPI transaction id, and whether it went through.
alter table settlements add column if not exists method     text not null default 'cash';
alter table settlements add column if not exists txn_id     text;
alter table settlements add column if not exists pay_status text not null default 'success';
alter table settlements drop constraint if exists settlements_method_check;
alter table settlements add constraint settlements_method_check check (method in ('cash','upi','other'));
alter table settlements drop constraint if exists settlements_pay_status_check;
alter table settlements add constraint settlements_pay_status_check check (pay_status in ('success','pending','failed'));
alter table settlements drop constraint if exists settlements_txn_id_check;
alter table settlements add constraint settlements_txn_id_check check (txn_id is null or char_length(txn_id) between 4 and 64);

create or replace function create_group(p_group jsonb, p_members jsonb, p_device text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare
  gid uuid := (p_group->>'id')::uuid;
  code text; me uuid; m jsonb;
begin
  if jsonb_array_length(p_members) not between 1 and 50 then raise exception 'A group needs 1–50 members'; end if;
  if exists (select 1 from groups where id = gid) then raise exception 'Group already exists'; end if;
  code := _new_invite_code();
  insert into groups(id, name, type, destination, currency, start_date, end_date, invite_code, theme)
  values (gid, trim(p_group->>'name'), p_group->>'type', coalesce(trim(p_group->>'destination'), ''), p_group->>'currency',
          nullif(p_group->>'start_date','')::date, nullif(p_group->>'end_date','')::date, code,
          coalesce(nullif(p_group->>'theme',''), 'vacation'));
  for m in select * from jsonb_array_elements(p_members) loop
    insert into members(id, group_id, name, device_id, upi_id)
    values ((m->>'id')::uuid, gid, trim(m->>'name'), nullif(m->>'device_id',''), nullif(m->>'upi_id',''));
    if m->>'device_id' = p_device then me := (m->>'id')::uuid; end if;
  end loop;
  if me is null then raise exception 'Creator must be a member'; end if;
  return jsonb_build_object('invite_code', code, 'member_token', _issue_token(me, gid), 'member_id', me, 'now', now());
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
    insert into settlements(id, group_id, from_id, to_id, amount, created_by, updated_by, deleted, method, txn_id, pay_status)
    values (sid, me.group_id, (p_settlement->>'from_id')::uuid, (p_settlement->>'to_id')::uuid,
            (p_settlement->>'amount')::bigint, me.id, me.id, coalesce((p_settlement->>'deleted')::boolean, false),
            coalesce(nullif(p_settlement->>'method',''), 'cash'), nullif(trim(p_settlement->>'txn_id'),''),
            coalesce(nullif(p_settlement->>'pay_status',''), 'success'))
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
  -- Amount and people are fixed. Only undo (soft delete), payment status and transaction id may change.
  update settlements set deleted = coalesce((p_settlement->>'deleted')::boolean, deleted),
         pay_status = coalesce(nullif(p_settlement->>'pay_status',''), pay_status),
         txn_id = coalesce(nullif(trim(p_settlement->>'txn_id'),''), txn_id),
         updated_by = me.id, updated_at = now(), revision = revision + 1
   where id = sid returning * into cur;
  insert into audit_log(group_id, entity, entity_id, action, actor, old_row, new_row)
  values (me.group_id, 'settlement', sid, 'update', me.id, null, to_jsonb(cur));
  return jsonb_build_object('status','ok','revision',cur.revision,'updated_at',cur.updated_at);
end $$;

grant execute on function create_group(jsonb, jsonb, text)  to anon, authenticated;
grant execute on function push_settlement(text, jsonb, int) to anon, authenticated;
