# Vissora

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