# Aura (Aura)

Aura is a feature-rich, high-performance streaming application for Android that provides:
- **Live IPTV**: Stream Free-To-Air channels with built-in DRM filters and support for custom M3U playlists (e.g. JioTV Go, Tata Play).
- **Movies & Series**: High-speed scrapers fetching media details and streaming links.
- **Anime Catalog**: Stream your favorite anime episodes.
- **Ad Security Gateway (AdWall)**: An launch-screen ad gateway supporting both **Start.io** and **Unity Ads** (flexible selection from Settings) with automated Video-to-Interstitial fallback loading.
- **Auto-Updates**: Automatically checks GitHub Releases to notify users about new releases.

---

## Developer Release Workflow (Using GitHub Releases)

To release a new update to users:

1. **Compile the APK**:
   Run the Gradle release task to generate a minified, signed APK:
   ```powershell
   .\gradlew.bat assembleRelease
   ```
   The compiled file will be located at:
   `app/build/outputs/apk/release/app-release.apk`

2. **Create a GitHub Release**:
   * Navigate to [Akshay-307/Aura Releases](https://github.com/Akshay-307/Aura/releases).
   * Click **Draft a new release**.
   * Set a version tag matching standard semantic versioning (e.g., `v1.0.1` or `v2.0.0`). *Make sure this version string is higher than your previous app version!*
   * Upload `app-release.apk` as a release asset.
   * Write your release notes and publish the release.

3. **Auto-Update Delivery**:
   * The app queries the GitHub Releases API (`https://api.github.com/repos/Akshay-307/Aura/releases/latest`) on startup and when triggered in Settings.
   * It parses the release tag and download URL of the uploaded APK, prompting users to install the update if a newer version is available.

