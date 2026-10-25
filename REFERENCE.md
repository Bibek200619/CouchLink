# Video reference and implementation

Reference: [I Built My Own TV OS With Claude Code](https://www.youtube.com/watch?v=oj2MVUbb4iE),
Zubair Trabzada / AI Workshop, 12:33. The downloaded video and working frames are private in
`work/reference/`. The initial automated scene summary covered only an imagined one-minute
sequence; it was not used for this rebuild. The actual video frames were inspected directly.

This is an independent implementation of the visible Nova interface. The creator's project
files are behind their private Skool community and were not obtained. Scenery is reconstructed;
the functional app tiles correspond to this TCL's installed applications rather than adding
non-working shortcuts for the video's demo subscriptions.

| Video segment | Implemented behavior |
|---|---|
| 1:38–2:20 | QR/manual phone pairing, circular remote, Back/Home, TV screen shortcuts, Remote/Design tabs |
| 2:20–3:20 | App Icons, Spotlight, Mosaic, Sidebar, Dock and Minimal layouts; live changes from the phone |
| 3:20–3:45 | Nine scenic backgrounds; own photo/video import; phone backdrop follows the TV |
| 6:58–7:51 | TV appearance sidebar: layout, tile treatment, labels, logo color, font, clock and chosen apps |
| 7:51–8:31 | Locally imported photo/video backgrounds and appearance controls |

Extra controls retained from CouchLink: keyboard (including YouTube's custom keyboard), live
screen snapshots, app launching, volume/mute/playback, touchpad, sleep/wake and inputs.

## Supabase relay

Project `alnxriyvmiztnbzgykhy` hosts the `couchlink-relay` Edge Function and a private command
queue. The TV polls over outbound HTTPS, validates each queued phone credential against its
paired-token hashes, and executes the request through its existing authenticated local API.
The phone uses its pinned local TLS connection first, then falls back to the cloud relay if the
TV cannot be reached directly. The queue temporarily carries a phone credential until the TV
claims a command; the claim transaction clears it, and completed rows are removed after delivery.
The Supabase service-role key stays in the function's runtime environment and is never included
in either APK. Android phone and TV connectivity over separate networks was exercised using a
temporary paired client.

## Device and rollback

The original CleanYourTV home is kept installed. Switch Home back with:

```sh
TV_HOST=192.168.1.81 tools/venv/bin/python -I tools/tv.py 'cmd package set-home-activity com.gathin.tvhome/.MainActivity; input keyevent HOME'
```

Run that from the sibling `cleanyourtv` folder. The previous APKs are kept in
`work/backups/v1.1/dist/`. App identities, signing key, TV authorization and phone tokens are
preserved by the v2 update. No streaming application was removed.
