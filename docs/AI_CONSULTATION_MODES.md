# AI Chat and AI Virtual consultation modes

These are appointment-linked modes (`ai_chat`, `ai_virtual`). Existing `chat`, video, phone, and in-person appointments continue through their existing paths. Starting either AI mode requires explicit patient consent; merely opening appointment details creates no session.

## Processing and safety

- Both modes use the same `ai_consultation` clinical conversation engine and the existing Cloudflare Workers AI Qwen3 model configured by `AIProvider.CHAT_MODEL`.
- AI Virtual records one patient turn at a time using the existing Flutter `record` dependency, sends it to the existing multi-model ASR consensus service, stores the recognized text, and uses device OS speech synthesis. It is not a full-duplex streaming call. Raw audio is removed from the device after upload and is not persisted by this feature.
- Both message modes assign request keys to each turn; a unique database index prevents duplicate patient/assistant rows when a client retries after a timeout.
- The backend uses the patient's authenticated identity for appointment ownership and loads only allowlisted allergies, chronic conditions, surgeries, and prescription facts scoped to that patient. No cross-patient or similar-case search runs. Similar cases are reported as unavailable until an approved de-identified corpus is configured.
- The model produces a draft summary only. Prescription stays null. A doctor assigned to the appointment must edit/approve or reject. Approval creates the linked ordinary consultation record; the doctor must use the existing workflow separately for any diagnosis, note, report, or prescription.
- Session, message, and review tables have RLS enabled with no direct authenticated-client policies. FastAPI uses the service role only after JWT and ownership checks. The service key is never sent to Flutter.

## Configuration

The feature reuses the configured `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN` for chat and ASR. If these are absent or the account cannot access a configured model, the backend returns an error and does not claim the turn was saved. Device TTS requires a supported OS speech engine. No new server credential is required.

`@cf/qwen/qwen3-30b-a3b-fp8` is hosted by Cloudflare Workers AI, which currently lists zero data retention for the model and a price of $0.0509 per million input tokens and $0.335 per million output tokens. Confirm current pricing before production use: [model documentation](https://developers.cloudflare.com/workers-ai/models/qwen3-30b-a3b-fp8/) and [pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/). Device speech uses [`flutter_tts`](https://pub.dev/packages/flutter_tts) (MIT license); it supports Android, iOS, macOS, web, and Windows.

## Deployment order

1. Apply `202611030001_add_ai_consultation_types.sql` and wait for it to commit.
2. Apply `202611030002_ai_consultation_sessions.sql`.
3. Apply `202611030003_ai_consultation_message_idempotency.sql`.
4. Deploy the FastAPI backend.
5. Build/deploy patient and doctor apps with the updated Flutter dependency lockfile.

The enum labels are in their own migration because PostgreSQL does not allow a newly added enum value to be used before the migration transaction commits.
