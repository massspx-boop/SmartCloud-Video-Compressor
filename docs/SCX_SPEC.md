# SCX Container Specification — Version 1

Status: initial implementation specification. This is a project format, not an established industry standard.

## Purpose
SCX v1 stores an arbitrary source file in independently compressed chunks while preserving the exact original byte sequence. It does not decode or re-encode video.

## Byte order and limits
All integers are big-endian. The supported chunk profile is exactly 131072 bytes. A decoder must reject unsupported versions, invalid lengths, unknown codecs, malformed end markers, truncated records, and integrity failures.

## Layout
1. Magic: 4 bytes, ASCII `SCX1`.
2. Version: unsigned 16-bit integer, value 1.
3. Chunk size: unsigned 32-bit integer, value 131072.
4. Original name length: unsigned 16-bit integer, 1–1024.
5. Original UTF-8 filename bytes.
6. Repeated chunk records:
   - uncompressed length: signed 32-bit, 1–131072;
   - stored length: signed 32-bit, 1–263168;
   - codec: unsigned 8-bit (0 = RAW, 1 = DEFLATE);
   - payload: exactly stored length bytes.
7. End record: uncompressed length 0, stored length 0, codec 0.
8. Integrity footer: 32-byte SHA-256 of the complete original byte stream.

## Hybrid selection
The encoder tries DEFLATE for each chunk. It stores DEFLATE only when the compressed payload is smaller than the source chunk; otherwise it stores RAW. This avoids wasting space on incompressible chunks, although record/header/footer overhead remains.

## Integrity
The decoder recomputes SHA-256 over restored bytes and compares it with the footer using a constant-time digest comparison. SHA-256 detects accidental corruption relative to the stored digest; it does not authenticate who created the file. Signed manifests are outside v1.

## Streaming and memory
The Android implementation uses 128 KiB source chunks and bounded temporary buffers. It must not load the entire media file into memory.

## Compatibility
Unknown versions or codec IDs must fail closed. Future incompatible changes require a new version. Do not silently interpret unknown flags or codec values.
