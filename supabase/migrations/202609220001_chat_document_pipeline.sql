-- Private metadata for reports uploaded through the patient chat.
-- The FastAPI service writes with the service-role key only after validating
-- the patient's JWT; patients retain read-only access to their own metadata.

create table if not exists public.medical_documents (
  id uuid primary key,
  patient_id uuid not null references public.patients(id) on delete cascade,
  consultation_id uuid references public.consultations(id) on delete set null,
  bucket_path text not null,
  document_type text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.patient_embeddings (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.patients(id) on delete cascade,
  source_document_id uuid references public.medical_documents(id) on delete cascade,
  content text not null,
  created_at timestamptz not null default now()
);

create index if not exists patient_embeddings_patient_created_idx
  on public.patient_embeddings(patient_id, created_at desc);

alter table public.medical_documents enable row level security;
alter table public.patient_embeddings enable row level security;

drop policy if exists "patient_documents_self_read" on public.medical_documents;
create policy "patient_documents_self_read" on public.medical_documents
  for select to authenticated
  using ((select auth.uid()) = patient_id);

drop policy if exists "patient_embeddings_self_read" on public.patient_embeddings;
create policy "patient_embeddings_self_read" on public.patient_embeddings
  for select to authenticated
  using ((select auth.uid()) = patient_id);
