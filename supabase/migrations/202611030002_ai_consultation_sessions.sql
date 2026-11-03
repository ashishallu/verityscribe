create table if not exists public.ai_consultation_sessions (
  id uuid primary key default gen_random_uuid(),
  appointment_id uuid not null unique references public.appointments(id) on delete cascade,
  consultation_id uuid unique references public.consultations(id) on delete set null,
  patient_id uuid not null references public.patients(id) on delete cascade,
  doctor_id uuid not null references public.doctors(id),
  hospital_id uuid not null references public.hospitals(id),
  mode text not null check (mode in ('ai_chat', 'ai_virtual')),
  status text not null default 'in_progress' check (status in ('in_progress','processing','awaiting_doctor_review','approved','rejected','failed')),
  consent_at timestamptz not null,
  voice_consent_at timestamptz,
  summary jsonb,
  review_note text,
  reviewed_by uuid references public.doctors(id),
  reviewed_at timestamptz,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ai_virtual_requires_voice_consent check (mode <> 'ai_virtual' or voice_consent_at is not null)
);

create table if not exists public.ai_consultation_messages (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.ai_consultation_sessions(id) on delete cascade,
  role text not null check (role in ('patient','assistant','system')),
  content text not null check (length(content) <= 12000),
  created_at timestamptz not null default now()
);

create table if not exists public.ai_consultation_review_events (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.ai_consultation_sessions(id) on delete cascade,
  doctor_id uuid not null references public.doctors(id),
  action text not null check (action in ('approved','rejected','request_information')),
  before_summary jsonb,
  after_summary jsonb,
  note text,
  created_at timestamptz not null default now()
);

create index if not exists ai_consultation_sessions_patient_started_idx
  on public.ai_consultation_sessions(patient_id, started_at desc);
create index if not exists ai_consultation_sessions_doctor_status_idx
  on public.ai_consultation_sessions(doctor_id, status, started_at desc);
create index if not exists ai_consultation_messages_session_created_idx
  on public.ai_consultation_messages(session_id, created_at);
create index if not exists ai_consultation_review_events_session_created_idx
  on public.ai_consultation_review_events(session_id, created_at);

alter table public.ai_consultation_sessions enable row level security;
alter table public.ai_consultation_messages enable row level security;
alter table public.ai_consultation_review_events enable row level security;
-- Deliberately no authenticated client policies. FastAPI uses service role only
-- after validating JWT ownership/doctor assignment; clients never receive it.
