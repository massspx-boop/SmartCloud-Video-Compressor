# Security and Data Handling

- SCVC v1 operates on user-selected documents through Android's Storage Access Framework.
- The app does not request broad shared-storage permissions and does not upload files.
- Chunk lengths are validated before allocation; supported chunks are capped.
- SHA-256 is checked before reporting a successful extraction.
- A failed extraction may leave a partial destination document. Delete that incomplete output and retry.
- Never extract automatically to a privileged or executable location.
- A valid SHA-256 footer detects corruption but is not a publisher signature.
