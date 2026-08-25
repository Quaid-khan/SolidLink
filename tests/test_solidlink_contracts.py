from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def test_android_hamburger_is_wired_to_a_real_drawer():
    source = read("app/src/main/java/com/solidlink/app/HomeScreen.kt")
    assert "ModalNavigationDrawer" in source
    assert "rememberDrawerState(DrawerValue.Closed)" in source
    assert "drawerState.open()" in source
    assert "NavigationDrawerItem" in source
    assert 'NavigationDestination("Settings"' in source


def test_android_drawer_has_real_destination_content():
    source = read("app/src/main/java/com/solidlink/app/HomeScreen.kt")
    for destination in ("Send", "Receive", "Peer Approval", "Active Transfer", "History", "Settings"):
        assert f'"{destination}"' in source
    assert "DestinationScreen" in source


def test_android_app_wires_lan_transport_and_protocol():
    gradle = read("app/build.gradle.kts")
    view_model = read("app/src/main/java/com/solidlink/app/SolidLinkViewModel.kt")
    assert 'implementation(project(":transport:lan-nsd"))' in gradle
    assert 'implementation(project(":core:protocol"))' in gradle
    for anchor in ("advertise", "discover", "performHello", "ProtobufFrameCodec.encode", "localOnlyRequired"):
        assert anchor in view_model


def test_transport_codec_uses_official_protobuf_runtime():
    gradle = read("transport/api/build.gradle.kts")
    codec = read("transport/api/src/main/kotlin/com/solidlink/transport/api/ProtoDelimitedIo.kt")
    assert "protobuf-java:4.36.0" in gradle
    assert "CodedInputStream" in codec
    assert "CodedOutputStream" in codec
    assert "readRawVarint32" in codec
    assert "writeUInt32NoTag" in codec
    assert "while (" not in codec


def test_local_only_endpoint_policy_rejects_public_routing():
    source = read("transport/api/src/main/kotlin/com/solidlink/transport/api/TransportApi.kt")
    assert "isLocalAddress" in source
    assert "isMulticastAddress" in source
    assert "192 && bytes[1] == 168" in source
    assert "endpoint.locallyDiscovered" in source


def test_android_manifest_has_minimal_local_transfer_permissions():
    manifest = read("app/src/main/AndroidManifest.xml")
    for permission in (
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
    ):
        assert permission in manifest
    assert 'android:exported="false"' in manifest


def test_ios_has_real_bonjour_network_foundation():
    transport = read("ios/SolidLinkiOS/Sources/SolidLinkCore/BonjourTransport.swift")
    model = read("ios/SolidLinkiOS/Sources/SolidLinkCore/ProtocolContract.swift")
    assert "NWBrowser" in transport
    assert "NWListener" in transport
    assert "_solidlink._tcp" in transport
    assert "startLocalDiscovery" in model
    assert "connect(to peer: BonjourPeer)" in model


def test_ios_privacy_metadata_and_shared_service_type_are_present():
    plist = read("ios/SolidLinkiOS/Resources/Info.plist")
    assert "NSLocalNetworkUsageDescription" in plist
    assert "NSBonjourServices" in plist
    assert "_solidlink._tcp" in plist


def test_repository_status_is_honest_about_physical_device_validation():
    readme = read("README.md")
    ios_readme = read("ios/README.md")
    assert "physical Android↔iPhone interoperability validation remain separate work" in readme
    assert "runtime tests on an iPhone are **pending and not claimed as completed**" in ios_readme
