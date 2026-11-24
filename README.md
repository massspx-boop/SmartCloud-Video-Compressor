# SmartCloud Video Compressor (SCVC)

Android application and SCX container implementation.

## Current implementation scope
- SCX Hybrid file container: chunked DEFLATE compression with RAW fallback.
- Exact byte restoration with SHA-256 verification.
- Streaming I/O with bounded memory use; no whole-video byte arrays.
- Android Storage Access Framework for selecting and saving files.
- GitHub Actions APK build.

**Important:** H.264/H.265/AV1 video is already compressed. Wrapping it in SCX may save little or make it slightly larger; RAW fallback prevents expansion inside SCX, but container metadata still adds a small overhead. This project does not claim a guaranteed size reduction. A future data-saving transcode mode must be benchmarked separately and will create a different video file.

## Build
Open the repository in Android Studio, or use the GitHub Actions workflow under Actions to build the debug APK.

## License
Source code is provided under the MIT License.