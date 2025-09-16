# Cast Package Documentation

This package contains the casting functionality for AniTail, supporting both Google Cast (Chromecast) and DLNA/UPnP devices.

## File Structure

### Core Components
- `CastManager.kt` - Original Google Cast manager
- `DlnaManager.kt` - DLNA/UPnP device management and control
- `DlnaDeviceDiscovery.kt` - Enhanced device discovery for DLNA devices
- `UniversalCastManager.kt` - Unified manager for both Cast and DLNA
- `UniversalDevicePicker.kt` - Device selection dialog showing both types
- `UniversalCastButton.kt` - Unified casting button component

### Legacy Components (Still Used)
- `CastOptionsProvider.kt` - Google Cast configuration
- `CastComposeActivity.kt` - Cast control UI
- `CastDevicePicker.kt` - Original Cast-only device picker
- `CastExpandedActivity.kt` - Extended Cast controls

## Architecture

```
UniversalCastButton
        ↓
UniversalDevicePicker
        ↓
UniversalCastManager
    ↙         ↘
CastManager   DlnaManager
                ↓
        DlnaDeviceDiscovery
```

## Usage Flow

1. User taps the cast button in the UI
2. `UniversalDevicePicker` shows available Cast and DLNA devices
3. When a device is selected:
   - Cast devices: Use existing Google Cast infrastructure
   - DLNA devices: Use `DlnaManager` to send UPnP commands
4. Music playback is controlled through the respective managers

## DLNA Implementation Details

### Device Discovery
- Uses Android's Network Service Discovery (NSD)
- Searches for multiple service types: `_http._tcp`, `_upnp._tcp`, etc.
- Applies heuristics to identify DLNA-capable devices
- Resolves service information to get IP addresses and ports

### Media Control
- Sends UPnP SOAP requests over HTTP
- Supports basic AVTransport actions: SetAVTransportURI, Play, Pause, Stop
- Includes DIDL-Lite metadata for song information

### Error Handling
- Graceful fallback when devices are unavailable
- Automatic disconnection when devices are lost
- Logging for debugging connection issues

## Integration Points

The casting functionality integrates with:
- `MusicService` - Main playback service
- `MiniPlayer` - UI component with cast button
- Media session - For playback state synchronization

## Future Enhancements

- Better DLNA device compatibility testing
- Support for additional UPnP services
- Queue management for DLNA devices
- Volume control integration
- Enhanced error messages for users