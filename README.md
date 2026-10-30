# Vissora

A Unified AI Based Vision Engine that tracks for unknown entities, vechiles, etc and store facial embeddings, ANPR Data, and many more. It provides features including cross-match, reverse vechile search, etc.

# Screenshots

<table>
  <tr>
    <td><img src="assets/Screenshot 2026-10-27 114043.png" width="400"></td>
    <td><img src="assets/Screenshot 2026-10-27 114149.png" width="400"></td>
  </tr>
  <tr>
    <td><img src="assets/Screenshot 2026-10-27 114156.png" width="400"></td>
    <td><img src="assets/Screenshot 2026-10-27 114339.png" width="400"></td>
  </tr>
</table>

### Build and Run

Run the following commands from the `Software` directory.

**Windows (PowerShell):**
```powershell
.\gradlew.bat run
```

**Linux / macOS:**
```bash
./gradlew run
```

## Manage Cameras

To add or remove cameras, edit the following source file:

```text
desktopMain/kotlin/.../cpanel.kt
```

- **Add a camera:** Update the camera configuration or list in `cpanel.kt`.
- **Remove a camera:** Remove the corresponding camera entry from `cpanel.kt`.

After making changes, rebuild and install the application using the appropriate Gradle command above.

! PLEASE CHECK AND MAKE SURE THE STREAM URL IS VALID AND REACHABLE BEFORE STARTING THE STREAM. MODIFY, ADD OR REMOVE STREAMS FROM THE CPanel.kt file inside androidMain
