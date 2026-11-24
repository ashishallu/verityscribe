<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/927303f8-b7ae-490a-b7fd-427c95017db8

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio and open `verity_patient_redesign_native`.
2. Configure `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and `API_BASE_URL` in the root `local.properties`, the app's untracked `.env`, or the parent project's `backend/.env`. Process environment variables take precedence. The Gradle file reads only those three values; it never reads or packages the Supabase service-role or model keys.
3. The default API base is the existing VerityScribe Render API. Set `API_BASE_URL` to a reachable local backend URL when testing locally (for an Android emulator, use the emulator's host address rather than `localhost`).
4. Sync Gradle and run on an emulator or physical device. The required Android internet permission is included.

The Gemini/Cloudflare credentials remain server-side. Authentication uses Supabase Auth; application data and AI requests use the existing authenticated FastAPI routes. Health Connect/Google Fit code from the supplied project is retained unchanged.
