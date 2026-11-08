// Marca sobre una captura las líneas que cuentan el caso: un recuadro y una etiqueta numerada por cada una.
// Uso: swift analisis/evidencia/anotar.swift <entrada.png> <salida.png> "x,y,ancho,alto|qué marca" ...
// Las coordenadas van en píxeles de la imagen original, con el origen arriba a la izquierda.
// Con "x,y,ancho,alto@cx,cy|…" el número va centrado en (cx, cy) en vez de al lado del recuadro.
import AppKit
import CoreGraphics

let args = CommandLine.arguments
guard args.count >= 4, let img = NSImage(contentsOfFile: args[1]),
      let cg = img.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    print("uso: anotar.swift entrada.png salida.png \"x,y,w,h|etiqueta\" ..."); exit(2)
}
let W = cg.width, H = cg.height
let ctx = CGContext(data: nil, width: W, height: H, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
ctx.draw(cg, in: CGRect(x: 0, y: 0, width: W, height: H))
let color = CGColor(red: 0.92, green: 0.41, blue: 0.20, alpha: 1)        // naranja de la paleta (#eb6834)
let fondo = CGColor(red: 0.92, green: 0.41, blue: 0.20, alpha: 0.95)
NSGraphicsContext.current = NSGraphicsContext(cgContext: ctx, flipped: false)
for (i, spec) in args.dropFirst(3).enumerated() {
    let partes = spec.split(separator: "|", maxSplits: 1).map(String.init)
    let caja = partes[0].split(separator: "@").map(String.init)
    let n = caja[0].split(separator: ",").compactMap { Double($0) }
    let fijo = caja.count > 1 ? caja[1].split(separator: ",").compactMap { Double($0) } : []
    let r = CGRect(x: n[0], y: Double(H) - n[1] - n[3], width: n[2], height: n[3])
    ctx.setStrokeColor(color); ctx.setLineWidth(4)
    ctx.addPath(CGPath(roundedRect: r, cornerWidth: 8, cornerHeight: 8, transform: nil)); ctx.strokePath()
    // Un número en un círculo sobre la esquina superior izquierda; el pie de figura dice qué es.
    let radio: CGFloat = 20
    // Afuera del recuadro para no tapar texto: a la izquierda si cabe, si no a la derecha; si no, en la esquina.
    var centro = CGPoint(x: r.minX, y: min(r.maxY, Double(H) - radio - 4))
    if r.minX > 2 * radio + 12 {
        centro = CGPoint(x: r.minX - radio - 8, y: r.midY)
    } else if r.maxX + 2 * radio + 12 < CGFloat(W) {
        centro = CGPoint(x: r.maxX + radio + 8, y: r.midY)
    } else {
        centro.x = max(r.minX, radio + 4)
    }
    if fijo.count == 2 { centro = CGPoint(x: fijo[0], y: Double(H) - fijo[1]) }
    ctx.setFillColor(fondo)
    ctx.fillEllipse(in: CGRect(x: centro.x - radio, y: centro.y - radio, width: 2 * radio, height: 2 * radio))
    let numero = "\(i + 1)" as NSString
    let attrs: [NSAttributedString.Key: Any] = [.font: NSFont.boldSystemFont(ofSize: 24), .foregroundColor: NSColor.white]
    let tam = numero.size(withAttributes: attrs)
    numero.draw(at: CGPoint(x: centro.x - tam.width / 2, y: centro.y - tam.height / 2), withAttributes: attrs)
    _ = partes
}
let salida = ctx.makeImage()!
let rep = NSBitmapImageRep(cgImage: salida)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[2]))
print("anotada: \(args[2]) (\(W)×\(H))")
