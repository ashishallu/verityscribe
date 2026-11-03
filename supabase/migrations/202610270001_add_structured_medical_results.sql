-- Store positioned, row-associated medical results beside the original OCR text.
-- Existing patient_embeddings RLS policies remain unchanged and continue to
-- restrict reads to the authenticated patient's own rows.

alter table public.patient_embeddings
  add column if not exists structured_results jsonb;

alter table public.patient_embeddings
  add constraint patient_embeddings_structured_results_array
  check (structured_results is null or jsonb_typeof(structured_results) = 'array');
