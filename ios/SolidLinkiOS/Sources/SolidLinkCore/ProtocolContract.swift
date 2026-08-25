import Combine
import Foundation
import SwiftProtobuf

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
    @Published public private(set) var isLocalNetworkAuthorized = false
    @Published public private(set) var transferMessage = "No transfer started"
    @Published public var peerApprovalRequired = true
    @Published public var advancedSasEnabled = true

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
}
