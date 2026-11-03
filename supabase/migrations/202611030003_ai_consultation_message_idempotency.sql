alter table public.ai_consultation_messages
  add column if not exists request_key text;

create unique index if not exists ai_consultation_messages_request_key_idx
  on public.ai_consultation_messages(session_id, request_key, role)
  where request_key is not null;
