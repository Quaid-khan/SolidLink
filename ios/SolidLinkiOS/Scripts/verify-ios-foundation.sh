#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "$ROOT_DIR/../.." && pwd)"
ANDROID_PROTO="$REPO_DIR/core/protocol/src/main/proto/solidlink.proto"
IOS_PROTO="$ROOT_DIR/Resources/solidlink.proto"
TARGET_PROTO="$ROOT_DIR/Sources/SolidLinkCore/Resources/solidlink.proto"

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

[[ -f "$ANDROID_PROTO" ]] || fail "Android source proto is missing"
[[ -f "$IOS_PROTO" ]] || fail "iOS source proto is missing"
[[ -f "$TARGET_PROTO" ]] || fail "SwiftPM target proto is missing"
[[ "$(sha256sum "$ANDROID_PROTO" | awk '{print $1}')" == "$(sha256sum "$IOS_PROTO" | awk '{print $1}')" ]] || fail "iOS proto differs from Android proto"
[[ "$(sha256sum "$ANDROID_PROTO" | awk '{print $1}')" == "$(sha256sum "$TARGET_PROTO" | awk '{print $1}')" ]] || fail "SwiftPM target proto differs from Android proto"

if command -v plutil >/dev/null 2>&1; then
    plutil -lint "$ROOT_DIR/Resources/Info.plist" >/dev/null 2>&1 || fail "Info.plist is not valid"
else
    grep -q '<plist' "$ROOT_DIR/Resources/Info.plist" || fail "Info.plist plist root is missing"
    grep -q '<key>NSLocalNetworkUsageDescription</key>' "$ROOT_DIR/Resources/Info.plist" || fail "Info.plist local-network key is malformed"
fi
grep -q 'NSLocalNetworkUsageDescription' "$ROOT_DIR/Resources/Info.plist" || fail "local-network usage description is missing"
grep -q '_solidlink._tcp' "$ROOT_DIR/Resources/Info.plist" || fail "Bonjour service declaration is missing"
grep -q 'swift-protobuf.git' "$ROOT_DIR/Package.swift" || fail "SwiftProtobuf dependency is missing"
grep -q 'protoc-gen-swift' "$ROOT_DIR/Scripts/generate-swift-protobuf.sh" || fail "official SwiftProtobuf generator is not wired"
grep -q '@main' "$ROOT_DIR/Sources/SolidLinkApp/SolidLinkApp.swift" || fail "SwiftUI app entry point is missing"
grep -q 'WindowGroup' "$ROOT_DIR/Sources/SolidLinkApp/SolidLinkApp.swift" || fail "SwiftUI WindowGroup is missing"
grep -q 'fileImporter' "$ROOT_DIR/Sources/SolidLinkApp/HomeView.swift" || fail "system document picker is missing"
grep -q 'startAccessingSecurityScopedResource' "$ROOT_DIR/Sources/SolidLinkCore/DocumentAccess.swift" || fail "security-scoped start lifecycle is missing"
grep -q 'stopAccessingSecurityScopedResource' "$ROOT_DIR/Sources/SolidLinkCore/DocumentAccess.swift" || fail "security-scoped stop lifecycle is missing"

if grep -R -n -E 'JSONSerialization|base64Encoded|base64EncodedString|Firebase|CloudKit|GTMAppAuth' "$ROOT_DIR/Sources"; then
    fail "manual/cloud wire-path shortcut detected"
fi

for screen in SendView ReceiveView PeerApprovalView ActiveTransferView HistoryView StagedFilesView ExportView SettingsView; do
    grep -q "struct $screen" "$ROOT_DIR/Sources/SolidLinkApp/Screens.swift" || fail "$screen is missing"
done

echo "PASS: iOS foundation static checks"
echo "PASS: shared proto SHA-256 $(sha256sum "$ANDROID_PROTO" | awk '{print $1}')"
echo "PENDING: macOS/Xcode/SwiftProtobuf build and iPhone runtime validation"
