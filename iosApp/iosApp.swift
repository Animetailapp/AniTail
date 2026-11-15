/*
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

import AVFoundation
import Combine
import Foundation
import Shared
import SwiftUI

@main
struct AniTailApp: App {
    init() {
        IosCrashRecovery.shared.install()
        NSSetUncaughtExceptionHandler { exception in
            IosCrashRecovery.shared.recordNative(message: "\(exception.name.rawValue): \(exception.reason ?? "")\n\(exception.callStackSymbols.joined(separator: "\n"))")
        }
        configureAppAudioSession()
        if UserDefaults.standard.object(forKey: "iosOnboardingComplete") == nil {
            let existingDatabase = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
                .first?.appendingPathComponent("metrolist/metrolist.db")
            let isUpgrade = existingDatabase.map { FileManager.default.fileExists(atPath: $0.path) } ?? false
            UserDefaults.standard.set(isUpgrade, forKey: "iosOnboardingComplete")
        }
        if !IosCrashRecovery.shared.hasPendingReport {
            MetrolistPlatform.shared.initialize()
        }
    }

    var body: some Scene {
        WindowGroup {
            AppHostView()
        }
    }

    private func configureAppAudioSession() {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.playback, mode: .default)
        } catch {
            print("Audio session configuration error: \(error.localizedDescription)")
        }
    }
}

private struct AppHostView: View {
    @State private var statusBarHidden = false

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .statusBarHidden(statusBarHidden)
            .onReceive(NotificationCenter.default.publisher(for: Notification.Name("com.anitail.statusBarVisibilityChanged"))) { notification in
                statusBarHidden = (notification.object as? NSNumber)?.boolValue ?? false
            }
            .onOpenURL { url in
                MainViewControllerKt.handleOpenUrl(url: url.absoluteString)
            }
            .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                guard let url = activity.webpageURL else { return }
                MainViewControllerKt.handleOpenUrl(url: url.absoluteString)
            }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
