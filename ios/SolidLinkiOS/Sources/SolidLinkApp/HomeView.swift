import SwiftUI
import UniformTypeIdentifiers

struct HomeView: View {
    @EnvironmentObject private var model: SolidLinkAppModel
    @State private var isImporterPresented = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Private transfers. No cloud relay.")
                        .font(.title2.weight(.semibold))
                    Text("Choose files, confirm a nearby peer, and transfer over an authenticated local connection. SolidLink does not inspect file contents or upload them to a server.")
                        .foregroundStyle(.secondary)
                }

                Section("1. Select files") {
                    Button("Choose files") {
                        isImporterPresented = true
                    }
                    if model.selectedFiles.isEmpty {
                        Text("No files selected. Access is limited to the files you choose.")
                            .foregroundStyle(.secondary)
                    } else {
                        Text("\(model.selectedFiles.count) file(s) selected and ready for peer confirmation.")
                        Button("Clear", role: .destructive) {
                            model.clearFiles()
                        }
                    }
                }

                Section("2. Nearby peer") {
                    Text("Discovery and peer confirmation will appear here when the local transport is connected. No peer or transfer is fabricated in this state.")
                        .foregroundStyle(.secondary)
                    Label("Waiting for local-network transport", systemImage: "dot.radiowaves.left.and.right")
                        .foregroundStyle(.secondary)
                }

                Section("Privacy controls") {
                    Toggle("Require peer approval", isOn: $model.peerApprovalRequired)
                    Toggle("Allow advanced SAS confirmation", isOn: $model.advancedSasEnabled)
                    Toggle("Local-only routing", isOn: .constant(true))
                        .disabled(true)
                    Text("This safety invariant cannot be disabled: public addresses, cloud fallback, and cellular fallback are rejected.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Workspaces") {
                    NavigationLink("Send") { SendView() }
                    NavigationLink("Receive") { ReceiveView() }
                    NavigationLink("Peer Approval") { PeerApprovalView() }
                    NavigationLink("Active Transfer") { ActiveTransferView() }
                    NavigationLink("History") { HistoryView() }
                    NavigationLink("Staged Files") { StagedFilesView() }
                    NavigationLink("Export") { ExportView() }
                    NavigationLink("Settings") { SettingsView() }
                }

                Section("Transfer history") {
                    Text("Completed transfers will appear here after durable history is connected. No placeholder records are shown.")
                        .foregroundStyle(.secondary)
                    Text("Nothing transferred yet")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.secondary)
                }

                Section {
                    Button(model.selectedFiles.isEmpty ? "Select files to continue" : "Waiting for peer confirmation") {}
                        .disabled(true)
                }
            }
            .navigationTitle("SolidLink")
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
