-- Durable document associations for the existing patient chat-session tables.
-- These tables already exist in the connected project; creating them here as
-- well makes fresh installs reproducible without changing any patient data.

create table if not exists public.ai_chat_sessions (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.patients(id) on delete cascade,
  session_title varchar(120),
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.ai_chat_messages (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.ai_chat_sessions(id) on delete cascade,
  sender_type varchar(20) not null check (sender_type in ('user', 'assistant')),
  message_text text not null,
  created_at timestamptz not null default now()
);

create index if not exists ai_chat_sessions_patient_started_idx
  on public.ai_chat_sessions(patient_id, started_at desc);
create index if not exists ai_chat_messages_session_created_idx
  on public.ai_chat_messages(session_id, created_at desc);

alter table public.ai_chat_sessions enable row level security;
alter table public.ai_chat_messages enable row level security;

drop policy if exists ai_chat_sessions_select_own on public.ai_chat_sessions;
create policy ai_chat_sessions_select_own on public.ai_chat_sessions
  for select to authenticated using ((select auth.uid()) = patient_id);
drop policy if exists ai_chat_sessions_insert_own on public.ai_chat_sessions;
create policy ai_chat_sessions_insert_own on public.ai_chat_sessions
  for insert to authenticated with check ((select auth.uid()) = patient_id);
drop policy if exists ai_chat_sessions_update_own on public.ai_chat_sessions;
create policy ai_chat_sessions_update_own on public.ai_chat_sessions
  for update to authenticated using ((select auth.uid()) = patient_id)
  with check ((select auth.uid()) = patient_id);

drop policy if exists ai_chat_messages_select_own on public.ai_chat_messages;
create policy ai_chat_messages_select_own on public.ai_chat_messages
  for select to authenticated using (
    exists (select 1 from public.ai_chat_sessions s
      where s.id = session_id and s.patient_id = (select auth.uid()))
  );
drop policy if exists ai_chat_messages_insert_own on public.ai_chat_messages;
create policy ai_chat_messages_insert_own on public.ai_chat_messages
  for insert to authenticated with check (
    exists (select 1 from public.ai_chat_sessions s
      where s.id = session_id and s.patient_id = (select auth.uid()))
  );

alter table public.medical_documents
  add column if not exists session_id uuid references public.ai_chat_sessions(id) on delete set null;
create index if not exists medical_documents_patient_session_idx
  on public.medical_documents(patient_id, session_id, created_at desc);

-- The API uses a server-side service role only after validating the patient's
-- JWT and checking session ownership. The browser receives no write policies
-- for document associations and no service-role key.
