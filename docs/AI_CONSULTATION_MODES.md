# AI Chat and AI Virtual consultation modes

These are appointment-linked modes (`ai_chat`, `ai_virtual`). Existing `chat`, video, phone, and in-person appointments continue through their existing paths. Starting either AI mode requires explicit patient consent; merely opening appointment details creates no session.

## Processing and safety

- Both modes use the same `ai_consultation` clinical conversation engine and the existing Cloudflare Workers AI Qwen3 model configured by `AIProvider.CHAT_MODEL`.
- AI Virtual streams 16 kHz mono PCM from Flutter `record` to the backend, which securely proxies it to Cloudflare Workers AI Deepgram Nova-3 over WebSocket. Interim captions and speech-start events are live; final transcripts/messages are persisted. Device OS speech synthesis reads the shared AI response, and a detected patient interruption stops current playback. Raw audio is not written to disk or persisted by VerityScribe. The language-model response is produced per completed patient utterance; token-by-token speech synthesis is not implemented.
- Both message modes assign request keys to each turn; a unique database index prevents duplicate patient/assistant rows when a client retries after a timeout.
- The backend uses the patient's authenticated identity for appointment ownership and loads only allowlisted allergies, chronic conditions, surgeries, and prescription facts scoped to that patient. Similar-case lookup is opt-in. It searches only doctor-approved symptom terms from other AI consultations at the same hospital, excludes the current patient, and returns only symptom terms and a relevance score. The corpus stores no narrative, name, date, patient identifier in search results, or transcript. RLS is enabled with no client policies; only the backend service role can query it. Matches are contextual examples, not diagnoses or treatment recommendations.
- The model produces a draft summary only. Prescription stays null. A doctor assigned to the appointment must edit/approve or reject. Approval creates the linked ordinary consultation record; the doctor must use the existing workflow separately for any diagnosis, note, report, or prescription.
- Session, message, and review tables have RLS enabled with no direct authenticated-client policies. FastAPI uses the service role only after JWT and ownership checks. The service key is never sent to Flutter.

## Configuration

The feature reuses the configured `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN` for chat, batch ASR, and live Nova-3 streaming. WebSocket access is authorized with a single-use, 90-second ticket stored server-side; the Cloudflare token never reaches Flutter. If the account cannot access the streaming model, the patient sees a connection error and can retry or switch to AI Chat. Device TTS requires a supported OS speech engine. No new server credential is required.

`@cf/qwen/qwen3-30b-a3b-fp8` is hosted by Cloudflare Workers AI, which currently lists zero data retention for the model and a price of $0.0509 per million input tokens and $0.335 per million output tokens. Confirm current pricing before production use: [model documentation](https://developers.cloudflare.com/workers-ai/models/qwen3-30b-a3b-fp8/) and [pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/). Device speech uses [`flutter_tts`](https://pub.dev/packages/flutter_tts) (MIT license); it supports Android, iOS, macOS, web, and Windows.

## Deployment order

1. Apply `202611030001_add_ai_consultation_types.sql` and wait for it to commit.
2. Apply `202611030002_ai_consultation_sessions.sql`.
3. Apply `202611030003_ai_consultation_message_idempotency.sql`.
4. Apply `202611030004_ai_consultation_case_matching.sql`.
5. Apply `202611030005_ai_virtual_stream_tickets.sql`.
6. Deploy the FastAPI backend.
7. Build/deploy patient and doctor apps with the updated Flutter dependency lockfile.

The enum labels are in their own migration because PostgreSQL does not allow a newly added enum value to be used before the migration transaction commits.
