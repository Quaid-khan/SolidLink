import Foundation
import XCTest
@testable import SolidLinkCore

final class SolidLinkCoreTests: XCTestCase {
    func testEnvelopeLimitAcceptsBoundedDataAndRejectsOversizedData() {
        XCTAssertNoThrow(try SolidLinkProtocol.validateDelimitedEnvelope(Data(repeating: 0, count: SolidLinkProtocol.maxEnvelopeBytes)))
        XCTAssertThrowsError(try SolidLinkProtocol.validateDelimitedEnvelope(Data(repeating: 0, count: SolidLinkProtocol.maxEnvelopeBytes + 1))) { error in
            XCTAssertEqual(error as? SolidLinkProtocol.WireError, .oversizedEnvelope)
        }
    }

    @MainActor
    func testModelCanClearUserSelectedFiles() {
        let model = SolidLinkAppModel()
        model.addFiles([URL(fileURLWithPath: "/tmp/example.bin")])
        XCTAssertEqual(model.selectedFiles.count, 1)
        model.clearFiles()
        XCTAssertTrue(model.selectedFiles.isEmpty)
    }

    func testSecurityScopedAccessBalancesStartAndStop() {
        var starts = 0
        var stops = 0
        let access = SecurityScopedDocumentAccess(
            url: URL(fileURLWithPath: "/tmp/example.bin"),
            startHandler: { starts += 1; return true },
            stopHandler: { stops += 1 }
        )

        XCTAssertTrue(access.start())
        XCTAssertTrue(access.start())
        XCTAssertEqual(starts, 1)
        access.stop()
        access.stop()
        XCTAssertEqual(stops, 1)
        XCTAssertEqual(access.state, .stopped)
    }

    func testSecurityScopedAccessRecordsDeniedStart() {
        let access = SecurityScopedDocumentAccess(
            url: URL(fileURLWithPath: "/tmp/example.bin"),
            startHandler: { false },
            stopHandler: {}
        )

        XCTAssertFalse(access.start())
        XCTAssertEqual(access.state, .denied)
    }
}
