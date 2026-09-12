import UIKit
import SwiftUI
import ComposeApp
import AVFoundation

struct ContentView: View {
    var body: some View {
        NativeHomeView().tint(.blue)
    }
}

private struct NativeHomeView: View {
    @StateObject private var viewModel = NativeHomeViewModel()
    @State private var isShowingAddAccount = false
    @State private var isShowingAddOptions = false
    @State private var addMode = NativeAddAccountView.EntryMode.manual
    @State private var isShowingRecycleBin = false
    @State private var isShowingSettings = false
    @State private var selectedLabel: String?
    @State private var showArchived = false

    private var collectionTitle: String {
        showArchived ? "Archived" : selectedLabel ?? "All Accounts"
    }

    private var filteredTokens: [NativeTokenSummary] {
        (showArchived ? viewModel.archivedTokens : viewModel.tokens).filter { token in
            selectedLabel.map { selected in
                token.labels.contains { $0.localizedCaseInsensitiveCompare(selected) == .orderedSame }
            } ?? true
        }
    }

    var body: some View {
        NavigationView {
            Group {
                if viewModel.isLoading && viewModel.tokens.isEmpty && viewModel.archivedTokens.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let message = viewModel.errorMessage, viewModel.tokens.isEmpty && viewModel.archivedTokens.isEmpty {
                    NativeHomeErrorView(message: message) { viewModel.load() }
                } else if filteredTokens.isEmpty {
                    NativeEmptyState(
                        title: showArchived ? "No Archived Accounts" : "No Accounts",
                        symbol: showArchived ? "archivebox" : "key.fill"
                    ) {
                        if !showArchived {
                            Button("Add Account") { isShowingAddOptions = true }
                                .buttonStyle(.borderedProminent)
                        }
                    }
                    .safeAreaInset(edge: .bottom) {
                        Text("0 entries")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                            .padding(.bottom, 12)
                    }
                } else {
                    NativeTokenListView(
                        tokens: filteredTokens,
                        collectionTitle: collectionTitle,
                        isArchived: showArchived,
                        viewModel: viewModel
                    )
                }
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Home")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Menu {
                        Button {
                            selectedLabel = nil
                            showArchived = false
                        } label: {
                            Label("All Accounts", systemImage: !showArchived && selectedLabel == nil ? "checkmark" : "key")
                        }
                        if !viewModel.labels.isEmpty {
                            Section("Labels") {
                                ForEach(viewModel.labels, id: \.self) { label in
                                    Button {
                                        selectedLabel = label
                                        showArchived = false
                                    } label: {
                                        Label(label, systemImage: selectedLabel == label && !showArchived ? "checkmark" : "tag")
                                    }
                                }
                            }
                        }
                        Divider()
                        Button {
                            selectedLabel = nil
                            showArchived = true
                        } label: {
                            Label("Archived", systemImage: showArchived ? "checkmark" : "archivebox")
                        }
                    } label: {
                        Image(systemName: "line.3.horizontal.decrease.circle")
                    }
                    .accessibilityLabel("Filter accounts")
                    .accessibilityValue(collectionTitle)
                    .tint(.primary)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { isShowingAddOptions = true } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add account")
                    .tint(.primary)
                    .confirmationDialog("Add Account", isPresented: $isShowingAddOptions, titleVisibility: .visible) {
                        Button("Enter Manually") { openAddAccount(.manual) }
                        Button("Scan with Camera") { openAddAccount(.scan) }
                        Button("Import from URL") { openAddAccount(.url) }
                        Button("Cancel", role: .cancel) {}
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button { isShowingSettings = true } label: {
                            Label("Settings", systemImage: "gearshape")
                        }
                        Button { isShowingRecycleBin = true } label: {
                            Label("Recently Deleted", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                    }
                    .accessibilityLabel("More options")
                    .tint(.primary)
                }
            }
        }
        .navigationViewStyle(.stack)
        .onAppear { viewModel.load() }
        .onReceive(viewModel.refreshTimer) { _ in
            if !isShowingSettings && !isShowingAddAccount && !isShowingRecycleBin { viewModel.refreshCodes() }
        }
        .sheet(isPresented: $isShowingSettings, onDismiss: viewModel.load) {
            NativeSettingsView()
        }
        .sheet(isPresented: $isShowingAddAccount) {
            NativeAddAccountView(initialMode: addMode) {
                isShowingAddAccount = false
                viewModel.load()
            }
        }
        .sheet(isPresented: $isShowingRecycleBin, onDismiss: viewModel.load) {
            NativeRecycleBinView(viewModel: viewModel)
        }
        .alert("Unable to Update Accounts", isPresented: Binding(
            get: { viewModel.actionError != nil && !isShowingRecycleBin },
            set: { if !$0 { viewModel.actionError = nil } }
        )) {
            Button("OK", role: .cancel) { viewModel.actionError = nil }
        } message: {
            Text(viewModel.actionError ?? "")
        }
    }

    private func openAddAccount(_ mode: NativeAddAccountView.EntryMode) {
        addMode = mode
        isShowingAddAccount = true
    }
}

private struct NativeAddAccountView: View {
    enum EntryMode: String, CaseIterable, Identifiable {
        case manual = "Enter Key"
        case scan = "Scan Code"
        case url = "Import"

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
    @State private var showsCodeOptions = false

    init(initialMode: EntryMode = .manual, onComplete: @escaping () -> Void) {
        self.onComplete = onComplete
        _entryMode = State(initialValue: initialMode)
    }

    var body: some View {
        NavigationView {
            Form {
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
                        Label(errorMessage, systemImage: "exclamationmark.circle")
                            .foregroundColor(.red)
                    }
                }
            }
            .disabled(viewModel.isSaving)
            .navigationTitle(entryMode == .scan ? "Scan QR Code" : entryMode == .url ? "Import Account" : "New Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                    .disabled(viewModel.isSaving)
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button(viewModel.isSaving ? "Saving…" : "Save") {
                        save()
                    }
                    .disabled(entryMode == .scan || viewModel.isSaving)
                }
            }
        }
        .navigationViewStyle(.stack)
        .interactiveDismissDisabled(viewModel.isSaving)
    }

    private var manualForm: some View {
        Group {
            Section("Account") {
                NativeFormField(title: "Service", text: $issuer)
                    .textContentType(.organizationName)
                NativeFormField(title: "Account Name", text: $label)
                    .textContentType(.username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            Section("Setup Key") {
                TextField("Enter setup key", text: $secretKey)
                    .font(.system(.body, design: .monospaced))
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .privacySensitive()
            }
            Section("Organization") {
                NativeFormField(title: "Labels", text: $labelsText)
                    .autocorrectionDisabled()
            }

            Section {
                DisclosureGroup("Code Options", isExpanded: $showsCodeOptions) {
                Picker("Type", selection: $type) {
                    ForEach(OtpType.allCases) { type in
                        Text(type.rawValue).tag(type)
                    }
                }

                if type == .totp {
                    NativeFormField(title: "Duration in Seconds", text: $period)
                        .keyboardType(.numberPad)
                }

                if type == .hotp {
                    NativeFormField(title: "Counter", text: $counter)
                        .keyboardType(.numberPad)
                }
                }
            }
        }
    }

    private var urlForm: some View {
        Section("Authenticator URL") {
            TextEditor(text: $authUrl)
                .font(.system(.body, design: .monospaced))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .frame(minHeight: 96)
                .accessibilityLabel("Authenticator URL")
                .privacySensitive()
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
        authUrl = value
        viewModel.addFromUrl(value, onComplete: handleSaveResult)
    }

    private func handleSaveResult(_ result: NativeSaveResult) {
        Task { @MainActor in
            if result.success {
                onComplete()
            } else {
                errorMessage = result.message ?? "Unable to save this account."
                entryMode = entryMode == .scan ? .url : entryMode
            }
        }
    }
}

private struct NativeQrScannerSection: View {
    let onScanned: (String) -> Void

    var body: some View {
        Section("Scan QR Code") {
            NativeQrScannerView(onScanned: onScanned)
                .frame(minHeight: 320)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
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
    private let sessionQueue = DispatchQueue(label: "com.boxy.camera")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var didScan = false
    private var wantsRunning = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .secondarySystemBackground
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                if granted { self?.configureSession() }
                else { self?.showMessage("Camera access is off. Enable it in Settings to scan a QR code.") }
            }
        default:
            showMessage("Camera access is off. Enable it in Settings to scan a QR code.")
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        let shouldScan = !didScan
        sessionQueue.async { [self] in
            wantsRunning = shouldScan
            if wantsRunning && !session.inputs.isEmpty && !session.isRunning { session.startRunning() }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        sessionQueue.async { [self] in
            wantsRunning = false
            if session.isRunning { session.stopRunning() }
        }
    }

    private func configureSession() {
        sessionQueue.async { [self] in
            guard let device = AVCaptureDevice.default(for: .video),
                  let input = try? AVCaptureDeviceInput(device: device),
                  session.canAddInput(input) else {
                showMessage("Camera is unavailable on this device.")
                return
            }
            session.beginConfiguration()
            session.addInput(input)
            let output = AVCaptureMetadataOutput()
            guard session.canAddOutput(output) else {
                session.removeInput(input)
                session.commitConfiguration()
                showMessage("QR scanning is unavailable.")
                return
            }
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: .main)
            guard output.availableMetadataObjectTypes.contains(.qr) else {
                session.removeOutput(output)
                session.removeInput(input)
                session.commitConfiguration()
                showMessage("QR scanning is unavailable.")
                return
            }
            output.metadataObjectTypes = [.qr]
            session.commitConfiguration()
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                let layer = AVCaptureVideoPreviewLayer(session: self.session)
                layer.videoGravity = .resizeAspectFill
                layer.frame = self.view.bounds
                self.view.layer.addSublayer(layer)
                self.previewLayer = layer
            }
            if wantsRunning { session.startRunning() }
        }
    }

    private func showMessage(_ message: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            let label = UILabel()
            label.text = message
            label.font = .preferredFont(forTextStyle: .body)
            label.adjustsFontForContentSizeCategory = true
            label.textColor = .secondaryLabel
            label.textAlignment = .center
            label.numberOfLines = 0
            label.translatesAutoresizingMaskIntoConstraints = false
            self.view.addSubview(label)
            NSLayoutConstraint.activate([
                label.leadingAnchor.constraint(equalTo: self.view.leadingAnchor, constant: 24),
                label.trailingAnchor.constraint(equalTo: self.view.trailingAnchor, constant: -24),
                label.centerYAnchor.constraint(equalTo: self.view.centerYAnchor),
            ])
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !didScan,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue else { return }
        didScan = true
        sessionQueue.async { [self] in
            wantsRunning = false
            if session.isRunning { session.stopRunning() }
        }
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
        guard !isSaving else { return }
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
        guard !isSaving else { return }
        isSaving = true
        store.addFromUrl(url: url) { [weak self] result in
            Task { @MainActor in
                self?.isSaving = false
                onComplete(result)
            }
        }
    }

    func updateDetails(tokenID: String, issuer: String, label: String, labelsText: String,
                       onComplete: @escaping (NativeSaveResult) -> Void) {
        guard !isSaving else { return }
        isSaving = true
        store.updateDetails(tokenId: tokenID, issuer: issuer, label: label, labelsText: labelsText) { [weak self] result in
            Task { @MainActor in
                self?.isSaving = false
                onComplete(result)
            }
        }
    }
}

private struct NativeFormField: View {
    let title: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption).foregroundColor(.secondary)
            TextField(title, text: $text)
                .accessibilityLabel(title)
        }
        .padding(.vertical, 3)
    }
}

private struct NativeEditAccountView: View {
    let token: NativeTokenSummary
    let onComplete: () -> Void
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = NativeAddAccountViewModel()
    @State private var issuer: String
    @State private var label: String
    @State private var labelsText: String
    @State private var errorMessage: String?

    init(token: NativeTokenSummary, onComplete: @escaping () -> Void) {
        self.token = token
        self.onComplete = onComplete
        _issuer = State(initialValue: token.issuer)
        _label = State(initialValue: token.label)
        _labelsText = State(initialValue: token.labels.joined(separator: ", "))
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Account") {
                    NativeFormField(title: "Service", text: $issuer)
                    NativeFormField(title: "Account Name", text: $label)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section("Organization") {
                    NativeFormField(title: "Labels", text: $labelsText)
                        .autocorrectionDisabled()
                }
                .disabled(viewModel.isSaving)
                if let errorMessage = errorMessage {
                    Label(errorMessage, systemImage: "exclamationmark.circle").foregroundColor(.red)
                }
            }
            .navigationTitle("Edit Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(viewModel.isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(viewModel.isSaving ? "Saving…" : "Save") {
                        errorMessage = nil
                        viewModel.updateDetails(tokenID: token.id, issuer: issuer, label: label, labelsText: labelsText) { result in
                            if result.success { onComplete() }
                            else { errorMessage = result.message ?? "Unable to save this account." }
                        }
                    }
                    .disabled(viewModel.isSaving || issuer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .navigationViewStyle(.stack)
        .interactiveDismissDisabled(viewModel.isSaving)
    }
}

private struct NativeTokenListView: View {
    let tokens: [NativeTokenSummary]
    let collectionTitle: String
    let isArchived: Bool
    @ObservedObject var viewModel: NativeHomeViewModel
    @State private var editingToken: NativeTokenSummary?
    @State private var pendingDelete: NativeTokenSummary?
    @State private var expandedID: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var groups: [(letter: String, tokens: [NativeTokenSummary])] {
        Dictionary(grouping: tokens) { token in
            let name = token.issuer.isEmpty ? token.displayName : token.issuer
            return name.first.map { $0.isLetter ? String($0).uppercased() : "#" } ?? "#"
        }
        .map { letter, accounts in
            (letter: letter, tokens: accounts.sorted {
                $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending
            })
        }
        .sorted {
            if $0.letter == "#" { return false }
            if $1.letter == "#" { return true }
            return $0.letter.localizedStandardCompare($1.letter) == .orderedAscending
        }
    }

    var body: some View {
        List {
            ForEach(groups, id: \.letter) { group in
            Section {
                ForEach(group.tokens, id: \.id) { token in
                    VStack(spacing: 0) {
                        Button {
                            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.25)) {
                                expandedID = expandedID == token.id ? nil : token.id
                            }
                        } label: {
                            HStack(spacing: 12) {
                                NativeAccountRow(token: token)
                                Spacer(minLength: 0)
                                Image(systemName: "chevron.down")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(.secondary)
                                    .rotationEffect(.degrees(expandedID == token.id ? 180 : 0))
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityValue(expandedID == token.id ? "Expanded" : "Collapsed")
                        if expandedID == token.id {
                            NativeExpandedCode(token: token, viewModel: viewModel)
                                .transition(.opacity)
                        }
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button { pendingDelete = token } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        .tint(.red)
                        Button { viewModel.performAction(token, isArchived ? "unarchive" : "archive") } label: {
                            Label(isArchived ? "Unarchive" : "Archive", systemImage: "archivebox")
                        }
                        .tint(.orange)
                        Button { editingToken = token } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                        .tint(.blue)
                    }
                    .disabled(viewModel.isMutating || viewModel.isLoading)
                    .listRowSeparator(.hidden, edges: .top)
                    .listRowSeparator(token.id == group.tokens.last?.id ? .hidden : .visible, edges: .bottom)
                }
            } header: {
                Text(group.letter)
            }
            .listSectionSeparator(.hidden)
            }
            if collectionTitle != "All Accounts" {
                Text(collectionTitle)
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
            Text("\(tokens.count) entries")
                .font(.footnote)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        }
        .listStyle(.insetGrouped)
        .sheet(isPresented: Binding(
            get: { editingToken != nil },
            set: { if !$0 { editingToken = nil } }
        )) {
            if let token = editingToken {
                NativeEditAccountView(token: token) {
                    editingToken = nil
                    viewModel.load()
                }
            }
        }
        .confirmationDialog("Delete Account?", isPresented: Binding(
            get: { pendingDelete != nil },
            set: { if !$0 { pendingDelete = nil } }
        ), titleVisibility: .visible) {
            Button("Delete Account", role: .destructive) {
                if let token = pendingDelete { viewModel.performAction(token, "recycle") }
                pendingDelete = nil
            }
        } message: {
            Text("You can recover this account in Recently Deleted.")
        }
    }
}

private struct NativeExpandedCode: View {
    let token: NativeTokenSummary
    @ObservedObject var viewModel: NativeHomeViewModel
    @State private var copied = false

    var body: some View {
        HStack(spacing: 14) {
            if token.timeBased {
                NativeCountdown(period: Double(token.periodSeconds))
                    .frame(width: 24, height: 24)
            } else {
                Text("#\(token.hotpCounter)")
                    .font(.caption.monospacedDigit())
                    .foregroundColor(.secondary)
            }
            Text(token.otp.isEmpty ? "Unavailable" : token.otp.chunkedCode)
                .font(.system(.title, design: .monospaced).weight(.medium))
                .foregroundColor(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
                .onLongPressGesture(minimumDuration: 0.45, perform: copyCode)
                .accessibilityLabel("Verification code")
                .accessibilityValue(token.otp)
                .accessibilityAction(named: "Copy Code", copyCode)
            if !token.timeBased {
                Button { viewModel.incrementHotp(token) } label: {
                    Image(systemName: "arrow.clockwise").frame(width: 44, height: 44)
                }
                .buttonStyle(.borderless)
                .disabled(viewModel.isMutating || viewModel.isLoading || token.hotpCounter == Int64.max)
                .accessibilityLabel("Next code")
            }
            NavigationLink {
                NativeAccountDetailView(tokenID: token.id, viewModel: viewModel)
            } label: {
                Image(systemName: copied ? "checkmark.circle.fill" : "info.circle")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Account details")
        }
        .padding(.bottom, 4)
        .task(id: copied) {
            guard copied else { return }
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            if !Task.isCancelled { copied = false }
        }
    }

    private func copyCode() {
        guard !token.otp.isEmpty else { return }
        UIPasteboard.general.setItems(
            [[UIPasteboard.typeAutomatic: token.otp]],
            options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(30)]
        )
        UISelectionFeedbackGenerator().selectionChanged()
        copied = true
    }
}

private struct NativeAccountIcon: View {
    let token: NativeTokenSummary
    var size: CGFloat = 44

    var body: some View {
        Text(token.initials.isEmpty ? String(token.displayName.prefix(1)) : token.initials)
            .font(.system(size: size * 0.36, weight: .semibold, design: .rounded))
            .foregroundColor(Color(hex: token.tintColor))
            .frame(width: size, height: size)
            .background(Color(hex: token.tintColor).opacity(0.13))
            .clipShape(RoundedRectangle(cornerRadius: size * 0.25, style: .continuous))
            .accessibilityHidden(true)
    }
}

private struct NativeAccountRow: View {
    let token: NativeTokenSummary
    var showsCode = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        HStack(spacing: 12) {
            NativeAccountIcon(token: token)
            VStack(alignment: .leading, spacing: 4) {
                Text(token.issuer.isEmpty ? token.displayName : token.issuer)
                    .font(.body.weight(.medium))
                    .foregroundColor(.primary)
                    .lineLimit(2)
                if !token.label.isEmpty {
                    Text(token.label)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }
                if showsCode && dynamicTypeSize.isAccessibilitySize { code }
            }
            if showsCode && !dynamicTypeSize.isAccessibilitySize {
                Spacer(minLength: 8)
                code
            }
        }
        .padding(.vertical, 5)
    }

    private var code: some View {
        Text(token.otp.isEmpty ? "—" : token.otp.chunkedCode)
            .font(.system(.body, design: .monospaced).weight(.medium))
            .foregroundColor(.blue)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .accessibilityLabel("Verification code")
            .accessibilityValue(token.otp)
    }
}

private struct NativeAccountDetailView: View {
    let tokenID: String
    @ObservedObject var viewModel: NativeHomeViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var isEditing = false
    @State private var confirmDelete = false
    @State private var copied = false

    private var token: NativeTokenSummary? {
        (viewModel.tokens + viewModel.archivedTokens).first { $0.id == tokenID }
    }

    private var archived: Bool {
        viewModel.archivedTokens.contains { $0.id == tokenID }
    }

    var body: some View {
        Group {
            if let token = token {
                Form {
                    Section {
                        VStack(spacing: 12) {
                            NativeAccountIcon(token: token, size: 64)
                            Text(token.issuer).font(.title2.weight(.semibold))
                                .multilineTextAlignment(.center)
                            if !token.label.isEmpty {
                                Text(token.label).font(.subheadline).foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                    }
                    .listRowBackground(Color.clear)

                    Section("Verification Code") {
                        HStack(spacing: 16) {
                            Button {
                                UIPasteboard.general.setItems(
                                    [[UIPasteboard.typeAutomatic: token.otp]],
                                    options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(30)]
                                )
                                UISelectionFeedbackGenerator().selectionChanged()
                                copied = true
                            } label: {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(token.otp.isEmpty ? "Unavailable" : token.otp.chunkedCode)
                                        .font(.system(.largeTitle, design: .monospaced).weight(.medium))
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.45)
                                    Label(copied ? "Copied" : "Copy Code", systemImage: copied ? "checkmark" : "doc.on.doc")
                                        .font(.subheadline)
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .foregroundColor(.blue)
                            .disabled(token.otp.isEmpty)
                            if token.timeBased {
                                NativeCountdown(period: Double(token.periodSeconds))
                                    .frame(width: 28, height: 28)
                            } else {
                                Button { viewModel.incrementHotp(token) } label: {
                                    Image(systemName: "arrow.clockwise").frame(width: 44, height: 44)
                                }
                                .buttonStyle(.borderless)
                                .disabled(viewModel.isMutating || viewModel.isLoading || token.hotpCounter == Int64.max)
                                .accessibilityLabel("Next code")
                            }
                        }
                        .padding(.vertical, 12)
                    }

                    Section("Account Details") {
                        NativeDetailRow(title: "Issuer", value: token.issuer)
                        if !token.label.isEmpty { NativeDetailRow(title: "Account", value: token.label) }
                        NativeDetailRow(title: "Type", value: token.timeBased ? "Time-based" : "Counter-based")
                        if token.timeBased {
                            NativeDetailRow(title: "Code Duration", value: "\(token.periodSeconds) seconds")
                        } else {
                            NativeDetailRow(title: "Counter", value: "\(token.hotpCounter)")
                        }
                        NativeDetailRow(title: "Status", value: archived ? "Archived" : "Active")
                    }
                    if !token.labels.isEmpty {
                        Section("Labels") {
                            ForEach(token.labels, id: \.self) { label in
                                Label(label, systemImage: "tag").foregroundColor(.secondary)
                            }
                        }
                    }
                    Section {
                        Button { viewModel.performAction(token, archived ? "unarchive" : "archive") } label: {
                            Label(archived ? "Unarchive Account" : "Archive Account", systemImage: "archivebox")
                        }
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Delete Account", systemImage: "trash")
                        }
                    }
                    .disabled(viewModel.isMutating || viewModel.isLoading)
                }
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Edit") { isEditing = true }
                            .disabled(viewModel.isMutating || viewModel.isLoading)
                    }
                }
                .sheet(isPresented: $isEditing) {
                    NativeEditAccountView(token: token) {
                        isEditing = false
                        viewModel.load()
                    }
                }
                .confirmationDialog("Delete Account?", isPresented: $confirmDelete, titleVisibility: .visible) {
                    Button("Delete Account", role: .destructive) { viewModel.performAction(token, "recycle") }
                } message: {
                    Text("You can recover this account in Recently Deleted.")
                }
            } else {
                NativeEmptyState(title: "Account Unavailable", symbol: "key.slash") {
                    Button("Back to Home") { dismiss() }
                }
            }
        }
        .navigationTitle("Account")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: copied) {
            guard copied else { return }
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            if !Task.isCancelled { copied = false }
        }
    }
}

private struct NativeDetailRow: View {
    let title: String
    let value: String
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                Text(value).foregroundColor(.secondary)
            }
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 20) {
                Text(title)
                Spacer(minLength: 8)
                Text(value).foregroundColor(.secondary).multilineTextAlignment(.trailing)
            }
        }
    }
}

private struct NativeCountdown: View {
    let period: Double
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: scenePhase != .active)) { context in
            let duration = max(1, period)
            let remaining = duration - context.date.timeIntervalSince1970.truncatingRemainder(dividingBy: duration)
            ZStack {
                Circle().stroke(Color.secondary.opacity(0.15), lineWidth: 3)
                Circle()
                    .trim(from: 0, to: remaining / duration)
                    .stroke(remaining / duration < 0.2 ? Color.orange : Color.blue,
                            style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            .accessibilityLabel("Time remaining")
            .accessibilityValue("\(Int(ceil(remaining))) seconds")
            .transaction { $0.animation = nil }
        }
    }
}

private struct NativeRecycleBinView: View {
    @ObservedObject var viewModel: NativeHomeViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedIDs: Set<String> = []
    @State private var pendingDeleteIDs: Set<String> = []

    private var availableIDs: Set<String> {
        Set(viewModel.recycledTokens.map(\.id))
    }

    private var busy: Bool {
        viewModel.isMutating || viewModel.isLoading || viewModel.isLoadingRecycleBin
    }

    var body: some View {
        NavigationView {
            Group {
                if viewModel.isLoadingRecycleBin && viewModel.recycledTokens.isEmpty {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.recycledTokens.isEmpty && viewModel.actionError == nil {
                    NativeEmptyState(title: "No Recently Deleted Accounts", symbol: "trash") {
                        EmptyView()
                    }
                } else {
                    List {
                        Section {
                            ForEach(viewModel.recycledTokens, id: \.id) { token in
                                Button {
                                    if !selectedIDs.insert(token.id).inserted { selectedIDs.remove(token.id) }
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: selectedIDs.contains(token.id) ? "checkmark.square.fill" : "square")
                                            .font(.title3)
                                            .foregroundColor(selectedIDs.contains(token.id) ? .blue : .secondary)
                                            .frame(width: 28)
                                            .accessibilityHidden(true)
                                        NativeAccountRow(token: token)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(token.displayName)
                                .accessibilityValue(selectedIDs.contains(token.id) ? "Selected" : "Not selected")
                                .accessibilityAddTraits(selectedIDs.contains(token.id) ? .isSelected : [])
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button { pendingDeleteIDs = [token.id] } label: {
                                        Label("Delete", systemImage: "trash")
                                    }.tint(.red)
                                    Button { recover([token.id]) } label: {
                                        Label("Recover", systemImage: "arrow.uturn.backward")
                                    }.tint(.blue)
                                }
                                .disabled(busy)
                            }
                        } header: {
                            HStack {
                                Text("\(selectedIDs.count) selected")
                                Spacer()
                                Button(selectedIDs == availableIDs ? "Deselect All" : "Select All") {
                                    selectedIDs = selectedIDs == availableIDs ? [] : availableIDs
                                }
                                .disabled(busy || availableIDs.isEmpty)
                            }
                        }
                        if let message = viewModel.actionError {
                            Section {
                                Label(message, systemImage: "exclamationmark.circle").foregroundColor(.red)
                                Button("Try Again") { viewModel.loadRecycleBin() }
                                    .disabled(busy)
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Recently Deleted")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.left")
                    }
                    .tint(.primary)
                    .accessibilityLabel("Back")
                    .disabled(viewModel.isMutating)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Delete", role: .destructive) { pendingDeleteIDs = selectedIDs }
                        .tint(.red)
                        .disabled(selectedIDs.isEmpty || busy)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Button { recover(selectedIDs) } label: {
                    Text("Recover")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .disabled(selectedIDs.isEmpty || busy)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(.regularMaterial)
            }
        }
        .navigationViewStyle(.stack)
        .interactiveDismissDisabled(viewModel.isMutating)
        .onAppear { viewModel.loadRecycleBin() }
        .onChange(of: availableIDs) { ids in selectedIDs.formIntersection(ids) }
        .confirmationDialog("Permanently Delete \(pendingDeleteIDs.count) Accounts?", isPresented: Binding(
            get: { !pendingDeleteIDs.isEmpty }, set: { if !$0 { pendingDeleteIDs = [] } }
        ), titleVisibility: .visible) {
            Button("Delete Permanently", role: .destructive) {
                let ids = pendingDeleteIDs
                pendingDeleteIDs = []
                viewModel.performRecycledAction(ids, restore: false) {
                    selectedIDs.subtract(ids)
                }
            }
        } message: {
            Text("This cannot be undone.")
        }
    }

    private func recover(_ ids: Set<String>) {
        viewModel.performRecycledAction(ids, restore: true) {
            selectedIDs.subtract(ids)
        }
    }
}

private struct NativeEmptyState<Actions: View>: View {
    let title: String
    let symbol: String
    @ViewBuilder let actions: () -> Actions

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: symbol)
                .font(.system(size: 48, weight: .light))
                .foregroundColor(.secondary)
                .accessibilityHidden(true)
            Text(title).font(.title2.weight(.semibold)).multilineTextAlignment(.center)
            actions()
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct NativeHomeErrorView: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        NativeEmptyState(title: "Unable to Load Codes", symbol: "exclamationmark.triangle") {
            Text(message).font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center)
            Button("Try Again", action: onRetry).buttonStyle(.borderedProminent)
        }
    }
}

@MainActor
private final class NativeHomeViewModel: ObservableObject {
    @Published var tokens: [NativeTokenSummary] = []
    @Published var archivedTokens: [NativeTokenSummary] = []
    @Published var recycledTokens: [NativeTokenSummary] = []
    @Published var isLoadingRecycleBin = false
    @Published var labels: [String] = []
    @Published var isMutating = false
    @Published var actionError: String?
    @Published var showLabelCounts = true
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
        showLabelCounts = NativeSettingsStore().snapshot().showLabelCounts
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
        guard !isMutating, !isLoading, !tokens.isEmpty || !archivedTokens.isEmpty else { return }
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
        guard !isMutating, !isLoading else { return }
        isMutating = true
        store.incrementHotp(tokenId: token.id, counter: token.hotpCounter) { [weak self] didUpdate in
            Task { @MainActor in
                self?.isMutating = false
                if didUpdate.boolValue {
                    self?.load()
                } else {
                    self?.actionError = "Unable to advance the code. Please try again."
                }
            }
        }
    }

    func loadRecycleBin() {
        isLoadingRecycleBin = true
        actionError = nil
        store.loadRecycleBin { [weak self] tokens in
            Task { @MainActor in
                self?.recycledTokens = tokens
                self?.isLoadingRecycleBin = false
            }
        } onError: { [weak self] message in
            Task { @MainActor in
                self?.isLoadingRecycleBin = false
                self?.actionError = message
            }
        }
    }

    func performAction(_ token: NativeTokenSummary, _ action: String) {
        guard !isMutating, !isLoading else { return }
        isMutating = true
        actionError = nil
        store.performAction(tokenId: token.id, action: action) { [weak self] message in
            Task { @MainActor in
                self?.isMutating = false
                if let message = message {
                    self?.actionError = message
                } else {
                    self?.load()
                    self?.loadRecycleBin()
                }
            }
        }
    }

    func performRecycledAction(_ ids: Set<String>, restore: Bool, onSuccess: @escaping () -> Void) {
        guard !ids.isEmpty, !isMutating, !isLoading, !isLoadingRecycleBin else { return }
        isMutating = true
        actionError = nil
        store.performRecycledAction(tokenIds: Array(ids), restore: restore) { [weak self] message in
            Task { @MainActor in
                guard let self = self else { return }
                self.isMutating = false
                if let message = message {
                    self.actionError = message
                } else {
                    onSuccess()
                    self.load()
                    self.loadRecycleBin()
                }
            }
        }
    }

    private func apply(_ snapshot: NativeHomeSnapshot, showLoading: Bool = true) {
        tokens = snapshot.activeTokens
        archivedTokens = snapshot.archivedTokens
        labels = snapshot.labels
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

private struct NativeSettingsLabel: View {
    let title: String
    let symbol: String
    let color: Color

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.white)
                .frame(width: 28, height: 28)
                .background(color)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                .accessibilityHidden(true)
            Text(title)
        }
    }
}

private struct NativeSettingsView: View {
    @StateObject private var viewModel = NativeSettingsViewModel()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            Form {
                Section {
                    HStack(spacing: 16) {
                        Image(systemName: "key.fill")
                            .font(.system(size: 28, weight: .medium))
                            .foregroundColor(.blue)
                            .frame(width: 56, height: 56)
                            .background(Color.blue.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Boxy").font(.title2.weight(.semibold))
                            Text("Authenticator").font(.subheadline).foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 6)
                }

                Section("Preferences") {
                    Toggle(isOn: $viewModel.showLabelCounts) {
                        NativeSettingsLabel(title: "Label Counts", symbol: "tag.fill", color: .blue)
                    }
                    Toggle(isOn: $viewModel.lockscreenPinPad) {
                        NativeSettingsLabel(title: "PIN Keypad", symbol: "number", color: .indigo)
                    }
                    Toggle(isOn: Binding(
                        get: { !viewModel.disableBackupAlerts },
                        set: { viewModel.disableBackupAlerts = !$0 }
                    )) {
                        NativeSettingsLabel(title: "Backup Reminders", symbol: "bell.fill", color: .orange)
                    }
                }

                Section("Privacy & Security") {
                    Toggle(isOn: $viewModel.blockScreenshots) {
                        NativeSettingsLabel(title: "Block Screenshots", symbol: "rectangle.slash", color: .gray)
                    }
                    Toggle(isOn: $viewModel.lockSensitiveFields) {
                        NativeSettingsLabel(title: "Protect Sensitive Fields", symbol: "eye.slash.fill", color: .purple)
                    }
                    HStack {
                        NativeSettingsLabel(title: "App Lock", symbol: "lock.fill", color: .green)
                        Spacer()
                        Text(viewModel.appLockEnabled ? "On" : "Off").foregroundColor(.secondary)
                    }
                    HStack {
                        NativeSettingsLabel(title: "Biometric Unlock", symbol: "faceid", color: .teal)
                        Spacer()
                        Text(viewModel.biometricUnlockEnabled ? "On" : "Off").foregroundColor(.secondary)
                    }
                }

                Section("Accounts") {
                    NavigationLink {
                        NativeTransferView()
                    } label: {
                        NativeSettingsLabel(title: "Import Account", symbol: "square.and.arrow.down", color: .blue)
                    }
                }

                Section {
                    NativeDetailRow(title: "Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .navigationViewStyle(.stack)
        .onAppear { viewModel.load() }
    }
}

private struct NativeTransferView: View {
    @StateObject private var viewModel = NativeAddAccountViewModel()
    @State private var authUrl = ""
    @State private var message: String?
    @State private var didImport = false

    var body: some View {
        Form {
            Section("Authenticator URL") {
                TextEditor(text: $authUrl)
                    .font(.system(.body, design: .monospaced))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .frame(minHeight: 160)
                    .accessibilityLabel("Authenticator URL")
                    .privacySensitive()
                    .disabled(viewModel.isSaving)
            }

            if let message = message {
                Section {
                    Label(message, systemImage: didImport ? "checkmark.circle.fill" : "exclamationmark.circle")
                        .foregroundColor(didImport ? .green : .red)
                }
            }
        }
        .navigationTitle("Import Account")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(viewModel.isSaving ? "Importing…" : "Import") {
                    message = nil
                    viewModel.addFromUrl(authUrl) { result in
                        didImport = result.success
                        message = result.success ? "Account Imported" : result.message ?? "Unable to import this account."
                        if result.success { authUrl = "" }
                    }
                }
                .disabled(authUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isSaving)
            }
        }
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
