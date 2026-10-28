# Infinite Marketplace Android

An ad-free Minecraft community-content companion app with:
- Live Java mod and modpack search via Modrinth's public API.
- Direct links to original Bedrock add-on, texture, map, and skin sources.
- A basic 64x64 skin texture painter.
- Starter Bedrock custom-item add-on export (.mcaddon).
- Starter Java data-pack and command function exports.
- No advertising SDK or analytics SDK.

## Build
The repository's `.github/workflows/build-infinite-marketplace.yml` workflow builds a debug APK with GitHub Actions. Open the Actions tab, select **Build Infinite Marketplace APK**, and download the `infinite-marketplace-debug-apk` artifact from a successful run.

## Limits
This is a companion app, not a Minecraft launcher, and does not bundle Minecraft binaries. Java mods and Bedrock add-ons are different formats. Generated packs are starter templates and should be tested against the target game version. Third-party files remain on their original host and should only be redistributed where the license or creator permits it.
