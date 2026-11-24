# SmartCloud Video Compressor (SCVC)

SCVC is an offline-first Android application for exact file archiving and video re-encoding.

## Features in this build
- Exact Mode: SCX v1 chunked DEFLATE with RAW fallback and SHA-256 verification.
- Restore Mode: exact original byte reconstruction; corrupted data is rejected. Restore uses the embedded original filename and extension with a matching document MIME type where Android supports it.
- Data-saving Mode: AndroidX Media3 Transformer with device media codecs, H.264 video, AAC audio, 480p / 720p / 1080p output profiles, and explicit target video bitrates (700 kbps / 1.4 Mbps / 2.5 Mbps). These are requested bitrates; device codecs may adjust them.
- Safe file handling: Android Storage Access Framework; source documents are not overwritten.
- Operation history: local SQLite records mode, source/output sizes, result and output SHA-256.
- Low-memory approach: streaming file copies and SCX chunks capped at 128 KiB.
- GitHub Actions: unit tests and debug APK artifact.

## Important limitations
SCX is an exact-byte archive, not a high-efficiency video codec. Already-compressed video generally uses RAW chunks and will not shrink meaningfully. Use Video Compress to re-encode to MP4; lower bitrates and resolutions can reduce size but may reduce visual quality. Requested bitrates are best-effort because Android device encoders can clamp or override them.
SCX preserves exact bytes and should not be expected to shrink already-compressed H.264/H.265/AV1 videos materially. Data-saving creates a new MP4 and changes the encoded bytes; visual quality can change. A smaller output is not guaranteed. Compare sizes and test playback before deleting the source.

This build works offline and does not upload videos. Hardware codec support varies by device. Keep the app open during long exports; durable background processing is not included in this release. Physical-device validation is still required before describing the APK as verified on a realme C30.

## Build
Open GitHub Actions → Android APK. Download the SCVC-debug-apk artifact, extract the ZIP, and install app-debug.apk.

See docs/SCX_SPEC.md, docs/SECURITY.md and docs/TESTING.md.
