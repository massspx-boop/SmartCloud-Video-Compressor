# SCVC release test plan

## Automated CI
- Exact SCX round-trip with mixed random/compressible data.
- Empty-file round-trip.
- Input/output byte accounting.
- Corrupt SHA-256 footer rejected.
- Stored filename path traversal blocked.
- GitHub Actions runs unit tests and assembles a debug APK.

## Required physical-device validation (not yet claimed complete)
Test on realme C30 / Android 11 Go / 2 GB RAM:
1. Install and launch.
2. Pack/extract a text file, image, 100 MB and 500 MB video; compare SHA-256.
3. Test RAW fallback and monitor memory.
4. Transcode short MP4 to 480p and 720p; verify audio, rotation, duration, playback and output dimensions.
5. Test long media while monitoring heat, battery, memory and free storage.
6. Cancel an export; verify the source remains unchanged and remove partial output.
7. Test unsupported/corrupt media and insufficient storage.
8. Compare output sizes; do not assume re-encoding always reduces them.

## Known limitations
- Media3 and hardware codec support varies by device and source format.
- Keep the app open during long video exports in this release.
- No cloud upload/server queue, signed SCX manifest, or random-access index.
