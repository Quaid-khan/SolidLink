import Combine
import Foundation
import SwiftProtobuf
#if canImport(Network)
import Network
#endif

public enum SolidLinkProtocol {
    public static let version = "1"
    public static let serviceName = "_solidlink._tcp"
    public static let defaultChunkBytes = 256 * 1024
    public static let maxEnvelopeBytes = 1024 * 1024

    public enum WireError: Error, Equatable {
        case oversizedEnvelope
        case truncatedEnvelope
        case missingEnvelopeBody
    }

    public static func validateDelimitedEnvelope(_ bytes: Data) throws {
        guard bytes.count <= maxEnvelopeBytes else { throw WireError.oversizedEnvelope }
        guard !bytes.isEmpty else { throw WireError.truncatedEnvelope }
    }
}

public struct SelectedFile: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let url: URL

    public init(id: UUID = UUID(), url: URL) {
        self.id = id
        self.url = url
    }
}

@MainActor
public final class SolidLinkAppModel: ObservableObject {
    @Published public private(set) var selectedFiles: [SelectedFile] = []
    @Published public private(set) var discoveredPeers: [BonjourPeer] = []
    @Published public private(set) var isLocalNetworkAuthorized = false
    @Published public private(set) var discoveryStatus = "Local discovery is stopped"
    @Published public private(set) var transferMessage = "No transfer started"
    @Published public var peerApprovalRequired = true
    @Published public var advancedSasEnabled = true

    private var bonjourTransport: BonjourTransport?
#if canImport(Network)
    private var activeConnection: NWConnection?
#endif

    public init() {}

    public func addFiles(_ urls: [URL]) {
        selectedFiles.append(contentsOf: urls.map { SelectedFile(url: $0) })
    }

    public func clearFiles() {
        selectedFiles.removeAll()
    }

    public func markLocalNetworkAuthorizationChecked(_ authorized: Bool) {
        isLocalNetworkAuthorized = authorized
    }

    public func startLocalDiscovery() {
        guard bonjourTransport == nil else { return }
        let transport = BonjourTransport(instanceName: "SolidLink iPhone")
#if canImport(Network)
        transport.onPeersChanged = { [weak self] peers in
            Task { @MainActor in
                self?.discoveredPeers = peers
                self?.discoveryStatus = peers.isEmpty
                    ? "Searching for nearby peers on local Wi-Fi"
                    : "Found \(peers.count) local peer\(peers.count == 1 ? "" : "s")"
            }
        }
        transport.onStatusChanged = { [weak self] status in
            Task { @MainActor in self?.discoveryStatus = status }
        }
#endif
        bonjourTransport = transport
        discoveryStatus = "Starting local Wi-Fi discovery"
        transport.start()
    }

    public func stopLocalDiscovery() {
        bonjourTransport?.stop()
        bonjourTransport = nil
        discoveredPeers.removeAll()
        discoveryStatus = "Local discovery is stopped"
#if canImport(Network)
        activeConnection?.cancel()
        activeConnection = nil
#endif
    }

    public func connect(to peer: BonjourPeer) {
#if canImport(Network)
        guard let bonjourTransport else {
            discoveryStatus = "Start local discovery before connecting"
            return
        }
        discoveryStatus = "Connecting to \(peer.displayName)"
        bonjourTransport.connect(to: peer) { [weak self] result in
            Task { @MainActor in
                switch result {
                case .success(let connection):
                    self?.activeConnection = connection
                    self?.transferMessage = "Local socket connected to \(peer.displayName); protocol handshake is next."
                    self?.discoveryStatus = "Connected to \(peer.displayName) on local Wi-Fi"
                case .failure(let error):
                    self?.discoveryStatus = "Connection failed: \(error.localizedDescription)"
                }
            }
        }
#else
        discoveryStatus = "iOS local networking requires an Apple device build"
#endif
    }
}
