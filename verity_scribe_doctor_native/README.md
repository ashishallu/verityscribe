# VerityScribe Doctor (Native Android)

This project is the supplied Jetpack Compose Doctor app UI. Its Compose screens are retained; the runtime data/authentication layer calls the VerityScribe FastAPI backend.

## Configure and run

1. Open `verity_scribe_doctor_native` in Android Studio and allow Gradle sync to finish.
2. Create a local `.env` file in this project directory using `.env.example` as a template, or set these as Gradle properties/environment variables:
   - `SUPABASE_URL`
   - `SUPABASE_PUBLISHABLE_KEY`
   - `API_BASE_URL` (defaults to the production API URL in `app/build.gradle.kts`)
3. Use an authorized clinician's email and password. Sign-in is checked by Supabase Auth and then `/me/doctor`; demo sign-in does not bypass authentication.
4. Build/run the `app` configuration on an Android emulator or device.

Only the Supabase URL, publishable key, and API base URL are read into app configuration. Never put a Supabase service-role key, Gemini key, or Cloudflare token in this Android project. AI requests are made by the authenticated backend.

The app uses live data from the signed-in doctor's assigned appointments, AI consultation reviews, reports, notifications, and patient records. Missing fields display as not recorded. The clinical assistant requires a selected appointment and calls the authenticated doctor-assistant API. Assistant conversations remain in app memory because the current backend does not expose doctor-assistant chat persistence.

## Current backend limitations

- The API has no doctor-side summary retry endpoint, notification-preference write endpoint, or appointment-completion transition; these controls report that limitation instead of simulating a successful server write.
- The prescription form does not capture catalog medicine IDs or quantities required by the prescription API, so prescriptions are not submitted from this screen.
- Voice-note capture in the supplied UI is not wired to an audio recorder in this native project; the UI reports this rather than creating a fake transcript.
