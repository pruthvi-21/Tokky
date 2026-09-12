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
            NativeHomeView()
                .tabItem {
                    Label("Accounts", systemImage: "key.fill")
                }

            LegacyComposeAppView()
                .ignoresSafeArea(edges: .top)
                .tabItem {
                    Label("Legacy", systemImage: "square.stack.3d.up.fill")
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

private struct NativeHomeView: View {
    @StateObject private var viewModel = NativeHomeViewModel()

    var body: some View {
        NavigationView {
            Group {
                if viewModel.isLoading && viewModel.tokens.isEmpty {
                    ProgressView()
                } else if let message = viewModel.errorMessage, viewModel.tokens.isEmpty {
                    NativeHomeErrorView(message: message) {
                        viewModel.load()
                    }
                } else if viewModel.tokens.isEmpty {
                    NativeHomeEmptyView()
                } else {
                    NativeTokenListView(
                        tokens: viewModel.tokens,
                        onRefreshHotp: viewModel.incrementHotp
                    )
                }
            }
            .navigationTitle("Accounts")
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button {
                        viewModel.load()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }

                    Button {} label: {
                        Image(systemName: "plus")
                    }
                    .disabled(true)
                }
            }
        }
        .onAppear {
            viewModel.load()
        }
        .onReceive(viewModel.refreshTimer) { _ in
            viewModel.refreshCodes()
        }
    }
}

private struct NativeTokenListView: View {
    let tokens: [NativeTokenSummary]
    let onRefreshHotp: (NativeTokenSummary) -> Void

    var groupedTokens: [(String, [NativeTokenSummary])] {
        Dictionary(grouping: tokens) { token in
            String(token.displayName.prefix(1)).uppercased()
        }
        .map { ($0.key, $0.value.sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }) }
        .sorted { $0.0 < $1.0 }
    }

    var body: some View {
        List {
            ForEach(groupedTokens, id: \.0) { section, tokens in
                Section(section) {
                    ForEach(tokens, id: \.id) { token in
                        NativeTokenRow(token: token) {
                            onRefreshHotp(token)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

private struct NativeTokenRow: View {
    let token: NativeTokenSummary
    let onRefreshHotp: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color(hex: token.tintColor))

                Text(token.initials)
                    .font(.caption.weight(.bold))
                    .foregroundColor(.white)
            }
            .frame(width: 42, height: 42)

            VStack(alignment: .leading, spacing: 4) {
                Text(token.issuer)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)

                if !token.label.isEmpty {
                    Text(token.label)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }

                if !token.labels.isEmpty {
                    Text(token.labels.joined(separator: ", "))
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 12)

            VStack(alignment: .trailing, spacing: 6) {
                Text(token.otp.chunkedCode)
                    .font(.system(.title3, design: .monospaced).weight(.semibold))
                    .textSelection(.enabled)

                if token.timeBased {
                    ProgressView(value: token.progress)
                        .frame(width: 72)

                    Text("\(token.remainingSeconds)s")
                        .font(.caption2.monospacedDigit())
                        .foregroundColor(.secondary)
                } else {
                    Button {
                        onRefreshHotp()
                    } label: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                    }
                    .buttonStyle(.borderless)

                    Text("#\(token.hotpCounter)")
                        .font(.caption2.monospacedDigit())
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

private struct NativeHomeEmptyView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "key.slash")
                .font(.system(size: 42))
                .foregroundColor(.secondary)

            Text("No active accounts")
                .font(.headline)
        }
    }
}

private struct NativeHomeErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 42))
                .foregroundColor(.orange)

            Text(message)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button("Retry", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
    }
}

@MainActor
private final class NativeHomeViewModel: ObservableObject {
    @Published var tokens: [NativeTokenSummary] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    let refreshTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    private let store = NativeHomeStore()

    deinit {
        store.dispose()
    }

    func load() {
        isLoading = true
        errorMessage = nil
        store.load { [weak self] snapshot in
            Task { @MainActor in
                self?.apply(snapshot)
            }
        } onError: { [weak self] message in
            Task { @MainActor in
                self?.isLoading = false
                self?.errorMessage = message
            }
        }
    }

    func refreshCodes() {
        guard !tokens.isEmpty else { return }
        store.refreshCodes { [weak self] snapshot in
            Task { @MainActor in
                self?.apply(snapshot, showLoading: false)
            }
        } onError: { [weak self] message in
            Task { @MainActor in
                self?.errorMessage = message
            }
        }
    }

    func incrementHotp(_ token: NativeTokenSummary) {
        store.incrementHotp(tokenId: token.id, counter: token.hotpCounter) { [weak self] didUpdate in
            guard didUpdate else { return }
            Task { @MainActor in
                self?.load()
            }
        }
    }

    private func apply(_ snapshot: NativeHomeSnapshot, showLoading: Bool = true) {
        tokens = snapshot.activeTokens as? [NativeTokenSummary] ?? []
        if showLoading {
            isLoading = false
        }
        errorMessage = nil
    }
}

private extension String {
    var chunkedCode: String {
        guard count > 3 else { return self }
        let midpoint = index(startIndex, offsetBy: count / 2)
        return "\(self[..<midpoint]) \(self[midpoint...])"
    }
}

private extension Color {
    init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var value: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&value)

        let red: Double
        let green: Double
        let blue: Double

        switch cleaned.count {
        case 6:
            red = Double((value & 0xFF0000) >> 16) / 255.0
            green = Double((value & 0x00FF00) >> 8) / 255.0
            blue = Double(value & 0x0000FF) / 255.0
        default:
            red = 0.20
            green = 0.47
            blue = 0.96
        }

        self.init(red: red, green: green, blue: blue)
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
