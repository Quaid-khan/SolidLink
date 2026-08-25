# SolidLink iPhone Companion Foundation

This directory contains the native SwiftUI and Swift Package foundation for the SolidLink iPhone companion. Android remains the primary implementation and the shared wire contract remains authoritative at [`core/protocol/src/main/proto/solidlink.proto`](../core/protocol/src/main/proto/solidlink.proto).

## Scope of this phase

The foundation provides an iOS 17 SwiftUI entry point, a shared observable app model, document-picker selection, balanced security-scoped URL access, privacy metadata for local Bonjour discovery, and dedicated screens for Send, Receive, Peer Approval, Active Transfer, History, Staged Files, Export, and Settings. Empty or unavailable states are explicit; the UI does not create fake peers, fake progress, or sample transfer history.

The iOS foundation now includes a Network.framework Bonjour advertiser/browser and local TCP socket connection path. It can advertise, discover, and open a local connection to an Android peer when built on a real iPhone. The native authenticated Protobuf handshake, transfer engine wiring, and Android↔iPhone file-transfer verification remain separate work; no received file is exportable until the later transfer and verification layers mark it complete.

## Toolchain and build

A macOS host with Xcode, the iOS SDK, Swift Package Manager, `protoc`, and the official `protoc-gen-swift` plugin is required. The Linux sandbox used for this phase has none of the Apple build tools, so `swift build`, `xcodebuild`, and runtime tests on an iPhone are **pending and not claimed as completed**.

On macOS, generate the isolated Swift bindings from the committed proto before opening or archiving the iOS app:

```sh
cd ios/SolidLinkiOS
./Scripts/generate-swift-protobuf.sh
```

The script uses the official SwiftProtobuf plugin and writes generated `*.pb.swift` files only under `Sources/SolidLinkCore/Generated/`. Generated files must not be hand-edited or replaced with handwritten protocol models. The SwiftProtobuf runtime is declared in `Package.swift`.

The package is intended to be linked into an Xcode iOS application target. The app target must include `Resources/Info.plist` or equivalent target settings and must not add background modes, tracking, advertising, contacts, broad photo-library access, or cloud relay capabilities.

## Privacy and wire invariants

`Resources/Info.plist` declares `NSLocalNetworkUsageDescription` and the interoperable Bonjour service type `_solidlink._tcp`. The local-only invariant is mandatory: peer discovery and file bytes stay on the local network, with no public endpoint, cellular, remote DNS, telemetry, account, or cloud fallback.

The wire format is the shared proto3 `Envelope` with official length-delimited Protobuf framing. Chunk data is carried by the generated Protobuf message and is never represented as JSON or base64. The serialized envelope ceiling is 1 MiB, matching Android’s protocol contract.

## Local static verification

From the repository root:

```sh
./ios/SolidLinkiOS/Scripts/verify-ios-foundation.sh
```

This verifies source parity, privacy keys, SwiftUI/document-access anchors, SwiftProtobuf generation wiring, and the absence of manual JSON/base64 framing in the iOS source. It does not substitute for an Xcode build, simulator run, physical iPhone test, or Bonjour interoperability test.
