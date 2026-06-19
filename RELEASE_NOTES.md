# Release Notes

## v1.0.0
### Added
- Initial release of Ventarys AI wrapper.
- Implemented core WebView functionality with JavaScript and DOM storage.
- Added comprehensive support for Puter account login.
- Enabled third-party cookies and multiple window / popup handling.
- Added a splash screen with the app logo that displays during initial page load.
- Added file chooser support (`onShowFileChooser`) to allow uploading and selecting files from the device.

### Changed
- Configured dynamic System UI (Status Bar and Navigation Bar) to adapt to system Light/Dark mode transparently.
- Disabled internal WebView algorithmic darkening so the web application can apply its native dark/light themes.
- Adjusted app icon inset to 25% to ensure the logo is properly framed on Android 10+.

### Fixed
- Fixed issues with external OAuth login redirects (Puter) by intercepting and handling window creation events.
- Fixed edge-to-edge layout issues by making system bars transparent.
