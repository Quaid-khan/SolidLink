import Foundation

public enum DocumentPermissionState: Equatable, Sendable {
    case notStarted
    case granted
    case denied
    case stopped
}

public final class SecurityScopedDocumentAccess {
    public let url: URL
    public private(set) var state: DocumentPermissionState = .notStarted

    private let startHandler: () -> Bool
    private let stopHandler: () -> Void

    public init(
        url: URL,
        startHandler: @escaping () -> Bool,
        stopHandler: @escaping () -> Void
    ) {
        self.url = url
        self.startHandler = startHandler
        self.stopHandler = stopHandler
    }

    public convenience init(url: URL) {
        self.init(
            url: url,
            startHandler: { url.startAccessingSecurityScopedResource() },
            stopHandler: { url.stopAccessingSecurityScopedResource() }
        )
    }

    @discardableResult
    public func start() -> Bool {
        guard state == .notStarted || state == .stopped else {
            return state == .granted
        }
        if startHandler() {
            state = .granted
            return true
        }
        state = .denied
        return false
    }

    public func stop() {
        guard state == .granted else {
            if state == .notStarted { state = .stopped }
            return
        }
        stopHandler()
        state = .stopped
    }

    deinit {
        stop()
    }
}

public struct DocumentDescriptor: Equatable, Sendable {
    public let url: URL
    public let filename: String
    public let isDirectory: Bool

    public init(url: URL, isDirectory: Bool) {
        self.url = url
        self.filename = url.lastPathComponent
        self.isDirectory = isDirectory
    }
}
