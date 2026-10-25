# Interface assets and prompts

Final wallpaper files are in `ui/backgrounds/`: `mountain.jpg`, `golden.jpg`, `dunes.jpg`,
`forest.jpg`, `garden.jpg`, `coast.jpg`, `cove.jpg`, `peaks.jpg`, `city.jpg`.
All nine were created using the built-in `image_gen` tool, then encoded as JPEG for the APKs.
The original generated PNG files are retained privately in `work/reference/` and in the tool's
generated-images folder. No generated asset is referenced outside this workspace at runtime.

## Reference-based edits

The mountain used frame 245 seconds of the supplied video with this prompt:

> Edit target: the supplied screenshot from a TV interface. Create a clean, full-bleed 16:9 landscape wallpaper by removing ALL user interface, ALL browser chrome, every logo, tile, text, clock, button, and the circular presenter portrait. Preserve the pictured mountain valley as closely as possible: sharp snowy pointed mountains surrounding a green valley, winding silvery river in the foreground, blue-grey sky, soft morning light. Reconstruct naturally the parts of the landscape covered by interface elements. The output must be ONLY the photorealistic landscape. No people, no letters, no interface, no watermarks. Match the mountain arrangement, perspective, colours, and river composition of the supplied reference. This is a production background asset for the replica TV and phone application.

The other four reference edits used this template, with the exact scene instruction below:

> Edit target: supplied screenshot from the TV interface. Produce ONLY the clean photorealistic landscape wallpaper, full-bleed 16:9. Remove all interface, browser chrome, clock, every letter, app logo, tiles, controls, the phone overlay, mouse cursor, and the circular presenter portrait. Reconstruct the areas hidden by these overlays as natural landscape. [Scene instruction] No people, no text, no UI, no watermarks. Match the pictured landscape as closely as possible, not a redesign. Production wallpaper asset for the replica TV application.

| Asset / frame | Scene instruction |
|---|---|
| Golden / 205 s | Preserve the golden rolling grassy hills, single tree at the upper right, warm golden sunset, long soft shadows and composition. |
| Dunes / 177 s | Preserve the sweeping orange and grey sand dunes, purple and pink sunset sky and composition. |
| Forest / 185 s | Preserve the tall dark pine trunks, soft shafts of light, dense grey woodland mist, mossy forest floor and composition. |
| Garden / 220 s | Preserve the Japanese garden: autumn red and gold maple leaves, rocks, green moss, tranquil dark koi pond and composition. |

## Gallery backgrounds

Each used this template with one scene description:

> Production asset: full-bleed 16:9 photorealistic landscape wallpaper for a minimalist smart TV home screen matching the scenic wallpaper gallery in the reference project. [Scene description] Only landscape, no people, no text, no user interface, no watermarks, no framing. High quality natural photography, understated lighting.

- Coast: Misty coast: a very high aerial view along steep lush green coastal cliffs curving from the left foreground into the misty distance, turquoise ocean on the right, hazy overcast sky. Muted green-grey cinematic colour, calm, no structures.
- Cove: Quiet cove: rugged dark volcanic rocks and small sea stacks in a calm secluded bay, photographed near the shore at dusk. Still silvery water, distant soft pink peach horizon, grey blue clouds, cinematic moody long exposure.
- Peaks: Blue peaks: layered distant blue alpine mountain silhouettes at blue hour, smooth blue and mauve gradients across mountains, pink horizon, peaceful landscape wide view, no buildings.
- City: City at dusk: a calm lake at blue hour with a small illuminated historic European city on the far bank, hills and dramatic dark mountains behind, warm golden windows reflected in the water, photorealistic serene wide view.

## Other assets

- DM Sans and Instrument Serif are bundled locally from Google Fonts. Their SIL Open Font
  License files are included in `ui/fonts/`.
- QRCode.js by davidshimjs is bundled with its MIT license in `ui/qrcode.LICENSE.txt`.
- Installed app icons come from Android PackageManager. Functional Netflix/YouTube/Prime
  marks and line controls are rendered in local HTML/SVG; no external icon service is used.
