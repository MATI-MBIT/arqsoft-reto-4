// Arma un clip MP4 (H.264) con los cuadros que el renderizador de Grafana tomó
// durante un caso de load/evidencias.sh. Cada cuadro se llama <epoch-ms>.png. Sobre
// cada uno se sobreimprime una franja con el segundo del caso y el último estímulo
// anotado en el registro del caso (la línea «+Ns  texto» más reciente): el estímulo
// y la respuesta quedan en la misma imagen.
//
//   swift analisis/evidencia/video.swift <cuadros/> <evidencias.log> <t0-epoch-s> <titulo> <salida.mp4> [cuadros-por-segundo]
import AVFoundation
import AppKit

let a = CommandLine.arguments
guard a.count >= 6 else { print("uso: video.swift <cuadros> <log> <t0> <titulo> <salida.mp4> [fps]"); exit(2) }
let dir = a[1], logPath = a[2], t0 = Double(a[3])!, titulo = a[4], salida = a[5]
let fps = Int32(a.count > 6 ? Int(a[6])! : 2)

// Estímulos: (segundo del caso, texto)
var marcas: [(Double, String)] = []
if let log = try? String(contentsOfFile: logPath, encoding: .utf8) {
    for linea in log.split(separator: "\n") {
        guard let r = linea.range(of: #"\+\s*(\d+)s\s+(.*)$"#, options: .regularExpression) else { continue }
        let parte = String(linea[r]).dropFirst()
        let campos = parte.split(separator: "s", maxSplits: 1)
        if let s = Double(campos[0].trimmingCharacters(in: .whitespaces)), campos.count == 2 {
            marcas.append((s, campos[1].trimmingCharacters(in: .whitespaces)))
        }
    }
}

let archivos = try FileManager.default.contentsOfDirectory(atPath: dir).filter { $0.hasSuffix(".png") }.sorted()
guard !archivos.isEmpty else { print("sin cuadros en \(dir)"); exit(1) }
let W = 1600, H = 1000, BANDA = 56
try? FileManager.default.removeItem(atPath: salida)
let writer = try AVAssetWriter(outputURL: URL(fileURLWithPath: salida), fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
    AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: W, AVVideoHeightKey: H + BANDA,
    AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: 2_500_000]])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: W,
    kCVPixelBufferHeightKey as String: H + BANDA])
writer.add(input)
writer.startWriting()
writer.startSession(atSourceTime: .zero)

func texto(_ s: String, _ tam: CGFloat, _ color: NSColor, _ peso: NSFont.Weight = .regular) -> NSAttributedString {
    NSAttributedString(string: s, attributes: [.font: NSFont.systemFont(ofSize: tam, weight: peso), .foregroundColor: color])
}

for (i, nombre) in archivos.enumerated() {
    guard let ms = Double(nombre.replacingOccurrences(of: ".png", with: "")),
          let img = NSImage(contentsOfFile: "\(dir)/\(nombre)")?.cgImage(forProposedRect: nil, context: nil, hints: nil) else { continue }
    let seg = ms / 1000 - t0
    var estimulo = marcas.last(where: { $0.0 <= seg })?.1 ?? "preparado: la carga de pequeños está por arrancar"
    if estimulo.count > 78 { estimulo = String(estimulo.prefix(77)) + "…" }
    var pb: CVPixelBuffer?
    CVPixelBufferPoolCreatePixelBuffer(nil, adaptor.pixelBufferPool!, &pb)
    guard let buffer = pb else { continue }
    CVPixelBufferLockBaseAddress(buffer, [])
    let ctx = CGContext(data: CVPixelBufferGetBaseAddress(buffer), width: W, height: H + BANDA, bitsPerComponent: 8,
                        bytesPerRow: CVPixelBufferGetBytesPerRow(buffer), space: CGColorSpaceCreateDeviceRGB(),
                        bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue)!
    ctx.setFillColor(CGColor(red: 0.07, green: 0.07, blue: 0.09, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: W, height: H + BANDA))
    ctx.draw(img, in: CGRect(x: 0, y: 0, width: W, height: H))
    NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
    ctx.setFillColor(CGColor(red: 0.95, green: 0.55, blue: 0.20, alpha: 1))
    ctx.fill(CGRect(x: 0, y: H, width: 8, height: BANDA))
    texto(String(format: "+%02.0f s", max(0, seg)), 26, .white, .bold).draw(at: CGPoint(x: 24, y: CGFloat(H + 14)))
    texto(estimulo, 22, NSColor(white: 0.92, alpha: 1), .medium).draw(at: CGPoint(x: 130, y: CGFloat(H + 16)))
    let t = texto(titulo, 16, NSColor(white: 0.6, alpha: 1))
    t.draw(at: CGPoint(x: CGFloat(W) - t.size().width - 24, y: CGFloat(H + 20)))
    CVPixelBufferUnlockBaseAddress(buffer, [])
    while !input.isReadyForMoreMediaData { usleep(5000) }
    adaptor.append(buffer, withPresentationTime: CMTime(value: Int64(i), timescale: fps))
}
input.markAsFinished()
let listo = DispatchSemaphore(value: 0)
writer.finishWriting { listo.signal() }
listo.wait()
print("clip: \(salida) · \(archivos.count) cuadros a \(fps) por segundo · \(String(format: "%.1f", Double(archivos.count) / Double(fps))) s")
