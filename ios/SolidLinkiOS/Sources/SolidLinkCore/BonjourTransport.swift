#if canImport(Network)
import Foundation
import Network

public struct BonjourPeer: Identifiable, Hashable, Sendable {
    public let id: String
    public let displayName: String
    public let serviceType: String

    fileprivate init(id: String, displayName: String, serviceType: String) {
        self.id = id
        self.displayName = displayName
        self.serviceType = serviceType
    }
}

public final class BonjourTransport: @unchecked Sendable {
    public typealias PeerChangeHandler = @Sendable ([BonjourPeer]) -> Void
    public typealias StatusHandler = @Sendable (String) -> Void

    private let queue = DispatchQueue(label: "com.solidlink.bonjour", qos: .userInitiated)
    private let serviceType: String
    private let instanceName: String
    private var browser: NWBrowser?
    private var listener: NWListener?
    private var connections: [NWConnection] = []
    private var endpoints: [String: NWEndpoint] = [:]
    private var peers: [String: BonjourPeer] = [:]

    public var onPeersChanged: PeerChangeHandler?
    public var onStatusChanged: StatusHandler?

    public init(instanceName: String, serviceType: String = "_solidlink._tcp") {
        self.instanceName = instanceName
        self.serviceType = serviceType
    }

    public func start() {
        queue.async { [weak self] in
            self?.startOnQueue()
        }
    }

    public func stop() {
        queue.async { [weak self] in
            guard let self else { return }
            self.browser?.cancel()
            self.listener?.cancel()
            self.connections.forEach { $0.cancel() }
            self.connections.removeAll()
            self.browser = nil
            self.listener = nil
            self.endpoints.removeAll()
            self.peers.removeAll()
            self.publishPeers()
            self.publishStatus("Local discovery stopped")
        }
    }

    public func connect(to peer: BonjourPeer, completion: @escaping @Sendable (Result<NWConnection, Error>) -> Void) {
        queue.async { [weak self] in
            guard let self, let endpoint = self.endpoints[peer.id] else {
                completion(.failure(TransportError.peerUnavailable))
                return
            }
            let connection = NWConnection(to: endpoint, using: .tcp)
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    completion(.success(connection))
                case .failed(let error):
                    completion(.failure(error))
                case .cancelled:
                    completion(.failure(TransportError.connectionCancelled))
                default:
                    break
                }
            }
            connection.start(queue: self.queue)
        }
    }

    private func startOnQueue() {
        guard browser == nil, listener == nil else { return }
        do {
            let listener = try NWListener(using: .tcp)
            listener.service = NWListener.Service(name: instanceName, type: serviceType)
            listener.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    self?.publishStatus("Advertising on the local Wi-Fi network")
                case .failed(let error):
                    self?.publishStatus("Local listener failed: \(error.localizedDescription)")
                case .cancelled:
                    self?.publishStatus("Local listener stopped")
                default:
                    break
                }
            }
            listener.newConnectionHandler = { [weak self] connection in
                guard let self else { return }
                self.connections.append(connection)
                connection.stateUpdateHandler = { [weak self, weak connection] state in
                    if case .failed = state, let connection {
                        self?.connections.removeAll { $0 === connection }
                    }
                    if case .cancelled = state, let connection {
                        self?.connections.removeAll { $0 === connection }
                    }
                }
                connection.start(queue: self.queue)
            }
            listener.start(queue: queue)
            self.listener = listener

            let browser = NWBrowser(for: .bonjour(type: serviceType, domain: nil), using: .tcp)
            browser.stateUpdateHandler = { [weak self] state in
                switch state {
                case .ready:
                    self?.publishStatus("Searching for nearby SolidLink peers")
                case .failed(let error):
                    self?.publishStatus("Peer discovery failed: \(error.localizedDescription)")
                case .cancelled:
                    self?.publishStatus("Peer discovery stopped")
                default:
                    break
                }
            }
            browser.browseResultsChangedHandler = { [weak self] results, _ in
                guard let self else { return }
                self.peers.removeAll()
                self.endpoints.removeAll()
                for result in results {
                    guard case .service(let name, let type, _, _) = result.endpoint else { continue }
                    let id = "\(name)|\(type)"
                    self.peers[id] = BonjourPeer(id: id, displayName: name, serviceType: type)
                    self.endpoints[id] = result.endpoint
                }
                self.publishPeers()
            }
            browser.start(queue: queue)
            self.browser = browser
        } catch {
            publishStatus("Local discovery unavailable: \(error.localizedDescription)")
        }
    }

    private func publishPeers() {
        let snapshot = Array(peers.values).sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
        onPeersChanged?(snapshot)
    }

    private func publishStatus(_ status: String) {
        onStatusChanged?(status)
    }
}

public enum TransportError: Error, LocalizedError {
    case peerUnavailable
    case connectionCancelled

    public var errorDescription: String? {
        switch self {
        case .peerUnavailable:
            return "The peer is no longer available on the local network."
        case .connectionCancelled:
            return "The local connection was cancelled."
        }
    }
}
#else
import Foundation

public struct BonjourPeer: Identifiable, Hashable, Sendable {
    public let id: String
    public let displayName: String
    public let serviceType: String

    public init(id: String, displayName: String, serviceType: String = "_solidlink._tcp") {
        self.id = id
        self.displayName = displayName
        self.serviceType = serviceType
    }
}

public final class BonjourTransport: @unchecked Sendable {
    public init(instanceName: String, serviceType: String = "_solidlink._tcp") {}
    public func start() {}
    public func stop() {}
}
#endif
