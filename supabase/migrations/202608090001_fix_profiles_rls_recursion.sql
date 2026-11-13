-- Repair deployed policy drift that makes an RLS policy on profiles query
-- public.profiles again, which Postgres rejects with 42P17 (infinite recursion).
-- Keep RLS enabled and restore the repository's owner-only profile access model.
alter table public.profiles enable row level security;

do $$
declare
  policy_name text;
begin
  for policy_name in
    select policyname
    from pg_policies
    where schemaname = 'public' and tablename = 'profiles'
  loop
    execute format('drop policy if exists %I on public.profiles', policy_name);
  end loop;
end;
$$;

create policy "profile_self_read" on public.profiles
  for select to authenticated
  using ((select auth.uid()) is not null and id = (select auth.uid()));

create policy "profile_self_update" on public.profiles
  for update to authenticated
  using ((select auth.uid()) is not null and id = (select auth.uid()))
  with check ((select auth.uid()) is not null and id = (select auth.uid()));

grant select, update on public.profiles to authenticated;
