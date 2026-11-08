// Extrae cuadros de un clip en los segundos dados, para revisar que el video muestra
// lo que el pie dice que muestra. Uso:
//   swift analisis/evidencia/cuadros.swift <clip.mp4> <carpeta> <s1> <s2> ...
// Escribe <carpeta>/<s>.png por cada segundo, y la duración del clip.
import AVFoundation
import AppKit

let args = CommandLine.arguments
guard args.count >= 4 else { print("uso: cuadros.swift <clip.mp4> <carpeta> <segundos>..."); exit(2) }
let asset = AVURLAsset(url: URL(fileURLWithPath: args[1]))
let dur = CMTimeGetSeconds(asset.duration)
print(String(format: "duración: %.1f s", dur))
let gen = AVAssetImageGenerator(asset: asset)
gen.appliesPreferredTrackTransform = true
gen.requestedTimeToleranceBefore = .zero
gen.requestedTimeToleranceAfter = .zero
try? FileManager.default.createDirectory(atPath: args[2], withIntermediateDirectories: true)
for s in args.dropFirst(3) {
    guard let seg = Double(s) else { continue }
    let t = CMTime(seconds: min(seg, max(0, dur - 0.1)), preferredTimescale: 600)
    do {
        let img = try gen.copyCGImage(at: t, actualTime: nil)
        let rep = NSBitmapImageRep(cgImage: img)
        let png = rep.representation(using: .png, properties: [:])!
        try png.write(to: URL(fileURLWithPath: "\(args[2])/\(s).png"))
        print("cuadro \(s) s → \(args[2])/\(s).png")
    } catch { print("no se pudo extraer el cuadro \(s): \(error)") }
}
