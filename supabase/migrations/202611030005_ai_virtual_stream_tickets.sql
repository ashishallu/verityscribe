create table if not exists public.ai_consultation_voice_tickets (
  id uuid primary key,
  session_id uuid not null references public.ai_consultation_sessions(id) on delete cascade,
  patient_id uuid not null references public.patients(id) on delete cascade,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists ai_consultation_voice_tickets_expiry_idx
  on public.ai_consultation_voice_tickets(expires_at);

alter table public.ai_consultation_voice_tickets enable row level security;
revoke all on public.ai_consultation_voice_tickets from anon, authenticated;
grant all on public.ai_consultation_voice_tickets to service_role;

create or replace function public.consume_ai_consultation_voice_ticket(
  p_ticket_id uuid,
  p_session_id uuid,
  p_patient_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if auth.role() <> 'service_role' then
    return false;
  end if;
  update public.ai_consultation_voice_tickets
     set consumed_at = now()
   where id = p_ticket_id
     and session_id = p_session_id
     and patient_id = p_patient_id
     and consumed_at is null
     and expires_at > now();
  return found;
end;
$$;

revoke all on function public.consume_ai_consultation_voice_ticket(uuid, uuid, uuid)
  from public, anon, authenticated;
grant execute on function public.consume_ai_consultation_voice_ticket(uuid, uuid, uuid)
  to service_role;
