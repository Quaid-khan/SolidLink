import SwiftUI
import UniformTypeIdentifiers

struct HomeView: View {
    @EnvironmentObject private var model: SolidLinkAppModel
    @State private var isImporterPresented = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label("Private transfers. No cloud relay.", systemImage: "lock.shield.fill")
                        .font(.title2.weight(.semibold))
                    Text("Move files over the same local Wi-Fi network. SolidLink does not upload them or use cellular fallback.")
                        .foregroundStyle(.secondary)
                }

                Section("1. Select files") {
                    Button {
                        isImporterPresented = true
                    } label: {
                        Label("Choose files", systemImage: "doc.badge.plus")
                    }
                    if model.selectedFiles.isEmpty {
                        Text("Files remain in your control until you choose them.")
                            .foregroundStyle(.secondary)
                    } else {
                        Text("\(model.selectedFiles.count) file(s) ready to send.")
                        ForEach(model.selectedFiles) { file in
                            Label(file.url.lastPathComponent, systemImage: "doc")
                                .lineLimit(1)
                        }
                        Button("Clear selection", role: .destructive) {
                            model.clearFiles()
                        }
                    }
                }

                Section("2. Nearby peers") {
                    HStack {
                        Label(model.discoveryStatus, systemImage: "dot.radiowaves.left.and.right")
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button("Refresh") {
                            model.stopLocalDiscovery()
                            model.startLocalDiscovery()
                        }
                        .buttonStyle(.borderless)
                    }
                    if model.discoveredPeers.isEmpty {
                        Text("Keep both devices on the same Wi-Fi network. Discovery is local-only and uses Bonjour.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(model.discoveredPeers) { peer in
                            HStack {
                                Label(peer.displayName, systemImage: "iphone.gen3")
                                Spacer()
                                Button("Connect") {
                                    model.connect(to: peer)
                                }
                                .buttonStyle(.borderedProminent)
                            }
                        }
                    }
                    if model.transferMessage != "No transfer started" {
                        Text(model.transferMessage)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Privacy controls") {
                    Toggle("Require peer approval", isOn: $model.peerApprovalRequired)
                    Toggle("Allow Advanced Security SAS", isOn: $model.advancedSasEnabled)
                    Toggle("Local-only routing", isOn: .constant(true))
                        .disabled(true)
                    Text("Public addresses, Internet routes, cloud relays, and cellular fallback are rejected.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Transfer readiness") {
                    Label("QR/PIN-first pairing", systemImage: "qrcode")
                    Label("Protobuf protocol", systemImage: "arrow.left.arrow.right")
                    Label("Chunked and resumable transfer", systemImage: "arrow.triangle.2.circlepath")
                    Text("The current repository slice verifies local discovery and socket connection. Full authenticated file transfer still requires the native transfer engine to be connected to this transport.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("SolidLink")
        }
        .task {
            model.startLocalDiscovery()
        }
        .onDisappear {
            model.stopLocalDiscovery()
        }
        .fileImporter(
            isPresented: $isImporterPresented,
            allowedContentTypes: [.item],
            allowsMultipleSelection: true,
        ) { result in
            guard case .success(let urls) = result else { return }
            model.addFiles(urls)
        }
    }
}

#Preview {
    HomeView()
        .environmentObject(SolidLinkAppModel())
}
