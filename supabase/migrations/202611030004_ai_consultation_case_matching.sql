alter table public.ai_consultation_sessions
  add column if not exists similar_case_consent_at timestamptz;

create table if not exists public.ai_consultation_case_library (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null unique references public.ai_consultation_sessions(id) on delete cascade,
  hospital_id uuid not null references public.hospitals(id) on delete cascade,
  patient_id uuid not null references public.patients(id) on delete cascade,
  symptom_terms text[] not null check (cardinality(symptom_terms) between 1 and 24),
  search_document tsvector not null,
  created_at timestamptz not null default now()
);

create or replace function public.set_ai_consultation_case_search_document()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
  new.search_document := to_tsvector('english', array_to_string(new.symptom_terms, ' '));
  return new;
end;
$$;

create trigger ai_consultation_case_search_document_trigger
  before insert or update of symptom_terms
  on public.ai_consultation_case_library
  for each row execute function public.set_ai_consultation_case_search_document();

revoke all on function public.set_ai_consultation_case_search_document()
  from public, anon, authenticated;

create index if not exists ai_consultation_case_library_search_idx
  on public.ai_consultation_case_library using gin(search_document);
create index if not exists ai_consultation_case_library_hospital_idx
  on public.ai_consultation_case_library(hospital_id, created_at desc);

alter table public.ai_consultation_case_library enable row level security;
revoke all on public.ai_consultation_case_library from anon, authenticated;
grant all on public.ai_consultation_case_library to service_role;

create or replace function public.find_similar_ai_consultation_cases(
  p_hospital_id uuid,
  p_excluded_patient_id uuid,
  p_search_terms text,
  p_limit integer default 3
)
returns table (
  symptom_terms text[],
  similarity real
)
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select library.symptom_terms,
         ts_rank_cd(library.search_document, query.terms)::real
    from public.ai_consultation_case_library as library
    join public.ai_consultation_sessions as session
      on session.id = library.session_id
    cross join plainto_tsquery('english'::regconfig, coalesce(p_search_terms, '')) as query(terms)
   where auth.role() = 'service_role'
     and library.hospital_id = p_hospital_id
     and library.patient_id <> p_excluded_patient_id
     and session.status = 'approved'
     and session.similar_case_consent_at is not null
     and session.reviewed_at is not null
     and library.search_document @@ query.terms
   order by ts_rank_cd(library.search_document, query.terms) desc,
            library.created_at desc
   limit least(greatest(coalesce(p_limit, 3), 1), 3);
$$;

revoke all on function public.find_similar_ai_consultation_cases(uuid, uuid, text, integer)
  from public, anon, authenticated;
grant execute on function public.find_similar_ai_consultation_cases(uuid, uuid, text, integer)
  to service_role;
