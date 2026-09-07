# Friston-3

![Friston-3 — Preserve the voices.](artwork/friston-3-banner.png)[^artwork]

*After the long vigil, a quieter duty remains: to keep the voices that time would otherwise carry away.*

## About

A Magisk module that enables automatic VoIP and cellular call recording on Android, by leveraging hidden audio APIs and runtime audioserver patching.

## How It Works

Friston-3 consists of two main components:

- **audioserver-patch** (Rust) -- Runs at boot to patch the `audioserver` process, bypassing recording restrictions that would otherwise block third-party audio capture.
- **friston3.sh** (Kotlin, packaged as an executable) -- A headless process that monitors VoIP audio sessions and cellular call state, then records calls automatically and encodes them to AAC.

### VoIP Recording Flow

1. `AudioModeChangeMonitor` watches for `MODE_IN_COMMUNICATION`.
2. `AudioRecordingStatusMonitor` detects third-party apps using `VOICE_COMMUNICATION` audio source.
3. When both conditions are met, `RecordingController` starts a `VoipRecorder` which:
   - Registers a loopback `AudioPolicy` to capture the remote party's voice (downlink).
   - Opens an `AudioRecord` with `VOICE_COMMUNICATION` source for the local microphone (uplink).
   - Mixes both streams and encodes to AAC (16kHz, mono, 64kbps) with ADTS framing.
4. Recordings are saved to `/data/local/tmp/Friston-3/` as `voip-<package>-<timestamp>.aac`.

### Cellular Recording Flow

1. `TelephonyCallStateMonitor` watches each active subscription for call state changes.
2. When a call becomes active, `RecordingController` starts a `CallRecorder` using the `VOICE_CALL` audio source.
3. Audio is encoded to AAC (16kHz, mono, 64kbps) with ADTS framing.
4. After the call ends, a unique matching CallLog entry is used to name the recording; otherwise the number remains `unknown`.

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
  module/             # Magisk module scripts and metadata
  justfile            # Build orchestration
```

## Installation

1. Run `just package` to produce `build/friston3-module.zip`.
2. Flash the zip through Magisk/KernelSU
3. Reboot. The module will automatically start on boot.

## Usage

Once installed, everything is automatic -- no user interaction required. VoIP and cellular calls are recorded when they become active and stopped when they end. Output files are located at:

```
/data/local/tmp/Friston-3/voip-<package>-<YYMMDDHHmm>.aac
/data/local/tmp/Friston-3/call-<number-or-unknown>-<YYMMDDHHmm>.aac
```

## Roadmap

- [x] VoIP call recording (WeChat, Telegram, etc.)
- [x] Cellular call recording
- [ ] Microphone recording support
- [ ] Automatic cloud backup

[^artwork]: The source artwork is copyrighted by Hypergryph (鹰角网络). This banner is an AI-assisted derivative artwork.
