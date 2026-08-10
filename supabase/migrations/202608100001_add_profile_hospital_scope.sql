-- Canonical hospital assignment for hospital administrators.
alter table public.profiles
  add column if not exists hospital_id uuid references public.hospitals(id) on delete restrict;

create index if not exists profiles_hospital_id_idx on public.profiles(hospital_id);

comment on column public.profiles.hospital_id is
  'Authoritative hospital scope for hospital_admin profiles; assigned by a privileged administrative workflow.';
