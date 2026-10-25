# CouchLink Jarvis

Jarvis uses the Android phone microphone and speech-recognition activity for voice input. CouchLink asks Android to prefer offline recognition; the installed speech service can still require network access. Simple TV commands are handled on the phone and sent through the paired CouchLink connection. General questions go through the existing Supabase relay to OpenRouter's `openrouter/free` model. The answer is shown on the phone, spoken by the phone, then sent to the TV home panel and spoken by the TV.

## Provider setup

The OpenRouter credential must stay in Supabase Edge Function secrets. Never place it in the Android APK, `RelayConfig`, a Figma file, or this repository.

Set `OPENROUTER_API_KEY` as a Supabase secret for the existing `couchlink-relay` function, then deploy the updated function. If the secret is absent, CouchLink returns a setup message. Free models have provider availability and rate limits; Jarvis reports service limits instead of switching to a paid model.

The Edge Function uses the paired TV's existing phone-token hashes to authorize assistant requests. Prompts are sent to OpenRouter only after the user taps Send or the microphone button; no always-on listener or background recording is used. The assistant request does not include screenshots or files.

## Free hosting limits

The model itself is hosted by OpenRouter. Render's free web service can run a lightweight proxy but cannot host a practical general-purpose model, and it sleeps after idle time. CouchLink already has a Supabase relay, so the assistant uses that rather than adding a second always-on service.

## Replit

The Replit account is signed in, but its dashboard currently reports the free Agent allowance is fully used. The app code remains in the local Android project; do not upgrade the account to continue.
