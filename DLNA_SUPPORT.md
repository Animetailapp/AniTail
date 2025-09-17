# DLNA/UPnP Casting Support

This document describes the DLNA/UPnP casting support added to AniTail for streaming music to devices like LG TVs and other DLNA-compatible devices.

## What is DLNA?

DLNA (Digital Living Network Alliance) is a standard that allows devices to share media over a network. Unlike Chromecast which requires Google Play Services, DLNA works with a wide variety of devices including:

- LG Smart TVs
- Samsung Smart TVs
- Sony TVs
- Many set-top boxes
- Network media players
- Some speakers and audio systems

## How it works

1. **Device Discovery**: The app uses Android's Network Service Discovery (NSD) to find DLNA-compatible devices on the local network
2. **Device Selection**: Users can choose from both Chromecast and DLNA devices in a unified device picker
3. **Media Streaming**: When a DLNA device is selected, the app sends UPnP SOAP requests to control playback

## Implementation Details

### Key Components

- `DlnaManager.kt`: Handles DLNA device discovery and media control
- `UniversalCastManager.kt`: Manages both Cast and DLNA sessions
- `UniversalDevicePicker.kt`: Shows both Cast and DLNA devices in one dialog
- `UniversalCastButton.kt`: Unified casting button supporting both protocols

### Network Permissions

The app uses the following permissions for DLNA functionality:
- `INTERNET`: For HTTP communication with DLNA devices
- `ACCESS_NETWORK_STATE`: To check network connectivity
- Network Service Discovery: For finding DLNA devices on the local network

### Supported Features

✅ **Device Discovery**: Automatically finds DLNA devices on the network
✅ **Unified UI**: One button and picker for both Cast and DLNA
✅ **Basic Playback Control**: Play, pause, stop
✅ **Media Metadata**: Sends song title, artist, and album art
✅ **Automatic Fallback**: Falls back to local playback if connection is lost

### Limitations

- DLNA implementation is simplified and may not work with all devices
- Some DLNA devices may require specific authentication or protocols
- Network discovery depends on devices being properly configured
- DLNA devices may have different levels of UPnP support

## Usage

1. Ensure your DLNA device (e.g., LG TV) is connected to the same WiFi network
2. Enable DLNA/media sharing features on your device
3. In AniTail, tap the cast button
4. Select your DLNA device from the list
5. Start playing music - it should stream to your selected device

## Troubleshooting

### Device not appearing
- Ensure both devices are on the same network
- Check that DLNA/media sharing is enabled on the target device
- Some devices may take time to appear in discovery
- Try restarting the app or toggling WiFi

### Playback issues
- Ensure the DLNA device supports the audio format being streamed
- Check network connectivity and stability
- Some devices may require specific UPnP service endpoints

### Technical Issues
- Check logs for DLNA-related messages (tagged with "DLNA" or "DlnaManager")
- Verify the device responds to UPnP discovery requests
- Some devices may use non-standard UPnP implementations

## Future Improvements

Potential enhancements for DLNA support:
- Better device type detection
- Support for more UPnP service types
- Enhanced error handling and user feedback
- Queue management for DLNA devices
- Volume control integration
- Support for video content casting