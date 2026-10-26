# Medical report processing reliability report

**Date:** 2026-10-26  
**Verdict: PARTIALLY VERIFIED** — the production failure is localized and code-level validation, retry, and diagnostic fixes are covered by automated tests. Real Cloudflare inference and the complete patient/doctor review workflow remain unverified and require account activation plus application work described below. No production credentials, schema, environment variables, deployment, or Git remote were changed.

## Findings and execution trace

| Stage | Current implementation | Finding/status |
|---|---|---|
| Select file | `lib/features/ai_screen.dart` uses `file_picker`; accepts PDF/PNG/JPEG/WebP by extension; supplies bytes to `ClinicNotifier.uploadReport` | Works as a selection path, but no client size check; the server is authoritative. The UI's “PDF report” option is a document category, not an extension filter. A production request logged category `pdf` with actual `image/jpeg`; backend uses content signature for extraction, so that mismatch did not cause the 403, but stored category can be misleading. |
| Multipart/auth | `lib/providers/clinic_provider.dart` POSTs `/chat/reports` with Supabase bearer token, multipart field `document`, `report_type`, and stable `X-Upload-Id` | Contract matches FastAPI. Patient identity is derived by `get_current_patient`, not accepted from the body. Existing retry ID behavior is retained. |
| Type/size validation | `backend/app/routers/integration.py` | Fixed: read is capped at configured limit + 1 byte before retaining the file; MIME now comes from PDF/JPEG/PNG/WebP signatures instead of trusting a claimed MIME. `CHAT_REPORT_MAX_BYTES` defaults to 10 MB and is clamped to 64 KB–25 MB. |
| Private Storage | `patient-report-uploads-v2` by default; only backend service-role client writes after patient auth | Live Supabase says this bucket is `public=false`. Its Storage-level `file_size_limit` and `allowed_mime_types` are unset; application-side limits now bound reads. Newly provisioned buckets get private + size/MIME restrictions. No public URL is created. |
| PDF extraction | `pypdf` text extraction | Text-based PDFs are extracted directly with page markers. Fixed: password-protected, malformed, over-limit PDFs return specific validation errors. `CHAT_REPORT_MAX_PDF_PAGES` defaults to 25 and is clamped to 1–100. Scanned/mixed PDFs are not silently treated as successfully extracted: current runtime has no PDF rasterizer/OCR bridge, so an unreadable page returns `pdf_ocr_required`. Scanned-PDF support remains open. |
| Image extraction | `AIProvider.extract_document_text` selects Cloudflare when the Cloudflare account ID and token are present; otherwise existing Hugging Face route | The recent Render trace for request `hnkmw3lx2wfvkwfs` reached this path and Cloudflare returned HTTP 403. This is the confirmed production failure. The backend previously discarded Cloudflare's numeric error code, so the exact subtype (terms, token permission, account restriction, model access) cannot be recovered from that old log. |
| Metadata/text persistence | `medical_documents` then `patient_embeddings`, with `source_document_id` | Both tables exist in live Supabase and have patient self-read RLS policies. Fixed: success is verified against both writes; a retry no longer short-circuits on metadata alone when extracted text is missing. First-attempt partial metadata is rolled back best-effort. Concurrent duplicate requests are not protected by a unique constraint on `patient_embeddings.source_document_id`; see blockers. |
| Clinical records/review | Existing schema has `reports`, `prescriptions`, and subtype tables | Chat uploads currently write only `medical_documents` and extracted text; they do **not** create a `reports` or `prescriptions` row. This avoids converting unverified OCR into authoritative clinical data, but means there is no structured extraction draft, clinician approval screen, or doctor review route for these chat uploads. The normal `/me/reports` and Patient Reports screen query `reports`, so uploaded chat documents do not appear there. This workflow is not complete. |
| Chatbot | `POST /chat` uses a separate `AIProvider.answer` route (`AI_LLM_BASE_URL` or Hugging Face); record context uses authenticated patient ID | The upload 403 is not the same request path as general chat. Extracted text is stored with its source document and personal answers label it as OCR/unverified; the chatbot must not describe it as a verified diagnosis/prescription. General chat/provider reliability was not live-tested in this task. |

## Root cause: what is known and what is not

The most recent Render request log reports `Cloudflare vision document extraction failed (HTTP 403)` after a JPEG upload reached the backend. The code path uses Cloudflare's account-scoped REST endpoint:

`POST https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model_id}`

The repository default model is `@cf/meta/llama-3.2-11b-vision-instruct` (`CLOUDFLARE_VISION_MODEL` can override it; the effective Render override was not exposed/verified). The implementation sends a bearer-authenticated JSON request containing a base64 data-URL `image`, `messages`, `temperature`, and `max_tokens`, then reads `result.response`. The documented endpoint, bearer authorization, image input, `messages`, and response field are consistent with Cloudflare's [official model guide](https://developers.cloudflare.com/workers-ai/guides/tutorials/llama-vision-tutorial/) and [model page](https://developers.cloudflare.com/workers-ai/models/llama-3.2-11b-vision-instruct/). Mocked request/response tests confirm the current wire shape; that is not evidence of live access.

Cloudflare's official [error table](https://developers.cloudflare.com/workers-ai/platform/errors/) maps several distinct failures to 403, including `5016` (Llama model terms not accepted), `5018`/`3041` (model/account access), `3023` (account blocked), and `5035` (model requires Workers Paid). The model guide specifically requires a one-time agreement request with `{ "prompt": "agree" }` before normal use. The old app log preserved only HTTP status, so we **cannot claim** the deployed 403 was definitely code `5016` or that the token itself is invalid.

The backend now parses only Cloudflare's numeric error code (never its message/body) and classifies known cases. Subsequent logs will include request ID, category, HTTP status, provider code, retained-file flag, and duration; no token, image, prompt content, OCR text, or provider error body is logged. The uploaded file is retained privately for retry for known access, terms, quota, and capacity errors, and the patient-facing message is actionable. It is never sent to a paid fallback after a Cloudflare failure.

### Required owner action to resolve the 403

If Cloudflare returns code `5016`, the Cloudflare account owner must perform its one-time model agreement for this exact model, as Cloudflare's guide instructs. This is a legal/terms acceptance and the app must not do it automatically. If the next log reports another code, fix that precise permission/account/model issue instead. Do not send or paste the API token into chat. After the account owner completes activation, retry the same upload; the stable upload ID lets the backend reuse the same private object path.

## Pricing and provider decision

The repository default vision model is a genuine vision-capable model, not a text-only or image-generation model. Cloudflare lists it as vision-capable with token-based neuron pricing on its [model page](https://developers.cloudflare.com/workers-ai/models/llama-3.2-11b-vision-instruct/). Current Workers AI pricing documents 10,000 Neurons/day on Workers Free; requests beyond that fail on Workers Free. On Workers Paid, usage above the daily allocation is billed at $0.011 per 1,000 Neurons. Therefore it is **free only within the daily allocation**, not unlimited; the effective account plan is not verified here. The deployed code uses the direct Workers AI REST route and has no automatic paid provider fallback. The Hugging Face route remains the legacy path only when Cloudflare credentials are absent; its effective free quota/pricing is not verified, and it was not called for the recent production request.

## Supabase/security evidence

Read-only inspection of live project `lsfhluvsrucwmbjvqwsr` confirmed:

- `medical_documents` columns include `id`, `patient_id`, `consultation_id`, `bucket_path`, and `document_type`.
- `patient_embeddings` includes `patient_id`, `source_document_id`, `content`, and `created_at`.
- `reports` and `prescriptions` are separate clinical tables with separate types/relationships; no OCR values should be inserted into them without a reviewed workflow.
- `medical_documents` and `patient_embeddings` have authenticated self-read policies; report/prescription patient/doctor select policies are present. No RLS policy was weakened or changed.
- The live migration history does not show the repository's `202609220001_chat_document_pipeline` migration, although the relevant tables and self-read policies already exist in live DB. This repo/live migration drift needs reconciliation before any future schema migration.
- The Storage bucket `patient-report-uploads-v2` is private, with no bucket-level size/MIME constraints. Backend request validation remains the control for current uploads.

## Changes in this pass

- `backend/app/services/ai_service.py`: introduced safe structured Cloudflare document-provider error categories; extracts numeric provider error code only; keeps secrets and response body out of the raised exception.
- `backend/app/routers/integration.py`: signature-based MIME validation, bounded request read, specific PDF errors/page cap, bucket restrictions for newly provisioned buckets, provider failure diagnostics and safe retry retention, strict persistence verification, and retry repair when prior metadata lacks its extracted text.
- `backend/.env.example`: documents safe configurable upload and PDF-page limits.
- `backend/tests/test_cloudflare_document_extraction.py`: tests the Cloudflare 5016 terms case and redaction while preserving existing request/response and quota behavior coverage.
- `backend/tests/test_report_processing_validation.py`: synthetic signature, size, corrupt/encrypted/over-page PDF, and incomplete-retry checks.
- No ASR files, Flutter files, SQL migrations, production secrets/settings, deploys, or pushes were changed.

## Verification

- `python -m unittest discover -s tests -v`: **31 tests passed** (includes ASR regression tests; all ASR code/config was left unchanged).
- New Cloudflare/document tests use mocked provider responses and synthetic local PDF/image bytes.
- Live Render evidence: one real upload reached Cloudflare and failed with HTTP 403. No successful real OCR/inference was run in this pass.
- No synthetic document was sent to a real external inference service; no production data was written or altered.
- Full Patient App → Storage → OCR → structured draft → doctor review → approved record → chatbot retrieval is **not** end-to-end verified.

## Remaining blockers and next actions

1. Owner must activate/authorize `@cf/meta/llama-3.2-11b-vision-instruct` in Cloudflare (or supply the next sanitized numeric error code to identify the distinct 403 cause). No production token/permission was changed here.
2. Redeploy the backend changes only after review/authorization, then run a synthetic image inference test and verify a usable transcript plus database writes. The model must not be called working based on unit tests alone.
3. Add a PDF renderer and bounded page-by-page OCR path for scanned/mixed PDFs only after selecting and verifying a free-compatible implementation and Render memory/runtime impact. Current implementation rejects these clearly rather than silently dropping pages.
4. Design a review-draft persistence/workflow (source-linked structured schema, status, patient view, authorized doctor queue, approve/reject) before writing OCR output to `reports`, `prescriptions`, or subtype tables. Existing chat uploads do not currently appear in the normal Reports list.
5. Add a uniqueness/transaction strategy for `patient_embeddings.source_document_id` after checking existing duplicate data; current app-level retry recovery cannot guarantee race-free deduplication across concurrent identical requests without a database constraint/transaction.
6. Test patient and doctor ownership boundaries, signed private-source viewing, and chatbot exclusion/labeling of unreviewed drafts after the review workflow is designed.

**Final verdict: PARTIALLY VERIFIED.** The production failure is confirmed to be at Cloudflare Vision (HTTP 403); the exact 403 subtype is still unknown. Code-level failure classification, bounded validation, and incomplete-retry behavior are covered by automated tests. Live inference, scanned-PDF OCR, structured draft/review persistence, and complete UI-to-chat end-to-end behavior remain unverified.
