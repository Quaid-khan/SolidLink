# Android radio adapters

SolidLink treats Android Wi-Fi Direct as an optional Android-to-Android optimization after the primary LAN NSD path. Wi-Fi Aware is capability-gated and must never be assumed available.

The implementation follows the official Android guidance:

- Wi-Fi Direct requires `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `INTERNET`, `NEARBY_WIFI_DEVICES` on Android 13+, and `ACCESS_FINE_LOCATION` through SDK 32. Peer discovery and service discovery also depend on Location Mode being enabled. The official API is asynchronous and the app must handle state, peer, and connection callbacks.
- Wi-Fi Aware is available from API 26 but may be temporarily unavailable even on supported hardware. The app must check both `PackageManager.FEATURE_WIFI_AWARE` and `WifiAwareManager.isAvailable()`, register for `ACTION_WIFI_AWARE_STATE_CHANGED`, and close attached/discovery sessions when availability changes.
- Wi-Fi Aware discovery messages are only for lightweight control data. Large transfers must use a requested Wi-Fi Aware `Network`, a `ServerSocket` on the publisher, and a socket created from the available network's `SocketFactory`.
- The adapters expose capability and permission state to the app and fail closed with typed unsupported/permission errors. The existing authenticated session and transfer engine remain required above either radio; the radio itself is not treated as a security boundary.

Sources:

- [Create P2P connections with Wi-Fi Direct](https://developer.android.com/develop/connectivity/wifi/wifi-direct)
- [Wi-Fi Aware overview](https://developer.android.com/develop/connectivity/wifi/wifi-aware)
- [WifiP2pManager API reference](https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager)
- [WifiAwareManager API reference](https://developer.android.com/reference/android/net/wifi/aware/WifiAwareManager)
