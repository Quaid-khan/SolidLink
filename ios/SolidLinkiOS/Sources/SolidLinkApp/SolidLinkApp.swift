import SwiftUI

@main
struct SolidLinkApp: App {
    @StateObject private var model = SolidLinkAppModel()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(model)
        }
    }
}
