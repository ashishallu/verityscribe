# Medical report processing reliability report

> **Chatbot outage diagnosis and local fix — 2026-10-27.** This supersedes earlier statements below that general chat was not investigated. Read-only Render logs for `verityscribe-1` on 2026-10-26 show the session-message insert and history query succeeded, then `AIProvider.answer` failed at `https://router.huggingface.co/nscale/v1/chat/completions` with HTTP **402 Payment Required**. This is a Hugging Face routed-inference billing/credit failure, not OCR, report retrieval, or session creation. The working tree now routes general chat, attached-report questions, and structured report summaries to Cloudflare Workers AI model `@cf/qwen/qwen3-30b-a3b-fp8` using the existing server-side Cloudflare account/token and documented `messages` request shape. There is no Hugging Face fallback. Mocked tests pass; no real Cloudflare inference was run and this fix has **not been deployed**, so the live chatbot remains unverified until deployment and a real request. Cloudflare documents 10,000 free Workers AI Neurons/day on Workers Free; requests above that fail on Free, while Workers Paid usage above the allocation is billable. The current Cloudflare account plan/quota was not inspected; confirm it is Workers Free to avoid paid overage.

> **Pre-deploy live failure diagnosis — 2026-10-26 15:04 UTC.** Both services were on `b70a675c8886f30dcf7fa3899651cbd6ec023cee`, which invoked Cloudflare Vision. Request `hnkp9gix2w94w7ek` passed authentication and private Storage upload, then Vision returned HTTP 403/code `5016`; FastAPI returned 503. The private object remained and no matching `medical_documents` or `patient_embeddings` rows existed. This localized the then-current failure to the old hosted Vision stage.

> **Deployment update — 2026-10-26 15:44 UTC.** The scoped OCR commit `4660488e8200b5fc841f5831eafdb5fac9082225` was pushed to `master`; both Render services now report deploy status `live` on that commit. Their Docker build logs show Tesseract 5.5.0, `tesseract-ocr-eng`, `tesseract-ocr-kan`, `pypdfium2==5.14.0`, and Pillow 12.2.0 installed successfully. Both `/health` endpoints return HTTP 200 and release `4660488e8200`; both report `environment="development"`, despite the checked-in `render.yaml` declaring `ENVIRONMENT=production`. No authenticated synthetic document upload has yet been made, so this verifies deployment/build/runtime readiness only—not OCR output or persistence. No patient data or secrets were used.

## Live request trace for the persistent error

- Before deployment, both services were on `b70a675...`; after pushing `4660488e8200b5fc841f5831eafdb5fac9082225`, Render showed both corresponding deploys `live`: `verityscribe-1` deploy `dep-dbfna6ks728c73dqd5hg`; `verityscribe` deploy `dep-dbfna6ks728c73dqd5f0`. Service IDs/build contexts: `srv-d9l1ghtaeets73fnlbng` uses Docker context `backend`, Dockerfile `backend/Dockerfile`; `srv-d9l19qoae00c7388iu50` uses root directory `backend`, context `.`, Dockerfile `Dockerfile`. Both build logs show Tesseract 5.5.0 and English/Kannada trained data installed; both show successful install of pypdfium2 5.14.0 and Pillow 12.2.0. Both health checks return release `4660488e8200`. `/health` reports `environment=development` on both services, while the committed Render blueprint declares `ENVIRONMENT=production`; Render MCP does not expose runtime env values, so the source of this difference remains unresolved. The health response is not evidence of OCR recognition.
- Exact latest request trace: request/correlation ID `hnkp9gix2w94w7ek`; `POST /api/v1/chat/reports`; `type=prescription`, 28,930 bytes, `image/jpeg`. At `15:04:28.862791448Z` the route logged receipt; at `15:04:30.711306747Z` it logged `Chat report stored privately`; at `15:04:31.512558497Z` it logged `Report provider failed`, category `vision_model_terms_required`, HTTP 403, provider code `5016`, `retained=True`, elapsed 2650 ms; at `15:04:31.513415693Z` the response was HTTP 503. Authentication, validation, bucket lookup, and upload succeeded. The first failing stage is the legacy Cloudflare Vision request—not Storage, local OCR, structured extraction, or database persistence. No token, image bytes, or medical text are included here.
- Read-only Supabase checks for this request found one private Storage object and zero matching rows in `public.medical_documents` and `public.patient_embeddings`. The latest object was retained; no extraction metadata/text persistence occurred. Earlier request `hnkmw3lx2wfvkwfs` (13:38 UTC) removed its object under the older unclassified-error behavior; that is not what happened for the latest `retained=True` request. No RLS or bucket policy was changed.
- Render MCP service/deploy/log inspection does not expose environment variable values or remote shell. Dashboard inspection timed out, so the two services' runtime environment key sets cannot be compared from available evidence. No secret values were requested, printed, or changed. The sanitized `5016` terms-required response is evidence against diagnosing this request as a missing/invalid token.
- Flutter's current `ClinicNotifier.uploadReport` parses a non-2xx JSON body's `detail` into `ApiException`, and `AiScreen` displays that message. Its generic “The report could not be uploaded” fallback is used only when no `ApiException` reaches the screen. That generic wording can therefore indicate a stale/different Flutter client or a non-JSON/unexpected response; it does not change the confirmed backend 503 cause for the logged requests.
- The Render MCP exposes service, deploy, and log inspection but no shell/exec command. Direct SSH to the advertised service host stopped at host-key verification, so no remote executable/language-data/OCR command was run. Build logs confirm packages were installed during image construction, not that Tesseract recognized a document at runtime.

**Confirmed root cause of the observed pre-deploy failure:** Cloudflare Vision returned a model terms-required response (`5016`). The OCR replacement has since been deployed to both services. A new production document request is required to determine whether the new OCR route recognizes and persists a real synthetic document.

> **Current implementation update — 2026-10-26 (supersedes the earlier findings below).** The image route no longer calls Cloudflare Vision or Hugging Face image inference. Scanned and mixed PDFs now have a local OCR path. The earlier report below is retained as history; it describes the pre-replacement state and must not be read as the current implementation.

## Current result

**Verdict: DEPLOYED, END-TO-END OCR UNVERIFIED.** Both services are live on the OCR commit; the image builds installed the Tesseract executable/language packages and PDFium/Pillow, and both health endpoints report the release ID. Local backend tests (38) and targeted Flutter analysis pass. No authenticated synthetic upload has gone through the production Storage → OCR → Supabase persistence → Reports-list path, so do not claim the upload feature is fixed yet.

### Synthetic reproduction performed in this investigation (local only)

These were real local execution attempts against the current working-tree implementation, not mocked OCR, and did **not** contact production:

| Synthetic input / check | Result | What it establishes |
|---|---|---|
| Synthetic printed PNG passed to local `extract_document_text` | `engine_not_installed` locally; generated fixture is available at `ocr_synthetic_test_uploads/verityscribe_ocr_synthetic.png` | Local workstation lacks Tesseract; this is not a result from Render. |
| Synthetic text PDF through current local extractor | Passed; extracted expected sentence. Fixture: `ocr_synthetic_test_uploads/verityscribe_ocr_text.pdf` | Local embedded-PDF-text path works on this sample. |
| Synthetic scanned PDF through current local extractor | PDFium rendered it; Tesseract then failed `engine_not_installed`. Fixture: `ocr_synthetic_test_uploads/verityscribe_ocr_scanned.pdf` | Local rendering works; local OCR is blocked by missing executable. |
| Deployed Tesseract/language availability | Build logs confirm install of Tesseract 5.5.0, `eng`, and `kan` in both service images. Direct read-only SSH check was blocked at host-key verification; no command ran inside the container. | Image build installation is evidenced; deployed executable/language listing and actual recognition are not verified. |
| Authenticated production synthetic PNG/PDF upload through Flutter → API → private Storage → OCR → DB → Reports list | Not run after deployment | No production patient session/JWT or approved diagnostic upload path was available. Earlier prescription trace is only evidence of the old Vision failure. |

**Precise unresolved boundary:** the old failure is confirmed as Cloudflare Vision `403/5016`, and the replacement is now live. Build logs establish the binary/data packages were installed in both images. However no direct command or authorized synthetic upload could be run inside production. The remaining proof is an authenticated synthetic upload, then read-only confirmation of the private object, extracted draft rows, and `/me/reports` result. Both health endpoints currently report `environment=development` although `render.yaml` says `production`; runtime env values are not exposed in the Render MCP, so that config mismatch also needs owner-side inspection.

## Current pipeline and changes

1. Patient uploads are authenticated; file bytes are signature-checked, bounded to the existing 10 MB default, and image dimensions/format are validated before private Storage writes. Image OCR is run in a worker thread. PDF processing keeps the existing 25-page limit and extracts text directly when available; scanned pages are rendered one at a time with PDFium, then OCRed. Mixed pages preserve both embedded and separately recognized text, page labels are retained, OCR is capped at 35 seconds per page and 90 seconds per PDF request, and a page/image that cannot be verified or read fails rather than disappearing silently.
2. **OCR engine:** Tesseract OCR (`tesseract-ocr` plus English and Kannada trained data; invoked as `tesseract stdin stdout -l eng+kan --oem 1 --psm 6`). It is a local executable, not a hosted model/API. Pillow applies EXIF orientation, grayscale/contrast normalization, and bounded downscaling. `pypdfium2` provides local PDF page rendering; `pypdf` continues embedded-text extraction and encryption/page-count validation. OCR processes pages serially to avoid loading a vision model or several page images at once.
3. The original upload remains in the existing private Supabase bucket; extracted text and metadata continue to be persisted in `medical_documents` and `patient_embeddings` through the existing authenticated patient → service-side write path. `/me/reports` now includes the patient's own uploaded drafts in the existing Reports list without creating an authoritative `reports` row. The UI visibly labels these as **Unverified OCR draft**. Upload success invalidates the Reports-list provider so it refreshes.
4. The chatbot route is separate from OCR. It queries `patient_embeddings` filtered by the authenticated patient ID and personal-prescription answers explicitly call OCR output unverified. The current working tree uses Cloudflare Workers AI for general health answers, attached-report Q&A, and structured report summaries; the deployed pre-fix chatbot used Hugging Face routed inference and failed with HTTP 402 as described above. OCR remains local and does not send raw image files to the chat model.
5. No change was made to the four working ASR models, their request formats, or the turbo audio-format fix. `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN` are used server-side by ASR, general/report chat, and report summaries; OCR itself remains local. `CLOUDFLARE_VISION_MODEL` was removed from Render blueprint and backend example configuration. No secrets were read or changed.

## Why this OCR option; rejected alternatives

The known production symptom was an HTTP 403 from the former Cloudflare Vision route. The prior implementation did not retain the provider's numeric error code, so the exact 403 subtype (account/model access, terms, token permissions, or plan) cannot be established from the available sanitized log. Instead of continuing that dependency, this implementation removes the Vision call entirely.

The chosen engine is Tesseract because it is a local, permissively licensed OCR engine with published command-line input/output, and its language data includes English and Kannada. It has no hosted-inference price or API quota; the incremental cost is container image size and Render CPU/RAM. Render's published Free web-service limit is 0.1 CPU and 512 MB RAM, so a sequential classical OCR engine is materially more suitable than loading a multi-billion-parameter vision-language model. Actual Render memory and latency have **not** been measured. Tesseract is intended for printed text and is not dependable for handwritten prescriptions; medical names, doses, decimals, units, tables, and handwriting must remain explicitly unverified.

Candidate review:

| Candidate | Evidence and fit | Decision |
|---|---|---|
| Tesseract 5 + Pillow + pypdfium2 | Local OCR on image bytes; local PDF page rendering; no remote inference fee/quota. English + Kannada training data. Low-complexity CPU path, sequential and bounded. | Selected for the first zero-hosted-inference implementation. Needs live OCR-quality and memory tests before claiming production reliability. |
| `microsoft/trocr-base-printed` | Hugging Face marks it Image-to-Text, but its card says it is not deployed by an Inference Provider; it is a 0.3B-parameter line-image recognizer, not a PDF processor. Hosted route/free pricing therefore is not available to verify; local model weights add runtime and memory beyond the selected classical OCR path. | Not selected. |
| PaddleOCR | A dedicated OCR family and plausible local alternative, but it adds another model/runtime stack and CPU/memory footprint. No deployment or medical-document benchmark was run in this task. | Not selected for the constrained first version; may be benchmarked later using synthetic/authorized data. |
| Qwen / DeepSeek VLMs | Suitable families for image understanding/OCR, but hosted availability and free inference are account/provider-specific; public weights do not establish a zero-cost hosted endpoint. Local large VLM inference does not fit the known Render Free 512 MB plan. | Not selected; no hidden paid inference or fallback. |

The implementation is local OCR only. Thus “availability” means that the container build must install the OS Tesseract executable, English/Kannada data, and pinned Python renderer/image packages; local host availability is **not verified** here. The OCR API uses PNG bytes on stdin and reads plain UTF-8 text from stdout. PDFium renders one PDF page to a Pillow image before the same OCR request. There is no model response JSON to parse and no external endpoint to authenticate.

Official references:

- [Tesseract User Manual](https://tesseract-ocr.github.io/tessdoc/) and [Tesseract language/script support](https://tesseract-ocr.github.io/tessdoc/Data-Files-in-different-versions)
- [Tesseract license and dependencies](https://github.com/tesseract-ocr/tesseract)
- [`pypdfium2` official Python API](https://pypdfium2.readthedocs.io/en/stable/python_api.html) and [PyPI license/package metadata](https://pypi.org/project/pypdfium2/)
- [Pillow `ImageOps.exif_transpose`](https://pillow.readthedocs.io/en/latest/reference/ImageOps.html)
- [TrOCR model card](https://huggingface.co/microsoft/trocr-base-printed) and [Transformers TrOCR OCR API](https://huggingface.co/docs/transformers/model_doc/trocr)
- [Render compute plans](https://render.com/docs/compute-plans)
- [Supabase private bucket access](https://supabase.com/docs/guides/storage/buckets/fundamentals)

## Current files and verification

- `backend/app/services/document_ocr.py`: local image preprocessing, bounded Tesseract execution, PDFium scanned-page rendering, mixed-page text preservation, and no-readable-page failure.
- `backend/app/services/ai_service.py`: document images use local OCR; general/report chat and structured summaries use Cloudflare Workers AI; ASR routes remain unchanged. Hugging Face is not used as a chatbot fallback.
- `backend/app/routers/integration.py`: local async-thread extraction; upload size/type/page/dimension validation; uploaded documents are merged into patient `/me/reports` as unverified drafts; the chatbot continues to retrieve only patient-filtered text.
- `backend/Dockerfile`, `backend/requirements.txt`, `backend/.env.example`, `render.yaml`: install local OCR/render dependencies and remove Vision-only configuration. Cloudflare account/token remain for ASR, not document processing.
- `lib/features/live_clinical_screen.dart`, `lib/features/ai_screen.dart`: identify uploaded extraction as unverified in the Reports page and refresh that list after upload.
- `backend/tests/test_document_ocr.py`, `backend/tests/test_patient_document_integration.py`: mocked image OCR, image bounds/errors, OCR timeout, text/scanned/mixed PDF behavior, patient-scoped retrieval, and unverified Reports-list presentation.
- `python -m unittest discover -s tests -v` from `backend`: **38 tests passed**. OCR and Supabase integration tests are mocks/unit tests, not proof of production inference or storage.
- Both Render Docker builds completed and installed Tesseract 5.5.0, English/Kannada data, pypdfium2 5.14.0, and Pillow 12.2.0; both deploys are live on `4660488e8200b5fc841f5831eafdb5fac9082225`. Health responses identify the release, but direct executable invocation and OCR recognition in the deployed container remain unverified.
- Read-only Supabase checks for the prior Cloudflare failures found no metadata or extracted-text rows; the latest failed upload's private object was retained. No post-deployment upload has yet been used to test Storage → OCR → persistence → Reports retrieval.
- The scoped commit was pushed and both services deployed. No Render environment value, Supabase schema/RLS, or production document data was changed.

## Remaining blockers before completion

1. Run a real OCR test on the provided synthetic printed PNG and scanned PDF through the authenticated production upload path, then verify the returned text, private Storage object, `medical_documents`/`patient_embeddings`, and `/me/reports`. Do not upload real patient material to external inference.
2. Measure latency, peak memory, and timeout behavior under the actual Render plan. The 35-second per-page timeout and serial page processing limit worst-case work but do not make OCR fast on 0.1 CPU; users may see longer latency for multi-page scans.
3. Tesseract can fail on handwriting. For prescriptions, display the source and require a clinician/pharmacist check; do not transcribe uncertain medicines/doses into an authoritative prescription. No structured LLM extraction was added because a zero-cost hosted model route was not verified and no paid inference was authorized.
4. A clinician review queue/approval flow is still absent. Chat uploads have no chosen `consultation_id` or assigned doctor in the existing schema; exposing all patient uploads to all doctors or fabricating a clinical `reports` row would violate patient scope. Add an explicit patient/consultation assignment and doctor-owned review workflow before turning draft text into clinical records.
5. **Exact next action:** upload the provided synthetic PNG, text PDF, and scanned PDF using a signed-in test patient account, or provide access to an authorized test session. The assistant does not have a patient JWT/session and will not bypass authentication or RLS. After the upload, inspect its correlation ID and read-only Supabase rows to complete production validation. Also have the owner inspect why both live `/health` endpoints report `environment=development` while `render.yaml` specifies `production`; runtime environment values are not exposed by the connected Render tools.

---

## Historical findings (before the latest 2026-10-26 production trace)

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

**Historical verdict (superseded by the live trace above): PARTIALLY VERIFIED.** At the time of that earlier investigation, the Cloudflare 403 subtype was unknown. The newer Render evidence above identifies provider code `5016` and the current request's first failing stage.

## Updated conclusion (2026-10-26, this investigation)

**The previously observed production failure was localized to Cloudflare Vision HTTP 403/code `5016` (`vision_model_terms_required`) and has been replaced in the deployed code.** Both Render services are now live on `4660488e8200b5fc841f5831eafdb5fac9082225`, and their successful image-build logs show the required local OCR packages installed. The new authenticated production upload path has not yet been exercised, so document OCR and subsequent persistence remain unverified—not declared fixed.

The scoped OCR commit has been deployed to both services. Local synthetic checks: searchable PDF extraction passed; PDFium rendered the scanned PDF, but local Tesseract was absent so OCR could not run. Production build logs confirm Tesseract/language packages were installed. The live API health endpoints return HTTP 200 and the deployed release, but direct `tesseract` invocation and authenticated upload/persistence checks remain outstanding. Automated checks: `python -m unittest discover -s tests -q` — 38 passed; `python -m compileall -q app tests` — passed; targeted Flutter `flutter analyze` — passed; `git diff --check` — passed. These do not substitute for the missing production document upload.

**Exact remaining blocker / next action:** production upload requires a signed-in patient session. Use the synthetic files at `ocr_synthetic_test_uploads/` in the app, then share the upload time/correlation ID so the subsequent backend logs and read-only database state can be inspected. No authentication or patient ownership checks will be bypassed. The services also report `environment=development` despite the blueprint value; the owner should inspect runtime configuration before changing it. No credentials, RLS, or clinical data were altered.

## Session-scoped document Q&A and handwriting follow-up (2026-10-26)

**Verdict: PARTIALLY VERIFIED.** Durable session isolation is implemented in source and covered by automated tests. The migration has deliberately **not** been applied to Supabase and this code has not been deployed. Handwriting recognition is **not improved or verified** in this pass; the current Tesseract engine remains a limitation.

### Handwriting diagnosis and model decision

The image path calls local Tesseract using `eng+kan`, OEM 1, PSM 6, after EXIF orientation correction, downscaling to at most 3000 pixels per side, grayscale conversion, and autocontrast. It returns raw Tesseract output; there is no downstream LLM rewrite in this path. This whole-page printed-text OCR configuration is the likely source of garbling on cursive drug names, doses, and mixed printed/handwritten layouts. The recognition engine (not an output parser or an LLM) is the confirmed capability mismatch; no live ground-truth sample was run here to quantify its error rate.

I did not integrate a speculative hosted model. Microsoft's [TrOCR base handwritten model card](https://huggingface.co/microsoft/trocr-base-handwritten) describes an IAM-finetuned, MIT-licensed image-to-text model intended for **single text-line images**. That does not establish suitability for mixed-layout prescriptions or Kannada clinic text, nor prove an accessible, free hosted endpoint for this account. Hugging Face's [Inference Providers pricing](https://huggingface.co/docs/inference-providers/pricing) currently documents $0.10 monthly credits for free users (subject to change) and pay-as-you-go after credits if enabled; this is not a guarantee of unlimited free inference. No free, accessible handwritten-medical model was verified, and no external inference or patient document was submitted. Choosing a model without validating the handwriting/script, endpoint, account access, resource budget, and billing controls would risk inaccurate clinical output or unexpected cost.

The existing pipeline continues to preserve source bytes privately and raw extracted text in `patient_embeddings.content`; messages and reports label this as unverified OCR. No confidence score is fabricated, and machine text is not written to the authoritative prescriptions table. No new structured medication extractor was added because there is no verified evidence-quality recognition source to ground it. Medication names/doses therefore remain subject to clinician/pharmacist confirmation. No comparative handwriting dataset or character/word-error benchmark was available; no accuracy improvement is claimed.

### Durable session document association

Live Supabase schema inspection found existing `public.ai_chat_sessions` and `public.ai_chat_messages` tables with patient ownership and RLS policies. `medical_documents` had no session link. Source changes now:

- Add `session_id` to `medical_documents` through `supabase/migrations/202610260001_chat_session_document_context.sql`, with an index and nullable FK to the existing chat session (legacy documents remain unassociated).
- Add authenticated patient endpoints to create/end a session, list its successfully extracted documents, and detach a document without deleting the original record. Sessions are resolved by both session ID and authenticated patient ID; ended or foreign sessions are rejected.
- Require a session ID for new chat uploads. The backend validates session ownership before Storage writes, persists/validates the document-session link, preserves upload retry behavior, and prevents reusing an upload ID attached to a different session.
- Include the active session ID on Flutter chat and multipart uploads. Clear conversation ends the session and starts a new one on the next message/upload. Attached-document chips show what is available in this chat and allow removal; removal only clears the session association.
- Persist chat turns in `ai_chat_messages` and bound retrieved history to ten rows. On each turn, document retrieval filters by authenticated `patient_id` and exact `session_id`, then fetches OCR rows only for those document IDs. The model receives bounded source-line excerpts and is instructed not to guess, change OCR text, or present it as clinician-approved. Explicit wider-record questions continue through the existing patient-scoped path; allergy checks remain active.

The migration source recreates the observed session/message table definitions and patient RLS policies idempotently for fresh installations, keeps RLS enabled, and adds no browser write policy for document associations. **Do not deploy the backend before applying and verifying this migration**: the currently deployed schema does not yet have `medical_documents.session_id`. Live RLS/migration state is not changed by this investigation.

### Tests and verification

- New `backend/tests/test_chat_session_documents.py` covers rejection of foreign/ended sessions, strict patient+session document selection, bounded literal evidence snippets, chat prompt isolation, and session message writes. These are mocked database/LLM tests, not production inference.
- `python -m unittest discover -s tests -q`: **45 tests passed**, including ASR regressions and five session/document isolation tests.
- `python -m compileall -q app tests`: passed.
- `flutter analyze lib/providers/clinic_provider.dart lib/features/ai_screen.dart`: passed with no issues.
- `git diff --check`: passed (Git reports only its existing LF-to-CRLF working-copy notice).
- No real handwriting inference, authenticated upload → session association → Q&A → removal → restart test, or production persistence test was run. The Flutter edits and final regression suite still require analysis/recheck.
- The four configured ASR model identifiers and ASR implementation were not edited. No deploy or push was performed.

### Remaining limitations and next steps

1. Rerun backend tests/compilation and analyze the affected Flutter files; fix any issues before review.
2. Reconcile repository migration history with the live project before applying the additive migration; then verify `session_id` and RLS policies read-only.
3. Obtain an authorized, synthetic mixed printed/handwritten test set with ground truth in the required languages. Evaluate candidate handwriting models against it, including endpoint accessibility, actual access/payment settings, latency and memory. Until then, do not claim prescription handwriting accuracy.
4. After the owner separately authorizes release, apply the migration, deploy, and perform authenticated synthetic upload, follow-up Q&A, detach/new-session, backend-restart, and cross-patient/cross-session tests. This local code change is not yet live.

## Deployment addendum (2026-10-26)

The owner subsequently authorized deployment. This addendum supersedes the pre-deployment status above:

- Applied `202610260001_chat_session_document_context.sql` to Supabase project `lsfhluvsrucwmbjvqwsr` before the application push.
- Read-only post-migration verification confirmed `medical_documents.session_id` is nullable UUID, the FK `medical_documents_session_id_fkey` uses `ON DELETE SET NULL`, RLS remains enabled on `ai_chat_sessions`, `ai_chat_messages`, and `medical_documents`, and the existing patient-owner policies remain in effect. No records were changed by the migration.
- Commit `261849c63ac93768c08407d5dc5e64f68363537b` (`Scope report chat documents to sessions`) was pushed to `master`; only the six scoped session-chat, migration, UI/provider, test, and report files were committed. Other pre-existing working-tree edits were left untouched.
- Both Render services deployed that exact commit successfully: `verityscribe-1` deploy `dep-dbfnrl0u01pc73cas1tg`; `verityscribe` deploy `dep-dbfnrkou01pc73cas1rg`. Both `/health` endpoints returned HTTP 200 and release `261849c63ac9`.
- Verification: backend suite **45 tests passed**; `python -m compileall -q app tests` passed; `git diff --check` passed. A fresh targeted Flutter analyzer invocation did not produce output and was interrupted after a 30-second stall, so this deployment does not claim a fresh Flutter-analysis pass (the earlier pre-deployment report records a prior pass).
- Still not end-to-end verified: no authenticated synthetic report was uploaded through the deployed app/API, queried in a chat, detached, or retrieved after restart. No real OCR inference was exercised. Handwriting recognition remains unchanged and not improved; do not treat deployment of session isolation as resolution of OCR accuracy.

**Next action:** sign in through the app and test the synthetic document workflow, then share its timestamp/request ID for sanitized server-log and read-only persistence verification. Do not use real patient records for this test.

## Chat request missing-session incident follow-up (2026-10-26)

**Finding:** the reported FastAPI `body.session_id: Field required` response could not have been produced by the deployed `/api/v1/chat` contract checked for this investigation. Both Render services were live on `261849c63ac93768c08407d5dc5e64f68363537b`; their live `/api/v1/openapi.json` describes `session_id` as optional. A harmless unauthenticated `POST /api/v1/chat` with only `{"message":"test from deployment validation"}` reached auth and returned `401 Missing bearer token`, not a missing-session `422`. In the corresponding Flutter source, `ClinicNotifier.sendMessage` awaited `/chat/sessions`, parsed `data.session_id`, and placed it in the `/chat` JSON map; upload likewise placed it in multipart `session_id`. The session endpoint's response shape matches that parser.

Therefore the screenshot’s exact producer remains unresolved: it indicates a stale/different backend schema or a different client/API route than the two current live services. No request/correlation ID or raw request metadata was provided to identify which. The source-level gaps that could allow recurrence were that session creation happened only on submit/upload, concurrent session creation was not coalesced, and the deployed schema accepted sessionless requests (which could silently route a message as general chat). This follow-up closes those gaps locally; it does not retroactively prove which external request generated the screenshot.

**Local fix (not deployed):**

- Chat screen opening now initializes a server-issued session; typed messages, suggested prompts, and uploads share the same in-flight initialization and reject empty/malformed/non-UUID IDs. The API request continues to send the exact `session_id` field.
- `ChatRequest.session_id` is now a required UUID. A missing or malformed ID is rejected by validation instead of silently entering sessionless general chat. Authenticated session-owner validation and exact patient/session document filters remain in place.
- Clearing increments a conversation generation, ends the previous session, and starts a new one. Late session/chat/upload completions from the cleared generation cannot replace the new conversation. Concurrent initialization is coalesced, and the UI shows the session error rather than a generic banner.
- Suggested-question chips now call the same send path as typed messages. The provider remains non-autoDispose, so its active session ID persists across screen rebuilds. The app does not restore prior messages after a full app restart; a fresh app scope starts a new session, and does not inherit prior-session documents.
- No migration, RLS policy, ASR model, or OCR implementation was changed.

**Verification:** reproduced the old contract behavior against the live API as described above, and added unit coverage for required/malformed UUID rejection. Local mocked regression checks: backend suite **46 passed**; Python compilation passed; Flutter chat-session suite **8 passed**; targeted Flutter analysis **No issues found**; `git diff --check` passed. The Flutter tests use fake API/auth/upload clients, not a signed-in production session. No production patient message or document was sent.

**Release state:** local source only. No push or Render deployment was authorized for this follow-up. The deployed OpenAPI contract remains optional until the new backend commit is deployed; after release, verify `/api/v1/openapi.json` marks `session_id` required and run an authenticated synthetic chat/upload test. If the same 422 appears after that, capture its request ID, API host/path, client build, and sanitized request field names to distinguish another stale client/endpoint from this implementation.

## Post-deployment missing-session report follow-up (2026-10-26)

**Verdict: BACKEND CONTRACT VERIFIED; CLIENT FAILURE NOT REPRODUCED.** The session lifecycle fix is now live on both Render backends, but the screenshot's missing `body.session_id` error remains unexplained because the failing browser request and client build were not available for inspection. No speculative change was made and no further deployment was triggered.

### Deployed service and API evidence

- `verityscribe-1` is live on commit `cb68fae48d90ad6f911ddb418eea41d2f9509efa` (deploy `dep-dbfobmnavr4c739bagjg`).
- `verityscribe` is live on the same commit (deploy `dep-dbfobmnavr4c739baf60`).
- Read-only `GET /api/v1/openapi.json` on both services marks `ChatRequest.session_id` required, type `string`, format `uuid`; required request fields are `message` and `session_id`.
- The deployed session-creation route is `POST /api/v1/chat/sessions`, returning HTTP 201. Its generated OpenAPI response schema is empty because the endpoint returns an untyped dictionary; source returns `{ "data": { "session_id": "<server-issued UUID>", "status": "active" } }`, matching the Flutter parser. The live route was not called with a patient token.
- Both services' `/health` return HTTP 200 with release `cb68fae48d90`; this is supplemental version evidence, not proof of chatbot success.

### Client and request trace

There is no Flutter/web frontend service in the Render workspace: the two listed Render services are backend Docker services. The repository's current `ClinicNotifier` implementation awaits `/chat/sessions`, validates the returned UUID, and includes it as `session_id` in the JSON for `POST /chat`; the suggested prompts use that same send method. The checked-in API default targets `https://verityscribe-1.onrender.com/api/v1`. This establishes source behavior only, not which already-running browser bundle, local Flutter process, or alternate client produced the screenshot.

Read-only Render application logs after the latest deploy showed health checks and an OpenAPI fetch, but no matching `POST /api/v1/chat` request on either backend. Render request-log queries also returned no matching chat posts. The screenshot contains no address bar, client build/version, timestamp, or correlation ID. The active browser client could not be reproduced in this investigation, and no authorized signed-in patient test account/request was available. Therefore the failing HTTP method/host/status/body, response request ID, initiating client file, frontend release/cache state, and first failing client stage remain **unverified**. The error text strongly indicates a request reached a backend that requires `session_id` without that field, but it does not identify which caller or deployment sent it.

### Required evidence to resolve without guessing

On the exact browser showing the error, open Chrome DevTools → Network, enable **Preserve log**, clear the list, reload the app, then send one harmless message such as “What is a balanced diet?”. Inspect the `POST /api/v1/chat/sessions` request and response, followed by `POST /api/v1/chat`. Share only:

- Request URL/path, method, HTTP status, timestamp, and response/request/correlation ID (if present).
- For session creation: sanitized response shape and whether `data.session_id` is a UUID (redact the UUID itself if preferred).
- For chat: JSON **field names only**, whether `session_id` is present and UUID-shaped (do not share its value), plus the response body with tokens, cookies, patient details, and clinical content removed.
- The request's Initiator filename and the app's address/origin. Do not share Authorization headers or a HAR containing credentials.

If no `POST /api/v1/chat/sessions` occurs, that narrows the first failing stage to the client/session initialization path. If it succeeds but `POST /api/v1/chat` omits `session_id`, the Initiator and payload will identify the actual caller or stale frontend build. If the field is present, the host and request ID will establish whether the request reached either verified backend or a different deployment. Only then should a code fix or redeployment be considered.

## HAR-confirmed missing-session upload (2026-10-26)

**Root cause confirmed: the running Flutter web client submitted an old/different multipart upload contract without `session_id`.** This was not a chat-message request, not OCR, not Supabase, and not backend UUID/session-owner validation. The backend correctly rejected the request before upload processing.

The supplied HAR was inspected without printing request bodies, authorization/cookies, or patient data. It records a page at `http://localhost:64058/` (Flutter web debug runtime), then:

1. `OPTIONS https://verityscribe-1.onrender.com/api/v1/chat/reports` returned 200.
2. `POST https://verityscribe-1.onrender.com/api/v1/chat/reports` at `2026-10-26T17:12:22.903Z` returned 422. Its multipart fields included `report_type`; **`session_id` was absent**. The response's sanitized validation detail was `loc=[body, session_id]`, `type=missing`, `msg=Field required`. The HAR carried request ID `a50b15e8fae5ac91-HYD`.
3. Render application logs independently confirm `OPTIONS /api/v1/chat/reports` 200 at `17:12:23.041Z` and `POST /api/v1/chat/reports` 422 at `17:12:25.736Z` on `verityscribe-1`. The `verityscribe` service had no corresponding request.
4. No `POST /api/v1/chat/sessions` appears in this HAR. The request initiator is the generic Dart `multipart_request.dart.lib.js` layer; it does not identify the application-level source file.

Both live backends at that time were on `cb68fae48d90ad6f911ddb418eea41d2f9509efa`. In that commit, `ClinicNotifier.uploadReport` awaits `_ensureChatSession()` before sending and sets both `report_type` and `session_id`; the session endpoint returns the nested `data.session_id` parsed by that same client. Therefore the runtime that emitted this HAR was **not executing the current checked-in upload implementation** (for example, a stale Flutter debug process/build or a different local checkout). The HAR establishes the client/runtime mismatch but cannot distinguish those two possibilities or identify the exact Flutter source checkout/version. The backend behaved correctly.

### Fix and verification status

No further code patch or deployment is indicated by this evidence, and none was made. Restart the Flutter app from the repository at commit `cb68fae` (stop the old `flutter run` process first; fetch `master` if that checkout is behind; then run `flutter clean`, `flutter pub get`, and the project's normal `flutter run -d chrome` command with its existing Supabase/API `--dart-define` values). This is a client refresh, not another backend release. After restart, verify the Network panel shows `POST /api/v1/chat/sessions` returning 201 with `data.session_id` and a subsequent `POST /api/v1/chat/reports` whose multipart field names include `session_id`; do not share its value. A live authenticated retry and successful report persistence/OCR remain unverified.

The HAR root page and generic Dart initiator do not reveal which local source checkout served port `64058`, so the precise reason it was stale (old process versus different checkout) remains unresolved. The smallest next evidence, if restart from the exact commit still fails, is a fresh sanitized HAR containing the session-creation request and upload request, plus the local `git rev-parse HEAD`; no credentials, cookies, session ID value, or patient document contents are needed.

## Layout-aware lab result extraction (2026-10-27)

**Root cause:** the prior extraction path retained only newline-separated source text. Digital PDFs were flattened by `pypdf.Page.extract_text()`; images and scanned PDF pages were sent to Tesseract's plain-text `--psm 6` output. `patient_embeddings` stored only that text. Chat retrieved individual lines and ranked them, which could separate a table's test label from the measured value, unit, and reference interval. There was no vector embedding/index in the active patient-chat path. Therefore prompt edits alone could not reliably restore the lost column/row relationship.

**Deployment update (2026-10-27):** the additive `structured_results` migration was applied to Supabase project `lsfhluvsrucwmbjvqwsr` and read-only schema verification confirmed the nullable JSONB column and array constraint. Backend deployment and the production synthetic upload/chat smoke test are pending; no real patient data has been used.

- Added conservative geometry-based row reconstruction in `backend/app/services/document_tables.py`. It recognizes common aliases for test, result, unit, range, status, specimen, and dates; retains literal values (including commas, decimal precision, and `<`/`>` qualifiers); and records page/table/row indices, source row, column headers, cell and row bounds, table bounds, confidence, and ambiguity. Missing/unreadable values remain null and are flagged; ranges are never used as measured values.
- Kept the existing OCR engine and local-only processing. Scanned image/PDF extraction now uses one bounded Tesseract TSV pass for both text reconstruction and word coordinates. Digital PDFs use the already-installed PDFium binding to obtain character positions; the original embedded PDF text remains the raw source. The old text-only extraction APIs remain available for other callers.
- The report upload stores the original extracted text and its JSON structured rows together in the patient's existing `patient_embeddings` row. `structured_results` is fetched only after patient ownership and active session document filters have selected the document. A new additive migration, `supabase/migrations/202610270001_add_structured_medical_results.sql`, adds a nullable JSONB array column without changing or weakening existing RLS. Null deliberately distinguishes legacy records that have not been processed by this new layout path from a valid empty result list; re-uploading a legacy file with the same upload identifier reprocesses it.
- Exact supported lab-result questions now use deterministic, session-scoped lookup before calling a model. The response preserves the measured value, unit, optional reference interval/date, page, and source row; multiple distinct results are all returned. A missing, unreadable, or ambiguous requested result fails closed. Broader document questions receive matching structured rows as a single evidence unit alongside source text. Patient/session ownership checks remain in force.

**Synthetic example:** a digital PDF containing separate columns `Investigation | Observed Value | Units | Reference Range` and the row `Platelet Count | 72,000 | /uL | 150,000-450,000` is reconstructed with the measured result exactly `72,000`, unit `/uL`, range `150,000-450,000`, page 1, and the original source row. The exact chat question “What is the patient's platelet count?” returns `72,000 /uL`, the separate reference interval, page citation, and an OCR verification caveat. It does not invoke the answer model. This fixture is synthetic and contains no patient data.

**Verification actually performed:**

- `python -m unittest discover -s tests -q` in `backend`: **72 tests passed** (includes upload → fake persistence → session retrieval → exact chat answer, patient/session scoping, alternate headers, multiple tables/pages, missing values, ranges, decimal/comma/qualifier preservation, and the broader existing ASR/OCR suite).
- `python -m compileall -q app tests` in `backend`: passed.
- A real synthetic digital PDF was generated in memory and processed by the installed `pypdf`/PDFium stack; its table row, coordinates, and source text were verified. An equivalent automated test now covers that path.
- Image/scanned-PDF OCR and Supabase persistence in the endpoint test use mocked Tesseract/database clients. The workstation has no `tesseract` executable, so no real scanned-image recognition was run. No production upload or clinical data was used.
- `git diff --check`: passed (Git printed existing LF-to-CRLF working-copy notices). Flutter SDK is present, but `flutter test test/clinic_provider_chat_test.dart` emitted no output and stalled for 40 seconds; it was interrupted and is **not** claimed as passing. No Flutter UI files were changed for this backend feature.
- The Supabase migration was applied through the connected authenticated project tooling. Read-only verification confirmed `patient_embeddings.structured_results` is nullable `jsonb` and the check constraint permits only JSON arrays or null. Existing RLS policies were not changed.
- On the isolated release candidate based on the current live commit, the focused `test_document_tables.py` (5), `test_document_ocr.py` (12), and `test_chat_session_documents.py` (13) checks passed; `git diff --check` passed. The prior local implementation also passed 72 backend tests, as recorded above.
- No production synthetic upload/chat request has yet been performed. The remaining release verification is the authenticated synthetic upload and follow-up chat lookup against the deployed service; scanned-image OCR remains unverified on a real runtime.

**Remaining limitations / release steps:** geometric grouping is heuristic and currently supports recognizable header rows and single visual-line result rows. It flags ambiguity rather than reconstructing complex merged cells or a table continuation with no repeated header. Handwritten or low-quality scanned values remain limited by the existing Tesseract recognition quality; the geometry feature does not establish clinical accuracy or a 90% accuracy claim. Before deployment, apply and read-only verify the additive migration, then run the synthetic upload and exact follow-up query against the target deployment. No push or deployment was performed in this change.
