# Validation — CouchLink 2.1, 29 October 2026

## Release checks

- Both APKs build with JDK 21, SDK 34 and Gradle 8.9; Android lint completes without errors.
- Version 2.1 (version code 4) preserves the signing identity, TV certificate, ADB authorization and existing phone pairings.
- The TV companion is installed on the owner's TCL C06. The owner's physical phone is switched off, so the new phone APK and its remote-network fallback still need an on-phone confirmation.
- Visually inspected Remote, Design, Settings and Screen interfaces. Generation screens and provider-account controls have been removed; photo/video imports remain as local TV backgrounds.
- Captured and visually inspected all six layouts on the real TV: App Icons, Spotlight, Mosaic, Sidebar, Dock and Minimal. Fixed Spotlight overflow and Sidebar logo sizing.
- Real TV API tests pass: authenticated navigation and volume, app catalogue including YouTube, unknown-command rejection and live JPEG capture. Current catalogue contains 19 launchable entries; home tiles follow the selected installed apps.
- Supabase project `alnxriyvmiztnbzgykhy` is active, with the relay Edge Function and private command tables installed. A temporary paired client completed cloud status, Home-key and live-preview requests through the TV; the preview returned a valid JPEG. This verified the phone-to-cloud-to-TV route, but not the owner's physical phone on a different network.
- Relay enrollment was corrected to preserve the TV's existing phone-token hashes. Queued credentials are erased when the TV claims a command; delivered command rows are deleted after the phone receives the response.
- The new phone Screen dialog displays the real TV picture. It uses snapshots around one frame per second, without audio; protected content may be black.
- Settings opens on the TV from the phone. Fixed the Home intent/panel ordering race. Home app listing and launching no longer depend on the remote service finishing startup.
- Decoded the TV's displayed QR code, opened its couchlink:// link on the Android emulator, compared all three prefilled pairing fields to the QR payload, submitted Pair, and verified Connected in the new phone UI.
- Selected a photo through Android's actual document picker. The image upload completed and appeared in the TV background picker.
- Real TV integration checks reject invalid layouts, path traversal, empty app sets and duplicate upload offsets. Chunked photo/video uploads, JPEG thumbnails and moving video wallpaper were exercised. Temporary imported media was removed.
- A full TV reboot returned automatically to dev.couchlink.tv/.HomeActivity. APP_AUTO_START remains allowed. The saved pairing and TLS identity remained valid; remote authorization was ready. Initial capture timed out during boot, then preview recovered automatically and the phone displayed the mountain Home screen. A later frame request returned HTTP 200 with a valid JPEG.
- The new phone Keyboard dialog sent a phrase into YouTube’s custom search keyboard; the real TV screenshot contains it and diagnostics report one successful youtube_keys request.
- The final visual-only update was installed after that reboot check. Android cleared the preferred Home on APK replacement; CouchLink Home was explicitly selected again and its resolved/resumed activity verified. All installed streaming applications were retained. Both final APK signatures were verified and SHA256SUMS refreshed. The phone download endpoint returns HTTP 200.

The dedicated emulator credentials were revoked, and their local credential and temporary QR payload were deleted. The owner’s existing phone pairing remains intact. No imported QA creations remain.

## Limits of verification

The interface and behavior were independently reconstructed from the actual 12:33 reference video. Original source/assets were not available; this is not a verified pixel-for-pixel copy. Scenic assets were recreated; see ASSETS.md and REFERENCE.md.

The owner's physical phone was off during final relay verification. Install the updated phone APK and confirm its Screen tab and keyboard while the phone is on a different network from the TV.

# Earlier validation — CouchLink 1.1

## Completed

- Both Android APKs build successfully with JDK 21 / SDK 34 / Gradle 8.9.
- Twelve core tests pass, including a simulated ADB challenge/response and binary stream,
  malformed HTTP framing, pairing expiry/replay/lockout, credential revocation and standard
  X.509 parsing/signature verification of the generated certificate, literal shell-text quoting
  and percent handling for custom TV keyboards.
- Android lint reports zero errors (7 TV warnings, 30 phone warnings).
- Both APKs pass Android APK Signature Scheme v2 verification.
- TV companion installed on the real TCL C06, Android 11, and its own ADB key was authorized
  by the owner through the TV's system debugging dialog.
- TLS pairing tested against the TV using the fingerprint read from its own setup screen.
  Unauthenticated requests are refused. Invalid control names are refused.
- Real TV navigation and volume requests complete successfully through the companion.
- App catalogue returns 21 launchable apps, including YouTube.
- A live 960 × 540 JPEG was obtained from the TV and visually inspected.
- The owner installed and paired the native Android phone app successfully over Wi-Fi.
- The companion was updated without losing its TV authorization, certificate or existing tokens.
- Native Android phone app v1.1 installed and paired through its actual UI on a dedicated
  Android 11 emulator, connected to the real TCL TV over the LAN.
- Screen preview visually verified in the native phone UI with the actual YouTube home screen.
  Fixed an overloaded `ImageView.setAlpha(int)` call that previously reduced image opacity to 1/255;
  the updated code explicitly uses float view alpha and full image alpha.
- Sent “couchlink” through the native phone Keyboard dialog into YouTube's custom TV search bar;
  the TV screenshot shows the exact phrase. Diagnostics confirm `youtube_keys` mode.
  YouTube supports printable English text; normal Android input fields use the Unicode IME.
- Native Apps page loaded names/icons and launched YouTube; the TV's resumed activity was checked.
- A right swipe on the native phone touchpad moved the real TV focus from YouTube to Netflix.
- Classic, Modern and Cinematic theme cards changed the real launcher's runtime configuration;
  screenshots visually confirm banner/icon/artwork changes. Previous-look restoration was exercised.
- After allowing TCL automatic startup, a second full TV reboot started the companion without
  opening its activity. A changed kernel boot ID, running RemoteService, authenticated saved
  test token and unchanged certificate confirm reboot persistence. The native phone emulator
  was restarted too and reconnected using its saved pairing, then displayed the TV home picture.
  TCL boot broadcasts delayed the remote's availability by roughly two minutes after reboot.
  Initial capture timed out during startup; preview polling recovered automatically.
- Final TV build also clears an old capture error when a later frame succeeds. Both APKs build
  and lint successfully, and the final installed companion returned a valid JPEG after its update.
- Temporary emulator pairing was removed through the native UI. The Mac test client was revoked
  through `/v1/unpair`; a subsequent request with its token returned 401, and its local credential
  was deleted. The dedicated emulator was shut down. The owner's pairing was preserved.

## Device-specific changes

The owner approved turning **Google Data Saver off** in the TV's Network & Internet settings.
Its VPN prevented incoming local connections. It can be turned back on from the same settings
if CouchLink is no longer needed; it must permit local connections while the remote is in use.
No Data Saver package was uninstalled or disabled.

The first reboot exposed TCL's vendor `APP_AUTO_START` app-op, which defaults to `ignore`
for a newly sideloaded app. System logs explicitly refused CouchLink's boot receiver and IME
for that reason. Set `APP_AUTO_START` to `allow` for `dev.couchlink.tv` only. To undo this
device-specific setting: `cmd appops set dev.couchlink.tv APP_AUTO_START ignore`.

This TV's hardware RSA provider failed native TLS signing. The companion uses a software RSA
identity encrypted at rest by Android Keystore AES-GCM, with TLS 1.2 ECDHE/AES-GCM and certificate
pinning. The ADB identity remains unchanged during app updates.

## Still to verify

- Owner's physical phone update and visual confirmation (the earlier installed phone build predates
  the preview fix). The owner will check later because their phone is switched off.

Private test credentials, setup UI captures and the preview image are in ignored `work/`.
They are not part of the APKs or public source documentation.
