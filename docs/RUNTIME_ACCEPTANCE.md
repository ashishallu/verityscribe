# VerityScribe Runtime Acceptance

## Scope and assumptions

Supabase is the single source of truth. The Patient App, Doctor App, and Admin Portal use the FastAPI backend and authenticated Supabase sessions. No passwords, API keys, JWT secrets, or service-role credentials belong in this document or in client source code.

The seeded reference identity is `patient001@demo.verityscribe.local` (Arjun Reddy). The seeded doctor and hospital-admin identities must be supplied through the existing secure credential store when testing. Their passwords are intentionally not documented.

Required runtime configuration:

- Patient and Doctor Flutter apps: `API_BASE_URL`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`
- Admin Portal: `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` (the legacy `NEXT_PUBLIC_SUPABASE_ANON_KEY` remains supported)
- FastAPI AI chat: `HF_TOKEN`/`HUGGINGFACE_TOKEN` or `AI_LLM_BASE_URL`
- FastAPI voice transcription: `AI_ASR_BASE_URL` and the private `VOICE_STORAGE_BUCKET` (default `voice-recordings`)

## Windows CMD launch commands

Replace only the angle-bracket placeholders at runtime; do not commit their values.

### Patient App

```cmd
cd /d C:\Users\ashishallu\Documents\VerityScribe
flutter run -d chrome --dart-define=API_BASE_URL=https://verityscribe-1.onrender.com/api/v1 --dart-define=SUPABASE_URL=https://<project>.supabase.co --dart-define=SUPABASE_PUBLISHABLE_KEY=<publishable-key>
```

### Doctor App

```cmd
cd /d C:\Users\ashishallu\Documents\VerityScribe\verity_scribe_doctor
flutter run -d chrome --dart-define=API_BASE_URL=https://verityscribe-1.onrender.com/api/v1 --dart-define=SUPABASE_URL=https://<project>.supabase.co --dart-define=SUPABASE_PUBLISHABLE_KEY=<publishable-key>
```

### Admin Portal

```cmd
cd /d C:\Users\ashishallu\Documents\VerityScribe\admin_portal
set NEXT_PUBLIC_API_BASE_URL=https://verityscribe-1.onrender.com/api/v1
set NEXT_PUBLIC_SUPABASE_URL=https://<project>.supabase.co
set NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=<publishable-key>
pnpm dev
```

## Acceptance workflow

1. Sign in to the Admin Portal with the existing hospital-admin account.
2. Open Appointments and create an appointment for Arjun Reddy and an available seeded doctor.
3. Confirm the created appointment ID in the Admin Portal and Supabase.
4. Sign in to the Patient App as Arjun Reddy and confirm the same appointment appears.
5. Sign in to the Doctor App as the assigned doctor and confirm the same appointment and patient context appear.
6. In the Doctor App, create a consultation for that appointment; confirm it appears for the patient after refresh.
7. Create a prescription with doctor-entered dosage, frequency, and quantity; confirm the prescription and medicines appear for the patient.
8. Log out and sign in again in each app; confirm the records remain available.
9. Test loading, populated, empty, API-error, authorization-error, and expired-session states for each workflow.

## Status from this environment

| Test | Status | Evidence / blocker |
|---|---|---|
| Patient Flutter static analysis | PASS | `flutter analyze --no-pub` |
| Doctor Flutter static analysis | PASS | `flutter analyze --no-pub` |
| Admin TypeScript check | PASS | `npx tsc --noEmit` |
| Backend Python compilation | PASS | `python -m compileall -q backend/app` |
| Repository diff check | PASS | `git diff --check` |
| Patient Chrome launch | BLOCKED | No usable Flutter device/browser target was available in this environment |
| Doctor Chrome launch | BLOCKED | No usable Flutter device/browser target was available in this environment |
| Admin Portal local runtime | BLOCKED | No authenticated browser session was available |
| Supabase seeded Auth identity verification | BLOCKED | Read-only Supabase query connection timed out |
| Public FastAPI `/health` request | BLOCKED | Network proxy refused the outbound request |
| Admin appointment creation | BLOCKED | Requires authenticated Admin Portal runtime |
| Patient appointment visibility | BLOCKED | Depends on the authenticated appointment creation test |
| Doctor appointment visibility | BLOCKED | Depends on the authenticated appointment creation test |
| Consultation synchronization | BLOCKED | Requires authenticated Doctor and Patient runtimes |
| Prescription synchronization | BLOCKED | Requires authenticated Doctor and Patient runtimes |
| Logout/login persistence | BLOCKED | Requires authenticated app runtimes |
| Chatbot provider runtime | BLOCKED | Requires a real JWT and configured AI provider |
| Voice/ASR runtime | BLOCKED | Requires a real JWT, private storage bucket, and ASR provider |
| Full `dart format --output=none --set-exit-if-changed .` | BLOCKED | Command stalled and was stopped; not reported as passed |

No runtime PASS is claimed for any authenticated workflow until it is executed with real sessions.
