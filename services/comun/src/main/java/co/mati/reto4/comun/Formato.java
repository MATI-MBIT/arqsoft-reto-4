package co.mati.reto4.comun;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * El formato XML del archivo del prototipo y su firma envuelta.
 *
 * <pre>
 * &lt;archivo&gt;
 *   &lt;encabezado&gt; remitente, fecha, lote, consecutivo, registros &lt;/encabezado&gt;
 *   &lt;declaraciones&gt; &lt;d n="1"&gt; cedula, nombre, monto, moneda, detalle &lt;/d&gt; ... &lt;/declaraciones&gt;
 *   &lt;firma&gt; resumen, valor, certificado, estampa(tiempo, valor) &lt;/firma&gt;
 * &lt;/archivo&gt;
 * </pre>
 *
 * El resumen es el SHA-256 de los bytes que van desde el inicio del documento
 * hasta antes de {@code <firma>}; el valor es la firma ECDSA del remitente sobre
 * ese resumen; la estampa es la firma de la ECD sobre {@code resumen|tiempo}. Es
 * una firma envuelta sobre bytes, no XMLDSig con canonicalización: mide el costo
 * de resumir y verificar en streaming, que es lo que pregunta SUP-01.
 */
public final class Formato {

    public static final String NS = "urn:bcr:orquidea:prueba";
    public static final byte[] MARCA_FIRMA = "<firma>".getBytes(StandardCharsets.US_ASCII);
    public static final String PROLOGO = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<archivo xmlns=\"" + NS + "\">";

    private Formato() {}

    public static String llave(String remitente, String fecha, String lote, String consecutivo) {
        return remitente + "|" + fecha + "|" + lote + "|" + consecutivo;
    }

    public static byte[] encabezado(String remitente, String fecha, String lote, String consecutivo, int registros) {
        return (PROLOGO + "<encabezado><remitente>" + remitente + "</remitente><fecha>" + fecha
                + "</fecha><lote>" + lote + "</lote><consecutivo>" + consecutivo + "</consecutivo><registros>"
                + registros + "</registros></encabezado>\n").getBytes(StandardCharsets.UTF_8);
    }

    public static String declaracion(int n, long cedula, String nombre, long monto, String detalle) {
        return "<d n=\"" + n + "\"><cedula>" + cedula + "</cedula><nombre>" + nombre + "</nombre><monto>" + monto
                + "</monto><moneda>USD</moneda><detalle>" + detalle + "</detalle></d>\n";
    }

    /** El bloque de la firma para el resumen dado. */
    public static byte[] firma(byte[] resumen, PrivateKey llaveRemitente, X509Certificate certificado,
                               PrivateKey llaveTsa, Instant tiempoEstampa) {
        byte[] valor = Pki.firmar(llaveRemitente, resumen);
        String tiempo = tiempoEstampa.toString();
        byte[] estampa = Pki.firmar(llaveTsa, datosEstampa(resumen, tiempo));
        try {
            return ("<firma><resumen>" + b64(resumen) + "</resumen><valor>" + b64(valor) + "</valor><certificado>"
                    + b64(certificado.getEncoded()) + "</certificado><estampa><tiempo>" + tiempo + "</tiempo><valor>"
                    + b64(estampa) + "</valor></estampa></firma></archivo>\n").getBytes(StandardCharsets.UTF_8);
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] datosEstampa(byte[] resumen, String tiempo) {
        return (b64(resumen) + "|" + tiempo).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] resumen(byte[]... partes) {
        MessageDigest d = FlujoCustodiado.sha256();
        for (byte[] p : partes) {
            d.update(p);
        }
        return d.digest();
    }

    public static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    public static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
