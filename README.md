# SolidLink

SolidLink is an Android-first, privacy-first, reliability-first peer-to-peer file transfer product. The primary launch target is a native Kotlin Android application built with Jetpack Compose. A native SwiftUI iPhone companion implements the same transport-neutral transfer contract and wire protocol in the `ios/SolidLinkiOS` package.

This repository is intentionally being built in ordered, verified increments. The current increment includes a real Android LAN/NSD advertiser and browser, a bidirectional Protobuf HELLO smoke handshake, and an iOS Bonjour/Network discovery-and-socket foundation. Full authenticated file transfer, durable transfer orchestration, and physical Android↔iPhone interoperability validation remain separate work and are not claimed as complete.

## Architecture rules

The core workflow must work without Internet access. The primary cross-platform path is local-network discovery through Android NSD and iOS Bonjour/Network framework. Android Wi-Fi Direct and Wi-Fi Aware are capability-gated Android-to-Android adapters, not the iPhone transport. No cloud relay, remote rendezvous, remote DNS, telemetry, advertisements, or cellular fallback may be introduced into the core transfer path.

File bytes must not be accepted before local-only routing policy, authenticated session establishment, and explicit peer approval succeed. Files are streamed in bounded chunks; the receiver persists verified checkpoints, verifies the final digest, and commits atomically. Session keys and private keys must never be stored in Room.

The wire protocol is language-neutral Protobuf using `protocol/solidlink.proto`, a single `Envelope.oneof`, official delimited-message APIs, strict size limits, and bounded `Chunk.payload` messages. Custom magic bytes, manual framing, JSON/base64 file payloads, trust-all certificate validation, plaintext fallback, and fake production adapters are prohibited.

## Module graph

| Module | Foundation role |
| --- | --- |
| `:app` | Android application, Compose entry point, manifest, and composition root |
| `:core:common` | Pure Kotlin shared utilities and result types |
| `:core:domain` | Pure Kotlin domain models, policies, and state machines |
| `:core:crypto` | Security interfaces and cryptographic implementation boundary |
| `:core:protocol` | Protobuf schema, generated bindings, and stream codec |
| `:core:transfer` | Chunking, verification, checkpointing, retry, and commit engine |
| `:data:db` | Room persistence and migrations |
| `:data:files` | SAF readers, private staging, and export |
| `:transport:api` | Transport-neutral discovery and byte-channel contracts |
| `:transport:lan-nsd` | Primary local-LAN Android transport |
| `:transport:wifi-direct` | Android-to-Android Wi-Fi Direct optimization |
| `:transport:wifi-aware` | Capability-gated Android Wi-Fi Aware adapter |
| `:transport:nearby` | Optional Google Play services adapter |
| `:platform:transfer-service` | Android foreground transfer lifecycle boundary |
| `:feature:transfer` | Transfer UI and application-facing feature layer |
| `:testkit` | Deterministic loopback, clocks, sources, and fault injection |

## Verification commands

Run the following from the repository root after the Android SDK and Gradle wrapper are available:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

The first phase is complete only when the baseline builds, unit tests pass, lint passes, and the domain module remains free of Android imports.

## Sources

The implementation baseline follows the supplied architecture document and current official Android guidance for [AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [AGP/Gradle compatibility](https://developer.android.com/build/releases/about-agp), and [Compose Compiler and BOM setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).
