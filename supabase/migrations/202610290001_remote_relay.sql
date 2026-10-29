create table if not exists public.couchlink_relay_devices (
  device_id text primary key check (length(device_id) between 1 and 80),
  secret_hash text not null,
  token_hashes text[] not null default '{}',
  last_seen timestamptz not null default now()
);

create table if not exists public.couchlink_relay_commands (
  id uuid primary key default gen_random_uuid(),
  device_id text not null references public.couchlink_relay_devices(device_id) on delete cascade,
  request jsonb not null,
  phone_token text,
  response jsonb,
  claimed boolean not null default false,
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
create index if not exists couchlink_relay_pending on public.couchlink_relay_commands(device_id,created_at) where response is null;
alter table public.couchlink_relay_devices enable row level security;
alter table public.couchlink_relay_commands enable row level security;
revoke all on public.couchlink_relay_devices,public.couchlink_relay_commands from anon,authenticated;

create or replace function public.couchlink_claim_command(p_device_id text)
returns table(command_id uuid, request jsonb, phone_token text) language plpgsql security definer set search_path=public as $$
begin
  update public.couchlink_relay_devices set last_seen=now() where device_id=p_device_id;
  return query
  with next_command as (
    select c.id,c.phone_token from public.couchlink_relay_commands c
    where c.device_id=p_device_id and c.response is null and not c.claimed and c.created_at>now()-interval '2 minutes'
    order by c.created_at for update skip locked limit 1
  )
  update public.couchlink_relay_commands c set claimed=true,phone_token=null from next_command n where c.id=n.id
  returning c.id,c.request,n.phone_token;
end $$;

create or replace function public.couchlink_complete_command(p_device_id text,p_command_id uuid,p_response jsonb)
returns boolean language plpgsql security definer set search_path=public as $$
begin
  update public.couchlink_relay_commands set response=p_response,completed_at=now()
  where id=p_command_id and device_id=p_device_id and response is null;
  return found;
end $$;
revoke all on function public.couchlink_claim_command(text),public.couchlink_complete_command(text,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.couchlink_claim_command(text),public.couchlink_complete_command(text,uuid,jsonb) to service_role;
