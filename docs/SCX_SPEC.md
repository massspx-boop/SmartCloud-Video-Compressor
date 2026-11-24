# SCX Container Specification — Version 1

Status: implemented project-specific format; not an established industry standard.

## Binary layout
All integers are big-endian.
1. Magic: 4 bytes, ASCII SCX1.
2. Version: unsigned 16-bit integer, value 1.
3. Chunk size: unsigned 32-bit integer, value 131072.
4. Original name length: unsigned 16-bit integer, 1–1024.
5. Original UTF-8 basename bytes.
6. Repeated chunk records: uncompressed length (signed 32-bit, 1–131072), stored length (signed 32-bit, 1–263168), codec byte (0 RAW, 1 DEFLATE), payload of exactly stored length.
7. End record: lengths 0, 0 and codec 0.
8. Footer: 32-byte SHA-256 of the original complete byte stream.

## Hybrid strategy
DEFLATE is used only when its output is smaller than the input chunk; otherwise RAW is stored. Header and record overhead remain, so SCX may be slightly larger than source media.

## Integrity and security
The decoder validates lengths before allocation, rejects unknown versions/codecs, malformed end records, truncated data, trailing data and SHA-256 mismatch. Embedded SHA-256 detects accidental corruption but is not publisher authentication. SCX v1 has no signature or random-access index.
