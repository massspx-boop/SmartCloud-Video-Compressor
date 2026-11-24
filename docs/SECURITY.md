# Security and privacy notes

- Video processing is local; the app does not upload media to a server.
- Input and output are selected through Android Storage Access Framework.
- The app never overwrites the selected source document.
- SCX chunk sizes and filename lengths are validated before allocation.
- Extracted filenames are reduced to safe basenames; path separators and control characters are removed.
- SHA-256 verifies restored bytes against the SCX footer but does not authenticate a publisher.
- Failed operations can leave a partial destination document. Delete incomplete outputs before retrying.
- Transcoding uses temporary app-private cache files. Cancelled or failed exports should be cleaned by the app; the user should still check storage if the OS terminates the app unexpectedly.
