package co.mati.reto4.comun;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * La verificación criptográfica completa de ADR-006: firma, firmante, cadena,
 * estampa y revocación, sobre el resumen calculado en streaming de los mismos
 * bytes que se custodian.
 *
 * <p>Cada comprobación queda en el mapa {@code comprobaciones}, que va a la
 * evidencia (ADR-008). La primera que falla da la causa del rechazo. Si todo pasa
 * menos la revocación, porque la caché venció y la ECD no responde, el resultado
 * es PENDIENTE: el archivo se acusa, pero no llega a resultado definitivo.
 */
public final class VerificadorCriptografico {

    public enum Estado { VERIFICADO, PENDIENTE, RECHAZADO }

    public record Veredicto(Estado estado, String causa, Map<String, String> comprobaciones,
                            String serial, String firmante) {}

    private final List<X509Certificate> raices;
    private final X509Certificate tsa;
    private final CacheRevocacion revocacion;
    private final Duration desfase;
    private final Duration antiguedad;

    public VerificadorCriptografico(List<X509Certificate> raices, X509Certificate tsa, CacheRevocacion revocacion,
                                    Duration desfase, Duration antiguedad) {
        this.raices = raices;
        this.tsa = tsa;
        this.revocacion = revocacion;
        this.desfase = desfase;
        this.antiguedad = antiguedad;
    }

    public Veredicto verificar(LectorArchivo.Contenido c, byte[] resumenCalculado) {
        Map<String, String> ok = new LinkedHashMap<>();
        byte[] resumenDeclarado = Base64.getDecoder().decode(c.resumen());
        if (resumenCalculado == null || !Arrays.equals(resumenCalculado, resumenDeclarado)) {
            return rechazo("RESUMEN_NO_COINCIDE", ok, null, null);
        }
        ok.put("resumen", "ok");

        X509Certificate crt;
        try {
            crt = Pki.certificado(Base64.getDecoder().decode(c.certificado()));
        } catch (RuntimeException e) {
            return rechazo("CERTIFICADO_ILEGIBLE", ok, null, null);
        }
        String serial = crt.getSerialNumber().toString();
        String firmante = cn(crt);

        if (!encadena(crt)) {
            return rechazo("CADENA_NO_ACEPTADA", ok, serial, firmante);
        }
        ok.put("cadena", "ok");

        if (!Pki.verificar(crt, resumenDeclarado, Base64.getDecoder().decode(c.valor()))) {
            return rechazo("FIRMA_INVALIDA", ok, serial, firmante);
        }
        ok.put("firma", "ok");

        if (!c.remitente().equals(firmante)) {
            return rechazo("FIRMANTE_NO_ES_REMITENTE", ok, serial, firmante);
        }
        ok.put("firmante", "ok");

        if (!Pki.verificar(tsa, Formato.datosEstampa(resumenDeclarado, c.tiempoEstampa()), Base64.getDecoder().decode(c.valorEstampa()))) {
            return rechazo("ESTAMPA_INVALIDA", ok, serial, firmante);
        }
        Instant tiempo;
        try {
            tiempo = Instant.parse(c.tiempoEstampa());
        } catch (RuntimeException e) {
            return rechazo("ESTAMPA_INVALIDA", ok, serial, firmante);
        }
        Instant ahora = Instant.now();
        if (tiempo.isAfter(ahora.plus(desfase))) {
            return rechazo("ESTAMPA_DESFASADA", ok, serial, firmante);
        }
        if (tiempo.isBefore(ahora.minus(antiguedad))) {
            return rechazo("ESTAMPA_VENCIDA", ok, serial, firmante);
        }
        ok.put("estampa", "ok");

        return switch (revocacion.estado(crt.getSerialNumber())) {
            case REVOCADO -> rechazo("CERTIFICADO_REVOCADO", ok, serial, firmante);
            case DESCONOCIDO -> {
                ok.put("revocacion", "pendiente");
                yield new Veredicto(Estado.PENDIENTE, "REVOCACION_SIN_VERIFICAR", ok, serial, firmante);
            }
            case VIGENTE -> {
                ok.put("revocacion", "ok");
                yield new Veredicto(Estado.VERIFICADO, null, ok, serial, firmante);
            }
        };
    }

    /** Solo la revocación, para la ventana nocturna (ADR-007): el resto ya se verificó al recibir. */
    public CacheRevocacion.Estado revocacion(java.math.BigInteger serial) {
        return revocacion.estado(serial);
    }

    public CacheRevocacion cache() {
        return revocacion;
    }

    private boolean encadena(X509Certificate crt) {
        for (X509Certificate raiz : raices) {
            try {
                if (!crt.getIssuerX500Principal().equals(raiz.getSubjectX500Principal())) {
                    continue;
                }
                crt.verify(raiz.getPublicKey());
                crt.checkValidity();
                return true;
            } catch (Exception e) {
                // la siguiente raíz
            }
        }
        return false;
    }

    private static Veredicto rechazo(String causa, Map<String, String> ok, String serial, String firmante) {
        ok.put("fallo", causa);
        return new Veredicto(Estado.RECHAZADO, causa, ok, serial, firmante);
    }

    static String cn(X509Certificate crt) {
        try {
            for (Rdn rdn : new LdapName(crt.getSubjectX500Principal().getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return rdn.getValue().toString();
                }
            }
        } catch (Exception e) {
            // sin CN
        }
        return "";
    }
}
