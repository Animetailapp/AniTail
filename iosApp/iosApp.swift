/*
 * AniTail Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

import AVFoundation
import Combine
import Foundation
import MediaPlayer
import Shared
import SwiftUI
import UIKit

@main
struct AniTailApp: App {
    init() {
        IosCrashRecovery.shared.install()
        NSSetUncaughtExceptionHandler { exception in
            IosCrashRecovery.shared.recordNative(message: "\(exception.name.rawValue): \(exception.reason ?? "")\n\(exception.callStackSymbols.joined(separator: "\n"))")
        }
        configureAppAudioSession()
        IosNowPlayingAnimatedArtworkBridge.shared.register(provider: IosNowPlayingAnimatedArtworkHost())
        if UserDefaults.standard.object(forKey: "iosOnboardingComplete") == nil {
            let existingDatabase = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)
                .first?.appendingPathComponent("anitail/anitail.db")
            let isUpgrade = existingDatabase.map { FileManager.default.fileExists(atPath: $0.path) } ?? false
            UserDefaults.standard.set(isUpgrade, forKey: "iosOnboardingComplete")
        }
        if !IosCrashRecovery.shared.hasPendingReport {
            AnitailPlatform.shared.initialize()
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

private final class IosNowPlayingAnimatedArtworkHost: NSObject, IosNowPlayingAnimatedArtworkProvider {
    var isSupported: Bool {
        if #available(iOS 26.0, *) {
            return !MPNowPlayingInfoCenter.supportedAnimatedArtworkKeys.isEmpty
        }
        return false
    }

    func createEntries(spec: IosNowPlayingAnimatedArtworkSpec, squareFallbackImage: UIImage?) -> [String: Any] {
        guard #available(iOS 26.0, *) else { return [:] }
        let supportedKeys = Set(MPNowPlayingInfoCenter.supportedAnimatedArtworkKeys)
        var entries: [String: Any] = [:]
        if supportedKeys.contains(MPNowPlayingInfoProperty1x1AnimatedArtwork),
           let artworkId = spec.squareArtworkId,
           let videoUrl = spec.squareVideoUrl {
            entries[MPNowPlayingInfoProperty1x1AnimatedArtwork] = makeAnimatedArtwork(
                artworkId: artworkId,
                videoUrl: videoUrl,
                previewImageUrl: spec.squarePreviewImageUrl,
                preloadedPreview: spec.squarePreviewImage,
                squareFallback: squareFallbackImage,
                targetAspectRatio: 1.0
            )
        }
        if supportedKeys.contains(MPNowPlayingInfoProperty3x4AnimatedArtwork),
           let artworkId = spec.tallArtworkId,
           let videoUrl = spec.tallVideoUrl {
            entries[MPNowPlayingInfoProperty3x4AnimatedArtwork] = makeAnimatedArtwork(
                artworkId: artworkId,
                videoUrl: videoUrl,
                previewImageUrl: spec.tallPreviewImageUrl,
                preloadedPreview: spec.tallPreviewImage,
                squareFallback: nil,
                targetAspectRatio: 3.0 / 4.0
            )
        }
        return entries
    }

    @available(iOS 26.0, *)
    private func makeAnimatedArtwork(
        artworkId: String,
        videoUrl: String,
        previewImageUrl: String?,
        preloadedPreview: UIImage?,
        squareFallback: UIImage?,
        targetAspectRatio: CGFloat
    ) -> MPMediaItemAnimatedArtwork {
        let readyPreview = preloadedPreview.flatMap { Self.normalizedPreviewImage($0, targetAspectRatio: targetAspectRatio) }
        let readyFallback = squareFallback.flatMap { Self.normalizedPreviewImage($0, targetAspectRatio: targetAspectRatio) }
        return MPMediaItemAnimatedArtwork(
            artworkID: artworkId,
            previewImageRequestHandler: { requestedSize, completion in
                let finish = OnceCompletion(completion)
                if let readyPreview {
                    finish.call(readyPreview)
                    return
                }
                if let previewImageUrl {
                    let (width, height) = Self.targetPreviewDimensions(requestedSize: requestedSize, targetAspectRatio: targetAspectRatio)
                    IosNowPlayingAnimatedArtworkKt.fetchIosAnimatedArtworkPreviewImage(
                        url: previewImageUrl,
                        targetWidth: Int32(width),
                        targetHeight: Int32(height)
                    ) { image in
                        if let valid = image.flatMap({ Self.normalizedPreviewImage($0, targetAspectRatio: targetAspectRatio) }) {
                            finish.call(valid)
                        } else if let readyFallback {
                            finish.call(readyFallback)
                        } else {
                            Self.extractVideoPreviewFrame(
                                artworkId: artworkId,
                                videoUrl: videoUrl,
                                targetAspectRatio: targetAspectRatio,
                                finish: finish
                            )
                        }
                    }
                    return
                }
                if let readyFallback {
                    finish.call(readyFallback)
                    return
                }
                Self.extractVideoPreviewFrame(
                    artworkId: artworkId,
                    videoUrl: videoUrl,
                    targetAspectRatio: targetAspectRatio,
                    finish: finish
                )
            },
            videoAssetFileURLRequestHandler: { _, completion in
                let finish = OnceCompletion(completion)
                IosNowPlayingAnimatedArtworkKt.fetchIosAnimatedArtworkVideoFilePath(
                    artworkId: artworkId,
                    sourceUrl: videoUrl
                ) { path in
                    guard let path, !path.isEmpty else {
                        finish.call(nil)
                        return
                    }
                    let fileURL = URL(fileURLWithPath: path)
                    Task {
                        let valid = await Self.matchesVideoAspectRatio(fileURL: fileURL, targetAspectRatio: targetAspectRatio)
                        finish.call(valid ? fileURL : nil)
                    }
                }
            }
        )
    }

    private static func targetPreviewDimensions(requestedSize: CGSize, targetAspectRatio: CGFloat) -> (Int, Int) {
        let defaultWidth = 768
        let defaultHeight = targetAspectRatio < 1.0 ? 1024 : 768
        guard requestedSize.width > 0, requestedSize.height > 0 else {
            return (defaultWidth, defaultHeight)
        }
        let maxSide = min(max(Int(requestedSize.width.rounded()), Int(requestedSize.height.rounded())), 1024)
        if targetAspectRatio < 1.0 {
            let height = max(maxSide, 256)
            let width = max(Int((CGFloat(height) * targetAspectRatio).rounded()), 192)
            return (width, height)
        }
        let side = max(maxSide, 256)
        return (side, side)
    }

    private static func normalizedPreviewImage(_ image: UIImage, targetAspectRatio: CGFloat) -> UIImage? {
        let size = image.size
        guard size.width > 0, size.height > 0 else { return nil }
        let ratio = size.width / size.height
        if abs(ratio - targetAspectRatio) <= 0.06 {
            return image
        }
        guard abs(targetAspectRatio - 1.0) < 0.01 else { return nil }
        let side = min(size.width, size.height)
        let origin = CGPoint(x: (side - size.width) / 2, y: (side - size.height) / 2)
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { _ in
            image.draw(at: origin)
        }
    }

    @available(iOS 26.0, *)
    private static func extractVideoPreviewFrame(
        artworkId: String,
        videoUrl: String,
        targetAspectRatio: CGFloat,
        finish: OnceCompletion<UIImage?>
    ) {
        IosNowPlayingAnimatedArtworkKt.fetchIosAnimatedArtworkVideoFilePath(
            artworkId: artworkId,
            sourceUrl: videoUrl
        ) { path in
            guard let path, !path.isEmpty else {
                finish.call(nil)
                return
            }
            let asset = AVURLAsset(url: URL(fileURLWithPath: path))
            Task {
                let generator = AVAssetImageGenerator(asset: asset)
                generator.appliesPreferredTrackTransform = true
                do {
                    let result = try await generator.image(at: .zero)
                    let image = UIImage(cgImage: result.image)
                    finish.call(Self.normalizedPreviewImage(image, targetAspectRatio: targetAspectRatio))
                } catch {
                    finish.call(nil)
                }
            }
        }
    }

    @available(iOS 26.0, *)
    private static func matchesVideoAspectRatio(fileURL: URL, targetAspectRatio: CGFloat) async -> Bool {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return false }
        let asset = AVURLAsset(url: fileURL)
        do {
            let tracks = try await asset.loadTracks(withMediaType: .video)
            guard let track = tracks.first else { return false }
            let naturalSize = try await track.load(.naturalSize)
            let transform = try await track.load(.preferredTransform)
            let oriented = naturalSize.applying(transform)
            let width = abs(oriented.width)
            let height = abs(oriented.height)
            guard width > 0, height > 0 else { return false }
            return abs((width / height) - targetAspectRatio) <= 0.06
        } catch {
            return false
        }
    }
}

private final class OnceCompletion<Value> {
    private let lock = NSLock()
    private var completion: ((Value) -> Void)?

    init(_ completion: @escaping (Value) -> Void) {
        self.completion = completion
    }

    func call(_ value: Value) {
        lock.lock()
        let handler = completion
        completion = nil
        lock.unlock()
        handler?(value)
    }
}
