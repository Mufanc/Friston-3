# Friston-3

A Magisk module that enables automatic VoIP call recording on Android, by leveraging hidden AudioPolicy APIs and runtime audioserver patching.

## How It Works

Friston-3 consists of two main components:

- **audioserver-patch** (Rust) -- Runs at boot to patch the `audioserver` process, bypassing recording restrictions that would otherwise block third-party audio capture.
- **friston3.sh** (Kotlin, packaged as an executable) -- A headless process that monitors audio mode changes and active recording sessions. When a VoIP call is detected (e.g. WeChat, Telegram), it automatically captures both uplink and downlink audio, mixes them, and encodes the result to AAC.

### VoIP Recording Flow

1. `AudioModeChangeMonitor` watches for `MODE_IN_COMMUNICATION`.
2. `AudioRecordingStatusMonitor` detects third-party apps using `VOICE_COMMUNICATION` audio source.
3. When both conditions are met, `RecordingController` starts a `VoipRecorder` which:
   - Registers a loopback `AudioPolicy` to capture the remote party's voice (downlink).
   - Opens an `AudioRecord` with `VOICE_COMMUNICATION` source for the local microphone (uplink).
   - Mixes both streams and encodes to AAC (16kHz, mono, 64kbps) with ADTS framing.
4. Recordings are saved to `/data/local/tmp/Friston-3/` as `voip-<package>-<timestamp>.aac`.

## Requirements

- Android 14+ (API 34)
- Rooted device with [Magisk](https://github.com/topjohnwu/Magisk) or [KernelSU](https://github.com/tiann/KernelSU) installed
- Android NDK (for building the Rust component)

## Building

Requires [just](https://github.com/casey/just) and `ANDROID_NDK` environment variable set.

```bash
just build            # Build everything (release)
just package          # Build & package into a flashable Magisk module zip
just clean            # Clean all build artifacts
```

### Project Structure

```
Friston-3/
  core/               # Kotlin - recording logic, monitors, utilities
  hiddenapi/          # Java stubs for Android hidden APIs
  audioserver-patch/  # Rust - runtime audioserver patching
  magisk/             # Magisk module scripts and metadata
  justfile            # Build orchestration
```

## Installation

1. Run `just package` to produce `build/friston3-module.zip`.
2. Flash the zip through Magisk/KernelSU
3. Reboot. The module will automatically start on boot.

## Usage

Once installed, everything is automatic -- no user interaction required. When a VoIP call begins, recording starts; when the call ends, recording stops. Output files are located at:

```
/data/local/tmp/Friston-3/voip-<package>-<YYMMDDHHmm>.aac
```

## Roadmap

- [x] VoIP call recording (WeChat, Telegram, etc.)
- [ ] Cellular call recording
- [ ] Microphone recording support
