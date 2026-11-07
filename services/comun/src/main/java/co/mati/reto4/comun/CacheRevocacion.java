package co.mati.reto4.comun;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509CRL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * La lista de revocación de la ECD en caché local (ADR-007, LAT-10).
 *
 * <p>Mientras la copia esté vigente, la consulta es local. Cuando vence, se intenta
 * refrescarla de la ECD con un timeout corto; si la ECD no responde, el estado del
 * certificado es DESCONOCIDO y el archivo queda en verificación pendiente. Para no
 * cobrarle el timeout a cada archivo mientras la ECD está caída, un refresco
 * fallido no se reintenta antes de {@code ESPERA_TRAS_FALLO}.
 */
public final class CacheRevocacion {

    public enum Estado { VIGENTE, REVOCADO, DESCONOCIDO }

    private static final Duration ESPERA_TRAS_FALLO = Duration.ofSeconds(2);

    private final String urlCrl;
    private final Duration vigencia;
    private final Duration timeout;
    private final Registro registro;
    private final HttpClient http;
    private volatile X509CRL crl;
    private volatile Instant obtenida = Instant.EPOCH;
    private volatile Instant ultimoFallo = Instant.EPOCH;

    public CacheRevocacion(String urlCrl, Duration vigencia, Duration timeout, Registro registro) {
        this.urlCrl = urlCrl;
        this.vigencia = vigencia;
        this.timeout = timeout;
        this.registro = registro;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public Estado estado(BigInteger serial) {
        if (!vigente()) {
            refrescar();
        }
        X509CRL actual = crl;
        if (actual == null || !vigente()) {
            return Estado.DESCONOCIDO;
        }
        return actual.getRevokedCertificate(serial) != null ? Estado.REVOCADO : Estado.VIGENTE;
    }

    public boolean vigente() {
        return crl != null && Instant.now().isBefore(obtenida.plus(vigencia));
    }

    /** Fuerza el refresco ignorando la espera tras un fallo (la ventana nocturna lo usa). */
    public synchronized boolean refrescarYa() {
        ultimoFallo = Instant.EPOCH;
        return refrescar();
    }

    private synchronized boolean refrescar() {
        if (vigente()) {
            return true;
        }
        if (Instant.now().isBefore(ultimoFallo.plus(ESPERA_TRAS_FALLO))) {
            return false;
        }
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(urlCrl)).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + r.statusCode());
            }
            crl = Pki.crl(r.body());
            obtenida = Instant.now();
            if (registro != null) registro.hecho("revocacion.refrescada", Map.of("revocados", crl.getRevokedCertificates() == null ? 0 : crl.getRevokedCertificates().size()));
            return true;
        } catch (Exception e) {
            ultimoFallo = Instant.now();
            if (registro != null) registro.hecho("revocacion.refresco_fallido", Map.of("error", String.valueOf(e.getMessage())));
            return false;
        }
    }

    /** Gancho de prueba (S-2): da por vencida la copia local. */
    public synchronized void vencer() {
        obtenida = Instant.EPOCH;
        if (registro != null) registro.hecho("revocacion.vencida", Map.of());
    }
}
