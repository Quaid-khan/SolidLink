#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROTOC_BIN="${PROTOC:-protoc}"
SWIFT_PLUGIN="${PROTOC_GEN_SWIFT:-protoc-gen-swift}"
PROTO_FILE="$ROOT_DIR/Resources/solidlink.proto"
OUT_DIR="$ROOT_DIR/Sources/SolidLinkCore/Generated"

command -v "$PROTOC_BIN" >/dev/null 2>&1 || { echo "protoc is required" >&2; exit 127; }
command -v "$SWIFT_PLUGIN" >/dev/null 2>&1 || { echo "protoc-gen-swift is required; build it from apple/swift-protobuf" >&2; exit 127; }

mkdir -p "$OUT_DIR"
"$PROTOC_BIN" \
  --plugin="protoc-gen-swift=$SWIFT_PLUGIN" \
  --swift_opt=Visibility=Public \
  --swift_out="$OUT_DIR" \
  "$PROTO_FILE"
