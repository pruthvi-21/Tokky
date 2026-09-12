import UIKit
import SwiftUI
import ComposeApp
import AVFoundation

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

            NativeSettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
        }
    }
}

private struct NativeHomeView: View {
    @StateObject private var viewModel = NativeHomeViewModel()
    @State private var isShowingAddAccount = false

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

                    Button {
                        isShowingAddAccount = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
        }
        .onAppear {
            viewModel.load()
        }
        .onReceive(viewModel.refreshTimer) { _ in
            viewModel.refreshCodes()
        }
        .sheet(isPresented: $isShowingAddAccount) {
            NativeAddAccountView {
                isShowingAddAccount = false
                viewModel.load()
            }
        }
    }
}

private struct NativeAddAccountView: View {
    enum EntryMode: String, CaseIterable, Identifiable {
        case manual = "Manual"
        case scan = "Scan"
        case url = "URL"

        var id: String { rawValue }
    }

    enum OtpType: String, CaseIterable, Identifiable {
        case totp = "TOTP"
        case hotp = "HOTP"
        case steam = "STEAM"

        var id: String { rawValue }
    }

    let onComplete: () -> Void

    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = NativeAddAccountViewModel()
    @State private var entryMode = EntryMode.manual
    @State private var type = OtpType.totp
    @State private var issuer = ""
    @State private var label = ""
    @State private var secretKey = ""
    @State private var period = "30"
    @State private var counter = "0"
    @State private var labelsText = ""
    @State private var authUrl = ""
    @State private var errorMessage: String?

    var body: some View {
        NavigationView {
            Form {
                Section {
                    Picker("Entry", selection: $entryMode) {
                        ForEach(EntryMode.allCases) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                switch entryMode {
                case .manual:
                    manualForm
                case .scan:
                    NativeQrScannerSection { value in
                        saveScannedUrl(value)
                    }
                case .url:
                    urlForm
                }

                if let errorMessage = errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Add Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        save()
                    }
                    .disabled(entryMode == .scan || viewModel.isSaving)
                }
            }
        }
    }

    private var manualForm: some View {
        Group {
            Section("Account") {
                TextField("Issuer", text: $issuer)
                    .textContentType(.organizationName)

                TextField("Label", text: $label)
                    .textContentType(.username)

                TextField("Secret", text: $secretKey)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()

                TextField("Labels", text: $labelsText)
                    .autocorrectionDisabled()
            }

            Section("Code") {
                Picker("Type", selection: $type) {
                    ForEach(OtpType.allCases) { type in
                        Text(type.rawValue).tag(type)
                    }
                }

                if type == .totp {
                    TextField("Period", text: $period)
                        .keyboardType(.numberPad)
                }

                if type == .hotp {
                    TextField("Counter", text: $counter)
                        .keyboardType(.numberPad)
                }
            }
        }
    }

    private var urlForm: some View {
        Section("Authenticator URL") {
            TextEditor(text: $authUrl)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .frame(minHeight: 96)
        }
    }

    private func save() {
        errorMessage = nil
        switch entryMode {
        case .manual:
            viewModel.addManual(
                issuer: issuer,
                label: label,
                secretKey: secretKey,
                typeName: type.rawValue,
                period: period,
                counter: counter,
                labelsText: labelsText,
                onComplete: handleSaveResult
            )
        case .url:
            viewModel.addFromUrl(authUrl, onComplete: handleSaveResult)
        case .scan:
            break
        }
    }

    private func saveScannedUrl(_ value: String) {
        viewModel.addFromUrl(value, onComplete: handleSaveResult)
    }

    private func handleSaveResult(_ result: NativeSaveResult) {
        Task { @MainActor in
            if result.success {
                onComplete()
            } else {
                errorMessage = result.message ?? "Unable to save this account."
                entryMode = entryMode == .scan ? .url : entryMode
                if entryMode == .url && authUrl.isEmpty {
                    authUrl = ""
                }
            }
        }
    }
}

private struct NativeQrScannerSection: View {
    let onScanned: (String) -> Void

    var body: some View {
        Section {
            NativeQrScannerView(onScanned: onScanned)
                .frame(minHeight: 320)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

private struct NativeQrScannerView: UIViewControllerRepresentable {
    let onScanned: (String) -> Void

    func makeUIViewController(context: Context) -> NativeQrScannerViewController {
        let controller = NativeQrScannerViewController()
        controller.onScanned = onScanned
        return controller
    }

    func updateUIViewController(_ uiViewController: NativeQrScannerViewController, context: Context) {}
}

private final class NativeQrScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onScanned: ((String) -> Void)?

    private let session = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var didScan = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .secondarySystemBackground
        configureSession()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if !session.isRunning {
            DispatchQueue.global(qos: .userInitiated).async {
                self.session.startRunning()
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if session.isRunning {
            session.stopRunning()
        }
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input)
        else {
            showMessage("Camera is unavailable.")
            return
        }

        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            showMessage("QR scanning is unavailable.")
            return
        }

        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        view.layer.addSublayer(layer)
        previewLayer = layer
    }

    private func showMessage(_ message: String) {
        let label = UILabel()
        label.text = message
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !didScan,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue
        else { return }

        didScan = true
        session.stopRunning()
        onScanned?(value)
    }
}

@MainActor
private final class NativeAddAccountViewModel: ObservableObject {
    @Published var isSaving = false
    private let store = NativeTokenSetupStore()

    deinit {
        store.dispose()
    }

    func addManual(
        issuer: String,
        label: String,
        secretKey: String,
        typeName: String,
        period: String,
        counter: String,
        labelsText: String,
        onComplete: @escaping (NativeSaveResult) -> Void
    ) {
        isSaving = true
        store.addManual(
            issuer: issuer,
            label: label,
            secretKey: secretKey,
            typeName: typeName,
            period: period,
            counter: counter,
            labelsText: labelsText
        ) { [weak self] result in
            Task { @MainActor in
                self?.isSaving = false
                onComplete(result)
            }
        }
    }

    func addFromUrl(_ url: String, onComplete: @escaping (NativeSaveResult) -> Void) {
        isSaving = true
        store.addFromUrl(url: url) { [weak self] result in
            Task { @MainActor in
                self?.isSaving = false
                onComplete(result)
            }
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

private struct NativeSettingsView: View {
    @StateObject private var viewModel = NativeSettingsViewModel()

    var body: some View {
        NavigationView {
            Form {
                Section("General") {
                    Toggle("Show label counts", isOn: $viewModel.showLabelCounts)
                    Toggle("Use PIN pad", isOn: $viewModel.lockscreenPinPad)
                    Toggle("Disable backup alerts", isOn: $viewModel.disableBackupAlerts)
                }

                Section("Security") {
                    Toggle("Block screenshots", isOn: $viewModel.blockScreenshots)
                    Toggle("Lock sensitive fields", isOn: $viewModel.lockSensitiveFields)

                    HStack {
                        Label("App Lock", systemImage: "lock.fill")
                        Spacer()
                        Text(viewModel.appLockEnabled ? "On" : "Off")
                            .foregroundColor(.secondary)
                    }

                    HStack {
                        Label("Biometrics", systemImage: "faceid")
                        Spacer()
                        Text(viewModel.biometricUnlockEnabled ? "On" : "Off")
                            .foregroundColor(.secondary)
                    }
                }

                Section("Transfer") {
                    NavigationLink {
                        NativeTransferView()
                    } label: {
                        Label("Import authenticator URL", systemImage: "square.and.arrow.down")
                    }

                    HStack {
                        Label("Encrypted export", systemImage: "square.and.arrow.up")
                        Spacer()
                        Text("Legacy")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
        }
        .onAppear {
            viewModel.load()
        }
    }
}

private struct NativeTransferView: View {
    @StateObject private var viewModel = NativeAddAccountViewModel()
    @State private var authUrl = ""
    @State private var message: String?

    var body: some View {
        Form {
            Section("Authenticator URL") {
                TextEditor(text: $authUrl)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .frame(minHeight: 120)

                Button("Import") {
                    viewModel.addFromUrl(authUrl) { result in
                        Task { @MainActor in
                            message = result.success
                                ? "Account imported."
                                : (result.message ?? "Unable to import this account.")
                            if result.success {
                                authUrl = ""
                            }
                        }
                    }
                }
                .disabled(authUrl.isEmpty || viewModel.isSaving)
            }

            if let message = message {
                Section {
                    Text(message)
                        .foregroundColor(message == "Account imported." ? .secondary : .red)
                }
            }
        }
        .navigationTitle("Import")
    }
}

@MainActor
private final class NativeSettingsViewModel: ObservableObject {
    @Published var showLabelCounts = false {
        didSet { guard didLoad else { return }; store.setShowLabelCounts(enabled: showLabelCounts) }
    }
    @Published var lockscreenPinPad = false {
        didSet { guard didLoad else { return }; store.setLockscreenPinPad(enabled: lockscreenPinPad) }
    }
    @Published var disableBackupAlerts = false {
        didSet { guard didLoad else { return }; store.setDisableBackupAlerts(enabled: disableBackupAlerts) }
    }
    @Published var blockScreenshots = true {
        didSet { guard didLoad else { return }; store.setBlockScreenshots(enabled: blockScreenshots) }
    }
    @Published var lockSensitiveFields = true {
        didSet { guard didLoad else { return }; store.setLockSensitiveFields(enabled: lockSensitiveFields) }
    }
    @Published private(set) var appLockEnabled = false
    @Published private(set) var biometricUnlockEnabled = false

    private let store = NativeSettingsStore()
    private var didLoad = false

    func load() {
        let snapshot = store.snapshot()
        didLoad = false
        showLabelCounts = snapshot.showLabelCounts
        lockscreenPinPad = snapshot.lockscreenPinPad
        disableBackupAlerts = snapshot.disableBackupAlerts
        blockScreenshots = snapshot.blockScreenshots
        lockSensitiveFields = snapshot.lockSensitiveFields
        appLockEnabled = snapshot.appLockEnabled
        biometricUnlockEnabled = snapshot.biometricUnlockEnabled
        didLoad = true
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
