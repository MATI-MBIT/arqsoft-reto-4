package co.mati.reto4.comun;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El registro de evidencia de solo anexar, firmado por BCR (ADR-008, SEG-18).
 *
 * <p>Una entrada por archivo verificado: la llave, el hash de los bytes custodiados,
 * el certificado del firmante, la estampa y el resultado de cada comprobación. El
 * texto que se firma se guarda tal cual, para poder verificar la firma después. La
 * tabla tiene un disparador que rechaza UPDATE y DELETE.
 */
public final class Evidencia {

    private final JdbcTemplate jdbc;
    private final Registro registro;
    private final ObjectMapper json;
    private volatile PrivateKey llave;
    private volatile X509Certificate certificado;

    public Evidencia(JdbcTemplate jdbc, Registro registro, ObjectMapper json) {
        this.jdbc = jdbc;
        this.registro = registro;
        this.json = json;
    }

    public void registrar(String llave, String hash, VerificadorCriptografico.Veredicto v, String tiempoEstampa) {
        Map<String, Object> entrada = new LinkedHashMap<>();
        entrada.put("llave", llave);
        entrada.put("hash", hash);
        entrada.put("serial", v.serial());
        entrada.put("firmante", v.firmante());
        entrada.put("estampa", tiempoEstampa);
        entrada.put("comprobaciones", new TreeMap<>(v.comprobaciones()));
        String contenido;
        try {
            contenido = json.writeValueAsString(entrada);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String firma = Base64.getEncoder().encodeToString(Pki.firmar(llaveBcr(), contenido.getBytes(StandardCharsets.UTF_8)));
        jdbc.update("""
                INSERT INTO operacion.evidencia (llave, hash, serial, firmante, estampa, contenido, firma_bcr)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", llave, hash, v.serial(), v.firmante(), tiempoEstampa, contenido, firma);
        registro.archivo("evidencia.registrada", llave, Map.of("hash", hash));
    }

    /**
     * Audita cada entrada (S-3): la firma de BCR es válida, el hash de la entrada es el
     * del contenido firmado y coincide con el de los bytes que el carril procesó.
     * Anota un evento por archivo y devuelve el resumen.
     */
    public Map<String, Object> auditar() {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT e.llave, e.hash, e.contenido, e.firma_bcr, a.hash_procesado, a.resultado
                FROM operacion.evidencia e LEFT JOIN operacion.archivo a ON a.llave = e.llave""");
        int validas = 0;
        int coinciden = 0;
        for (Map<String, Object> f : filas) {
            String contenido = (String) f.get("contenido");
            boolean firmaValida = Pki.verificar(certificadoBcr(), contenido.getBytes(StandardCharsets.UTF_8),
                    Base64.getDecoder().decode((String) f.get("firma_bcr")));
            boolean hashFirmado = contenido.contains("\"hash\":\"" + f.get("hash") + "\"");
            Object procesado = f.get("hash_procesado");
            boolean hashCoincide = hashFirmado && (procesado == null || procesado.equals(f.get("hash")));
            validas += firmaValida ? 1 : 0;
            coinciden += hashCoincide ? 1 : 0;
            registro.archivo("evidencia.auditada", (String) f.get("llave"), Map.of("firmaValida", firmaValida,
                    "hashCoincide", hashCoincide, "procesado", procesado != null,
                    "resultado", f.get("resultado") == null ? "" : f.get("resultado")));
        }
        return Map.of("entradas", filas.size(), "firmasValidas", validas, "hashesCoinciden", coinciden);
    }

    private PrivateKey llaveBcr() {
        if (llave == null) {
            llave = Pki.llave("bcr");
        }
        return llave;
    }

    private X509Certificate certificadoBcr() {
        if (certificado == null) {
            certificado = Pki.certificado("bcr");
        }
        return certificado;
    }
}
