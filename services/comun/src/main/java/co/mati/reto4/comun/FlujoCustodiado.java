package co.mati.reto4.comun;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Lee el archivo una sola vez y, sobre los mismos bytes, hace tres cosas a la vez:
 * los copia a la custodia, calcula el hash de todos los bytes (el que va a la
 * evidencia y el que el carril vuelve a calcular) y calcula el resumen firmado,
 * que cubre los bytes desde el inicio hasta antes de la marca {@code <firma>}.
 *
 * <p>El resumen se calcula con un retraso del largo de la marca: cada byte entra al
 * resumen solo cuando ya se sabe que no es parte de la marca. Como el lector
 * rechaza CDATA, comentarios e instrucciones, la marca solo puede aparecer como
 * etiqueta, y la primera aparición es la de la firma.
 *
 * <p>También anota el instante del primer y del último byte: el último es el t0 del
 * acuse y de la latencia de ASR-L04.
 */
public final class FlujoCustodiado extends FilterInputStream {

    private static final byte[] MARCA = Formato.MARCA_FIRMA;

    private final OutputStream custodia;
    private final MessageDigest hash = sha256();
    private final MessageDigest resumen = sha256();
    private final byte[] anillo = new byte[MARCA.length];
    private int enAnillo;
    private boolean marcaVista;
    private long bytes;
    private Instant primerByte;
    private Instant ultimoByte;

    public FlujoCustodiado(InputStream in, OutputStream custodia) {
        super(in);
        this.custodia = custodia;
    }

    @Override
    public int read() throws IOException {
        byte[] uno = new byte[1];
        int n = read(uno, 0, 1);
        return n < 0 ? -1 : uno[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n < 0) {
            if (ultimoByte == null) {
                ultimoByte = Instant.now();
            }
            return n;
        }
        if (n > 0) {
            if (primerByte == null) {
                primerByte = Instant.now();
            }
            bytes += n;
            hash.update(b, off, n);
            if (custodia != null) {
                custodia.write(b, off, n);
            }
            if (!marcaVista) {
                for (int i = off; i < off + n && !marcaVista; i++) {
                    empujar(b[i]);
                }
            }
        }
        return n;
    }

    private void empujar(byte x) {
        if (enAnillo == anillo.length) {
            resumen.update(anillo[0]);
            System.arraycopy(anillo, 1, anillo, 0, anillo.length - 1);
            enAnillo--;
        }
        anillo[enAnillo++] = x;
        if (enAnillo == anillo.length && java.util.Arrays.equals(anillo, MARCA)) {
            marcaVista = true;
        }
    }

    /** Lee lo que quede hasta el final: el último byte marca el t0. Devuelve si había algo más que espacios. */
    public boolean drenar() throws IOException {
        byte[] buf = new byte[8192];
        boolean basura = false;
        int n;
        while ((n = read(buf, 0, buf.length)) >= 0) {
            for (int i = 0; i < n && !basura; i++) {
                basura = !Character.isWhitespace(buf[i]);
            }
        }
        return basura;
    }

    public byte[] hash() {
        return ((MessageDigest) clonar(hash)).digest();
    }

    /** El resumen de los bytes firmados, o null si nunca apareció la marca de la firma. */
    public byte[] resumen() {
        return marcaVista ? ((MessageDigest) clonar(resumen)).digest() : null;
    }

    public long bytes() {
        return bytes;
    }

    public Instant primerByte() {
        return primerByte;
    }

    public Instant ultimoByte() {
        return ultimoByte;
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object clonar(MessageDigest d) {
        try {
            return d.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }
}
