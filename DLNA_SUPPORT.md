# Universal Casting Support

This document describes the universal casting support added to AniTail for streaming music to various devices including Chromecast, DLNA/UPnP devices, and AirPlay devices.

## Supported Casting Protocols

### Chromecast
The original casting protocol supported by AniTail, works with all Chromecast-enabled devices and requires Google Play Services.

### DLNA/UPnP
DLNA (Digital Living Network Alliance) is a standard that allows devices to share media over a network. Unlike Chromecast which requires Google Play Services, DLNA works with a wide variety of devices including:

- LG Smart TVs
- Samsung Smart TVs
- Sony TVs
- Many set-top boxes
- Network media players
- Some speakers and audio systems

### AirPlay
Apple's AirPlay protocol is now supported for devices that implement AirPlay receivers, including:

- Apple TV devices
- HomePod and HomePod mini
- Many modern LG TVs (with AirPlay 2 support)
- Many modern Samsung TVs (with AirPlay 2 support)
- Sony TVs with AirPlay support
- Various AirPlay-compatible speakers and soundbars

## How it works

1. **Device Discovery**: The app uses Android's Network Service Discovery (NSD) to find compatible devices on the local network:
   - Chromecast devices via Google Cast SDK
   - DLNA-compatible devices via UPnP discovery
   - AirPlay devices via Bonjour/mDNS discovery
2. **Device Selection**: Users can choose from Chromecast, DLNA, and AirPlay devices in a unified device picker
3. **Media Streaming**: When a device is selected, the app uses the appropriate protocol to control playback:
   - Chromecast: Uses Google Cast SDK and CastPlayer
   - DLNA: Sends UPnP SOAP requests for media control  
   - AirPlay: Uses HTTP requests following AirPlay protocol

## Implementation Details

### Key Components

- `CastManager.kt`: Handles Chromecast device control via Google Cast SDK
- `DlnaManager.kt`: Handles DLNA device discovery and media control
- `AirPlayManager.kt`: Handles AirPlay device discovery and media control
- `UniversalCastManager.kt`: Manages Cast, DLNA, and AirPlay sessions
- `UniversalDevicePicker.kt`: Shows Cast, DLNA, and AirPlay devices in one dialog
- `UniversalCastButton.kt`: Unified casting button supporting all three protocols

### Network Permissions

The app uses the following permissions for casting functionality:
- `INTERNET`: For HTTP communication with all casting devices
- `ACCESS_NETWORK_STATE`: To check network connectivity
- Network Service Discovery: For finding devices on the local network (DLNA, AirPlay)

### Supported Features

✅ **Device Discovery**: Automatically finds Chromecast, DLNA, and AirPlay devices on the network
✅ **Unified UI**: One button and picker for all three casting protocols
✅ **Basic Playback Control**: Play, pause, stop across all protocols
✅ **Media Metadata**: Sends song title, artist, and album art to compatible devices
✅ **Automatic Fallback**: Falls back to local playback if connection is lost

### Limitations

- **DLNA**: Implementation is simplified and may not work with all devices. Some DLNA devices may require specific authentication or protocols.
- **AirPlay**: Implementation supports basic AirPlay protocol but may not work with all AirPlay devices. Some devices may require authentication or encryption.
- **Network Discovery**: Depends on devices being properly configured and discoverable on the local network.
- **Protocol Support**: Different devices may have varying levels of protocol compliance.

## Usage

1. Ensure your casting device is connected to the same WiFi network as your Android device
2. Enable the appropriate sharing/casting features on your target device:
   - **DLNA devices**: Enable DLNA/media sharing features
   - **AirPlay devices**: Enable AirPlay (usually found in device settings)
3. In AniTail, tap the cast button
4. Select your desired device from the unified device list
5. Start playing music - it should stream to your selected device

## Troubleshooting

### Device not appearing
- Ensure both devices are on the same network
- Check that the appropriate casting/sharing protocol is enabled on the target device:
  - **DLNA**: Enable DLNA/UPnP media sharing
  - **AirPlay**: Enable AirPlay in device settings
- Some devices may take time to appear in discovery
- Try restarting the app or toggling WiFi

### Playback issues
- Ensure the target device supports the audio format being streamed
- Check network connectivity and stability
- **DLNA**: Some devices may require specific UPnP service endpoints
- **AirPlay**: Some devices may require authentication or specific AirPlay versions

### Technical Issues
- Check logs for casting-related messages (tagged with "Cast", "DLNA", or "AirPlay")
- Verify the device responds to discovery requests
- Some devices may use non-standard protocol implementations

## Future Improvements

Potential enhancements for universal casting support:
- Better device type detection and classification
- Support for more protocol variations and implementations
- Enhanced error handling and user feedback for all protocols
- Queue management for DLNA and AirPlay devices
- Volume control integration across all protocols
- Support for video content casting
- Authentication support for secured AirPlay devices
- Enhanced metadata display and album art support