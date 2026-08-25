// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "SolidLinkiOS",
    platforms: [
        .iOS(.v17),
    ],
    products: [
        .library(name: "SolidLinkCore", targets: ["SolidLinkCore"]),
    ],
    dependencies: [
        .package(
            url: "https://github.com/apple/swift-protobuf.git",
            from: "1.6.0"
        ),
    ],
    targets: [
        .target(
            name: "SolidLinkCore",
            dependencies: [
                .product(name: "SwiftProtobuf", package: "swift-protobuf"),
            ],
            path: "Sources/SolidLinkCore",
            resources: [.process("Resources")]
        ),
        .testTarget(
            name: "SolidLinkCoreTests",
            dependencies: ["SolidLinkCore"],
            path: "Tests/SolidLinkCoreTests"
        ),
    ]
)
