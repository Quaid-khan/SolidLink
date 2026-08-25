import SwiftUI

struct SendView: View {
    @EnvironmentObject private var model: SolidLinkAppModel

    var body: some View {
        List {
            Section("Selected files") {
                if model.selectedFiles.isEmpty {
                    Text("Choose files from the Send screen before requesting a transfer.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(model.selectedFiles) { file in
                        Label(file.url.lastPathComponent, systemImage: "doc")
                    }
                }
            }
            Section("Peer approval") {
                Text("A transfer cannot begin until a discovered peer is authenticated and explicitly approved.")
                    .foregroundStyle(.secondary)
                Button("Request transfer") {}
                    .disabled(model.selectedFiles.isEmpty)
            }
        }
        .navigationTitle("Send")
    }
}

struct ReceiveView: View {
    var body: some View {
        List {
            Section {
                Text("SolidLink receives only after a nearby peer is authenticated and the request is approved on this device.")
                    .foregroundStyle(.secondary)
                Button("Start receiving") {}
                    .disabled(true)
            }
            Section("Current state") {
                Text("No approved incoming request")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Receive")
    }
}

struct PeerApprovalView: View {
    var body: some View {
        List {
            Section {
                Text("No peer is ready for approval. Discovery is provided by the local Bonjour transport and never by a cloud directory.")
                    .foregroundStyle(.secondary)
            }
            Section("Confirmation") {
                Text("Peer identity, transcript authentication, and user confirmation are required before keys become active.")
                    .foregroundStyle(.secondary)
                Button("Approve peer") {}
                    .disabled(true)
            }
        }
        .navigationTitle("Peer Approval")
    }
}

struct ActiveTransferView: View {
    var body: some View {
        List {
            Section {
                Text("No active transfer")
                    .font(.headline)
                Text("Progress is shown only for a real authenticated session with durable checkpoints. There is no sample progress to display.")
                    .foregroundStyle(.secondary)
            }
            Section {
                Button("Cancel transfer", role: .destructive) {}
                    .disabled(true)
            }
        }
        .navigationTitle("Active Transfer")
    }
}

struct HistoryView: View {
    var body: some View {
        List {
            Text("Completed transfers will appear after verified commit.")
                .foregroundStyle(.secondary)
            Text("No transfer history")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
        }
        .navigationTitle("History")
    }
}

struct StagedFilesView: View {
    var body: some View {
        List {
            Text("Received bytes remain private and unavailable for export until final size, chunk count, and digest verification complete.")
                .foregroundStyle(.secondary)
            Text("No verified staged files")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.secondary)
        }
        .navigationTitle("Staged Files")
    }
}

struct ExportView: View {
    var body: some View {
        List {
            Text("Export is enabled only for a verified, committed staged file selected by the user.")
                .foregroundStyle(.secondary)
            Button("Export verified file") {}
                .disabled(true)
        }
        .navigationTitle("Export")
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: SolidLinkAppModel

    var body: some View {
        Form {
            Section("Safety") {
                Toggle("Require peer approval", isOn: $model.peerApprovalRequired)
                Toggle("Allow advanced SAS confirmation", isOn: $model.advancedSasEnabled)
                Toggle("Local-only routing", isOn: .constant(true))
                    .disabled(true)
            }
            Section("Storage and privacy") {
                Text("Files are selected through the system document picker. SolidLink does not request broad photo-library or storage access.")
                    .foregroundStyle(.secondary)
                Text("No account, telemetry, advertising, or cloud relay is configured.")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
    }
}
