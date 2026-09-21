-- Voice recordings contain protected health information. They are never
-- public; the authenticated backend performs ownership checks before storage
-- operations using its server-only service-role client.
insert into storage.buckets (id, name, public, file_size_limit)
values ('voice-recordings', 'voice-recordings', false, 52428800)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit;
