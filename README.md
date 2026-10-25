# CouchLink 2.1 — Nova interface

An Android phone remote and a companion app for this TCL C06 Android 11 TV.
The Android phone app and TCL Android TV companion provide a remote, TV screen preview,
app shortcuts, and a customizable TV home. They connect directly on the same Wi-Fi when possible
and use an encrypted Supabase relay when the phone and TV are on different networks.

## What it does

- Directional pad with hold-to-repeat, OK, Home, Back, Settings, menu and input selection.
- Swipe touchpad with tap for OK and hold for Menu.
- Volume, mute, play/pause, sleep and wake while the TV remains reachable.
- App shortcuts with the TV's installed app names and icons.
- Unicode text entry using the companion's input method. Select a text field on the TV before sending.
  Apps with custom keyboards, such as YouTube, also support a plain-English key-event fallback.
- Screen preview through live JPEG snapshots, about one frame a second depending on the TV.
  Preview has no audio; protected streaming video and some HDMI inputs may be black or unavailable.
  A tap sends a touch to the TV; apps that do not support touch still work with directional controls.
- Local discovery, certificate verification, one-use pairing codes, saved pairing and revocation.
- Internet relay through Supabase for commands, keyboard input, app launching and screen preview
  when the phone and TV are on different networks.
- A full TV Home with App Icons, Spotlight, Mosaic, Sidebar, Dock and Minimal layouts.
- Shared appearance settings: backgrounds, brand/glass/dark tiles, logo/name labels, white or
  brand logos, Modern/Elegant/Rounded typography, clock visibility and app selection/order.
- Nine bundled scenic wallpapers, plus phone photo/video import and looping video backgrounds.
- QR/manual pairing. The phone uses Remote and Design tabs; extra keyboard, screen,
  touchpad, volume, playback, power and app controls remain on the Remote tab.

See [REFERENCE.md](REFERENCE.md) for the video mapping and relay design,
[ASSETS.md](ASSETS.md) for wallpaper provenance/prompts, and [VALIDATION.md](VALIDATION.md)
for actual checks and unverified items. Original creator source files were not obtained;
scenery was reconstructed and tiles use the TV's real installed apps.

## Install and pair

1. Install `dist/CouchLink-TV.apk` on the TV and open **CouchLink TV**.
2. Keep USB/network debugging enabled. Accept the companion's **Allow USB debugging** prompt.
   This authorizes the TV companion's own key, which stays in its private app storage.
3. Install `dist/CouchLink-Phone.apk` on an Android phone (Android 8 or newer).
4. On the phone, choose **Connect** and choose the discovered TV or enter its displayed address.
5. Pair once while the phone can reach the TV's address, using the six-digit code and
   twelve-character certificate fingerprint shown on the TV.
6. Press Home to leave the companion running. The phone can then control the TV over the
   internet from a different Wi-Fi or mobile network.

The v2 TV home is `dev.couchlink.tv/.HomeActivity`. It has been selected as Home on this TV.
In its Settings, **Connect your phone** displays a QR code for the installed Android app,
plus a manual code and fingerprint. **Previous TV home** opens the retained CleanYourTV home.
The TV app's original setup screen remains available for ADB authorization and keyboard recovery.

On the phone, **Design** applies a preset or background immediately. Remote includes a
prominent **Choose apps** action. Tap a selected app to remove it, reorder by selecting apps,
then save. Remote's **Open on the TV** cards open the corresponding TV panel.
Use the small monitor button on Remote for the live screen preview.

Imports accept photos converted to JPEG on the phone and videos up to 32 MB. The TV media
library is capped at 256 MB and keeps up to 40 imports; deleting an import frees its local file.

The pairing code expires after five minutes and can be used once. Choose **New pairing code**
on the TV for another phone. Five incorrect attempts invalidate a code. **Forget phones**
on the TV revokes every phone token immediately. At most eight phone pairings are retained.

The TV uses an outbound HTTPS connection to Supabase project `alnxriyvmiztnbzgykhy`.
The relay stores the TV's relay secret as a hash and stores paired phone-token hashes. A phone
token is held in a protected command row only until the TV claims that command, then erased;
completed command rows are deleted when the phone receives the response. Commands and previews
are not intended as permanent cloud storage. Both devices need internet access for cross-network
control; local direct control remains available when they share a reachable network.

Keep debugging enabled while using CouchLink: system-wide key events and screenshots use
the TV's loopback ADB service. ADB must already have been configured on this TV; this app does
not root it or enable debugging silently. Wake cannot power on a TV that has disconnected from
the network in deep standby.

For phone text entry, CouchLink remembers the previous TV keyboard before selecting its own.
Choose **Restore TV keyboard** in the companion if you want to use the original on-screen keyboard.
The foreground notification's **Stop remote** action also attempts to restore it.

## Build

Use JDK 17 or 21, Android SDK platform 34 and build-tools 34.0.0. Set `JAVA_HOME` and
`ANDROID_HOME` as needed, then run `./scripts/build.sh`. The script builds both APKs,
runs protocol/security unit tests and Android lint, and copies APKs into `dist/`.
The current build signs with the local Android debug key, suitable for direct installation.
Keep that key for updates; store distribution should use a separate release-signing configuration.

## Project

| Module | Purpose |
|---|---|
| `core` | ADB packet/authentication client, strict HTTP framing, certificate pinning and pairing policy |
| `tv` | Foreground companion, TLS API, NSD advertisement, local ADB controls, app catalogue and phone input method |
| `phone` | Android phone app, NSD discovery, pairing, native network bridge and file picker |
| `ui` | Bundled offline TV and phone interface, scenic assets and fonts |
| `android-shared` | Restricted local WebView host and chunked media import |

The TLS private key is encrypted at rest with an Android Keystore AES-GCM key. The phone verifies a 48-bit fingerprint read from the TV
during pairing, then pins the full SHA-256 certificate fingerprint for all later requests.
TV-side credentials are stored as SHA-256 token hashes; phone tokens stay in private app storage.
Android backup is disabled for both apps. API requests have strict size limits and use a fixed
control allowlist; there is no remote shell endpoint. Preview polling stops when its page is hidden
or the phone app goes into the background. Screens are not saved to the photo library.

The TV companion starts again after a reboot once enabled. It uses a visible foreground service
and advertises `_couchlink._tcp.`. It normally listens on port 8787, choosing another port if busy.
On this TCL, its vendor automatic-start permission also needs to be allowed for CouchLink;
that setting has been enabled on the configured TV.
Discovery communicates the chosen port and can update the saved TV address after DHCP changes;
the saved certificate is still verified.

The TV's Google Data Saver VPN blocked local connections during setup and was turned off with
your approval. Cross-network control uses outbound HTTPS, but direct same-network control still
depends on the TV allowing local connections.

## Jarvis assistant

The Jarvis tab uses phone speech recognition for voice input and deterministic commands for opening TV apps, navigation, volume and playback. General questions use OpenRouter's free model through the existing Supabase relay; the phone and TV speak the reply. Add `OPENROUTER_API_KEY` to the Supabase function secrets and deploy `couchlink-relay` before enabling general answers. The key stays server-side. See [ASSISTANT.md](ASSISTANT.md) for setup and limits. Free model requests may be slow or rate limited.

## Remove

First select the retained CleanYourTV launcher as Home (see [REFERENCE.md](REFERENCE.md)).
Restore the TV keyboard in the companion, use **Forget phones**, stop the remote, then remove
CouchLink from the TV and phone through their app settings. If no other task needs debugging,
turn USB/network/wireless debugging off, revoke debugging authorizations and verify ADB is closed.
The CleanYourTV launcher is separate and remains installed.

## Technical references

- [Android ADB protocol](https://android.googlesource.com/platform/packages/modules/adb/+/1a0fb8846d4e6b671c8aa7f137a8c21d7b248716/protocol.txt)
- [Android network service discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [Connected-device foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device)
