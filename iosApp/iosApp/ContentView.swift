import UIKit
import SwiftUI
import ComposeApp

struct LegacyComposeAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        TabView {
            LegacyComposeAppView()
                .ignoresSafeArea(edges: .top)
                .tabItem {
                    Label("Accounts", systemImage: "key.fill")
                }

            NavigationView {
                NativeMigrationView()
                    .navigationTitle("Boxy")
            }
            .tabItem {
                Label("Native", systemImage: "iphone")
            }
        }
    }
}

private struct NativeMigrationView: View {
    private let rows = [
        NativeMigrationRow(title: "Shared Kotlin Core", value: "Ready", icon: "checkmark.seal.fill"),
        NativeMigrationRow(title: "SwiftUI Shell", value: "Ready", icon: "iphone"),
        NativeMigrationRow(title: "Compose Fallback", value: "Available", icon: "square.stack.3d.up.fill"),
    ]

    var body: some View {
        List {
            Section {
                ForEach(rows) { row in
                    HStack(spacing: 12) {
                        Image(systemName: row.icon)
                            .foregroundColor(.accentColor)
                            .frame(width: 24)

                        Text(row.title)

                        Spacer()

                        Text(row.value)
                            .foregroundStyle(.secondary)
                    }
                }
            } header: {
                Text("Phase 1")
            }
        }
    }
}

private struct NativeMigrationRow: Identifiable {
    let id = UUID()
    let title: String
    let value: String
    let icon: String
}
