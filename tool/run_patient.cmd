@echo off
setlocal
if "%SUPABASE_URL%"=="" (
  echo SUPABASE_URL is missing. Set it to https://YOUR_PROJECT.supabase.co and run this command again.
  exit /b 1
)
if "%SUPABASE_PUBLISHABLE_KEY%"=="" (
  echo SUPABASE_PUBLISHABLE_KEY is missing. Set it to your Supabase publishable key and run this command again.
  exit /b 1
)
flutter run -d chrome --dart-define=API_BASE_URL=https://verityscribe-1.onrender.com/api/v1 --dart-define=SUPABASE_URL=%SUPABASE_URL% --dart-define=SUPABASE_PUBLISHABLE_KEY=%SUPABASE_PUBLISHABLE_KEY%
