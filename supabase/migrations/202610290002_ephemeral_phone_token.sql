alter table public.couchlink_relay_commands add column if not exists phone_token text;

drop function if exists public.couchlink_claim_command(text);
create function public.couchlink_claim_command(p_device_id text)
returns table(command_id uuid, request jsonb, phone_token text)
language plpgsql security definer set search_path=public as $$
begin
  update public.couchlink_relay_devices set last_seen=now() where device_id=p_device_id;
  return query
  with next_command as (
    select c.id,c.phone_token from public.couchlink_relay_commands c
    where c.device_id=p_device_id and c.response is null and not c.claimed and c.created_at>now()-interval '2 minutes'
    order by c.created_at for update skip locked limit 1
  )
  update public.couchlink_relay_commands c set claimed=true,phone_token=null
  from next_command n where c.id=n.id
  returning c.id,c.request,n.phone_token;
end $$;
revoke all on function public.couchlink_claim_command(text) from public,anon,authenticated;
grant execute on function public.couchlink_claim_command(text) to service_role;
