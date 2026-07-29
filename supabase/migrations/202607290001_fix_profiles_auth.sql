-- Keep profile creation server-side. Browser clients never need elevated keys.
create or replace function public.handle_new_user_profile()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles (id, full_name, role)
  values (
    new.id,
    coalesce(
      nullif(trim(concat_ws(' ', new.raw_user_meta_data ->> 'first_name', new.raw_user_meta_data ->> 'last_name')), ''),
      split_part(coalesce(new.email, 'User'), '@', 1)
    ),
    'staff'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile
  after insert on auth.users
  for each row execute procedure public.handle_new_user_profile();

-- Existing profiles remain private to their authenticated owner.
drop policy if exists "profile_self_read" on public.profiles;
create policy "profile_self_read" on public.profiles
  for select to authenticated
  using ((select auth.uid()) is not null and id = (select auth.uid()));

create policy "profile_self_update" on public.profiles
  for update to authenticated
  using ((select auth.uid()) is not null and id = (select auth.uid()))
  with check ((select auth.uid()) is not null and id = (select auth.uid()));

grant select, update on public.profiles to authenticated;
