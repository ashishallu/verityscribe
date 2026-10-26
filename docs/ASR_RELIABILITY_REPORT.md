# ASR reliability report

Date: 2026-10-26
Verdict: **Flux removed from the active pipeline.** User-provided Render logs show `@cf/deepgram/flux` repeatedly failed with HTTP 401 at WebSocket handshake, before audio was sent. The precise account/token cause was not verifiable without inspecting secrets or Cloudflare account settings. As requested, Flux and its gateway-specific adapter were removed. The four other models remain configured exactly as before. The user's latest live test reported usable transcripts from those four models; no fresh live inference was performed in this code update.

## Scope and preserved safeguards

This update removes the failing Flux model and retains the four user-reported-working Cloudflare models. Their IDs, provider routes, request encoding, response parsing, and weights are unchanged. Calls remain parallel and independent; failures retain per-model diagnostics, one model failure does not suppress other transcripts, and a single model is never represented as multi-model consensus. Transcript results remain drafts requiring clinician review; this work does not create or approve clinical records.

The failure evidence supplied by the user reports a 400-style `invalid_audio_or_model_request` for Cloudflare Whisper Large-v3 Turbo and `provider_billing_required` for Hugging Face `openai/whisper-large-v3-turbo`. Current repo defaults had drifted to Hugging Face ASR and could send an unverified/paid provider route. The latter is removed from the configurable ASR allowlist. Cloudflare's official Turbo example sends Base64 audio, while the old Cloudflare adapter sent a JSON array of bytes to every Whisper model. That is a plausible and directly fixable cause of Turbo's request rejection; it is not proven as the historical cause because the rejected request body was not retained.

## A. Final model configuration

| Model | Route | Request format | Intended role | Status in this run |
|---|---|---|---|---|
| `@cf/openai/whisper` | Cloudflare Workers AI batch REST | Existing JSON byte-array payload; unchanged | Existing baseline | Mock regression passed; live not tested |
| `@cf/openai/whisper-tiny-en` | Cloudflare Workers AI batch REST | Existing JSON byte-array payload; unchanged | Existing English lightweight baseline | Mock regression passed; live not tested |
| `@cf/deepgram/nova-3` | Cloudflare Workers AI batch REST | Existing raw WAV bytes with `audio/wav`; unchanged | Existing fast baseline | Mock regression passed; live not tested |
| `@cf/openai/whisper-large-v3-turbo` | Cloudflare Workers AI batch REST | Base64-encoded audio JSON, matching Cloudflare's published example | Batch ASR | User reports usable live transcript; mocked request/response regression is retained |

The failed Hugging Face `openai/whisper-large-v3-turbo` route and Cloudflare Flux route have been removed. ASR configuration accepts only the four allowlisted Cloudflare model IDs; custom endpoints and non-Cloudflare providers are rejected so the ensemble cannot silently route to a paid external provider. A legacy `AI_ASR_MODELS_JSON` override that lists Flux now drops that retired entry and preserves the remaining supported models, avoiding a break from stale Render configuration.

## B. Replacement rationale and limitations

1. **Batch model — `@cf/openai/whisper-large-v3-turbo`.** Cloudflare's catalog marks it batch ASR, and its official tutorial demonstrates Base64 audio in the `audio` field. The adapter uses the documented format and supports both flat `result.text` and `result.transcription_info.text` response forms.

Flux was the only additional distinct Cloudflare option considered here, but its authenticated Gateway handshake consistently failed. It is therefore no longer part of the ensemble. Four active models are preferable to advertising a fifth model that cannot currently produce a transcript.

## C. Cost verification and safeguards

Cloudflare's official pricing page states that Workers AI includes **10,000 neurons/day** at no charge. On **Workers Free**, usage beyond the included allowance fails. All four models share that daily allocation; the app does not enable paid-provider fallback or automatically retry HTTP 429 quota/rate-limit errors. The reviewed pricing page does not list Whisper Tiny.

The code restricts ASR models and providers to the four allowlisted Cloudflare Workers AI routes, has no paid-provider fallback, and does not retry Cloudflare HTTP 429 quota/rate-limit errors. **It cannot prevent Cloudflare account-level paid overage if the account is on Workers Paid.** To keep spend at zero, the account must remain on Workers Free; the app cannot inspect or lock the account plan from Render.

## D. Benchmark results

The supplied Render logs show `@cf/deepgram/flux`, HTTP 401 during its WebSocket handshake, before audio transmission. This indicates an authentication rejection, not an audio encoding or transcript parsing failure. The precise credential/Gateway cause cannot be established from the log. User reports that the other four models returned usable transcripts. This code update did not run a controlled real-audio inference test.

There are **no new latency or accuracy measurements**, and no clinical-word/number/negation comparison against a reference transcript. The user's earlier single-run observations remain historical only: Whisper 4,221 ms, Whisper Tiny 4,027 ms, Nova-3 825 ms. They are not reused as current benchmarks.

Automated tests validate the four-model configuration, request/response adapters, partial/all-failure behavior, parallel prediction collection, no paid/external route, and that a 429 does not retry. These mocked tests do not establish live account accessibility, transcription quality, or availability.

## E. Regression and test results

Command: `python -m unittest discover -s tests -q` from `backend/`. **23 tests passed.** Coverage includes the four-model configuration, migration of legacy overrides that still include Flux, rejection of oversized custom configurations, Cloudflare request formats including Turbo Base64 payload/response parsing, four-model parallel consensus, one/all model failures, external/paid-provider rejection, and one-shot handling of Cloudflare quota 429; existing document extraction tests also passed.

These are unit tests with mocked provider responses, not real inference or a deployed end-to-end test. Live regression of the three existing models was not possible here, so they are preserved in source and their adapter contracts are regression-tested but not freshly verified against Cloudflare.

## F. Error analysis

- Cloudflare Turbo's prior failure is consistent with the old byte-array payload differing from Cloudflare's documented Base64 input. Fix: dedicated Base64 serialization and parsing for the published result envelope. Historical causality remains unconfirmed.
- Hugging Face Turbo's billing error is avoided by removing that inference route from the ASR ensemble; no Hugging Face ASR call or paid fallback is made.
- Flux’s exact account/Gateway authentication cause remains unresolved; the observed handshake failure is why Flux has been removed rather than left as a permanently failing fifth prediction.
- Cloudflare quota/rate limit is surfaced as a controlled failed prediction; the request is not automatically retried and no other provider is invoked.
- Invalid audio and provider timeouts are bounded and isolated per model. The endpoints continue retaining a secure recording before ASR; the transcription draft remains subject to clinician review.

## G. Files changed for this update

- `backend/app/services/ai_service.py` — four-model Cloudflare-only allowlist; preserved request routes; Turbo Base64 adapter; removed Flux WebSocket adapter; no 429 retries.
- `backend/app/routers/integration.py` — user-facing error messages now accurately identify Cloudflare access, auth, billing restrictions, and quota exhaustion.
- `backend/tests/test_asr_consensus.py` — regression and mocked adapter/quota coverage.
- `backend/requirements.txt` — removed the now-unused WebSocket dependency.
- `backend/.env.example` — four-model ASR configuration; removed the unused Gateway setting.
- `docs/ASR_RELIABILITY_REPORT.md` — this report.

No Flutter app, doctor app, admin portal, Supabase schema/RLS, recording storage, ownership logic, or Render environment values were changed in this update.

## H. Remaining blockers and next verification

1. Confirm that the Cloudflare account remains on Workers Free. The repository cannot enforce account-plan selection; on Workers Paid, Cloudflare can bill beyond the daily free allocation.
2. After deployment, verify all four predictions appear in the UI and that each returns a usable transcript. Compare against synthetic/consented audio with known words, numbers, and negations.

## I. Final verdict

**Final active configuration: four models.** Flux has been removed due repeated live Gateway handshake 401 errors. No production credentials were changed. The three pre-existing REST adapters and Turbo's Base64 request format are unchanged. The user reports all four models returned usable transcripts before this removal; the current verification relies on automated mocks, not a new live inference. Deployment status is reported separately after the code is pushed.

## Official references

- [Cloudflare Workers AI pricing and free daily allocation](https://developers.cloudflare.com/workers-ai/platform/pricing/)
- [Cloudflare Whisper Large-v3 Turbo model and input parameters](https://developers.cloudflare.com/workers-ai/models/whisper-large-v3-turbo/)
- [Cloudflare Turbo tutorial using Base64 audio](https://developers.cloudflare.com/workers-ai/guides/tutorials/build-a-workers-ai-whisper-with-chunking/)
- [Cloudflare Flux model and input format](https://developers.cloudflare.com/workers-ai/models/flux/)
- [Cloudflare AI Gateway realtime WebSocket API](https://developers.cloudflare.com/ai-gateway/usage/websockets-api/realtime-api/)
- [Cloudflare authenticated AI Gateway token requirements](https://developers.cloudflare.com/ai-gateway/configuration/authentication/)
- [Cloudflare token permission definitions](https://developers.cloudflare.com/fundamentals/api/reference/permissions/)
- [Deepgram Flux protocol reference: CloseStream and EndOfTurn transcript schema](https://developers.deepgram.com/reference/speech-to-text/listen-flux?explorer=true)
- [Cloudflare Workers AI model catalog](https://developers.cloudflare.com/workers-ai/models/)
