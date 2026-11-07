package co.mati.reto4.recepcion;

import co.mati.reto4.comun.ArchivoInvalido;
import co.mati.reto4.comun.Cola;
import co.mati.reto4.comun.Evidencia;
import co.mati.reto4.comun.FlujoCustodiado;
import co.mati.reto4.comun.Formato;
import co.mati.reto4.comun.LectorArchivo;
import co.mati.reto4.comun.Procesador;
import co.mati.reto4.comun.Registro;
import co.mati.reto4.comun.VerificadorCriptografico;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recepción y acuse (ADR-001, ADR-004, ADR-005, ADR-006, ADR-007, ADR-008).
 *
 * <p>Lee el archivo una sola vez en streaming: lo custodia, calcula su hash y su
 * resumen y valida su estructura mientras llegan los bytes. Con el último byte (t0)
 * termina la verificación criptográfica. Si pasa, registra la llave en la misma
 * transacción que el archivo, anexa la evidencia firmada, publica el archivo en el
 * tópico de su carril según su tamaño en bytes y solo entonces acusa. Si la
 * revocación no se pudo verificar, acusa en «verificación pendiente» y deja el
 * archivo para la ventana nocturna, sin publicarlo a ningún carril.
 */
@RestController
public class RecepcionController {

    private final JdbcTemplate jdbc;
    private final VerificadorCriptografico verificador;
    private final Evidencia evidencia;
    private final Procesador procesador;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final Registro registro;
    private final MeterRegistry metricas;
    private final Path custodia;
    private final boolean porTamano;
    private final long umbralBytes;

    public RecepcionController(JdbcTemplate jdbc, VerificadorCriptografico verificador, Evidencia evidencia,
                               Procesador procesador, KafkaTemplate<String, String> kafka, ObjectMapper json,
                               Registro registro, MeterRegistry metricas,
                               @Value("${orquidea.custodia-dir}") String custodia,
                               @Value("${orquidea.ruteo}") String ruteo,
                               @Value("${orquidea.umbral-bytes}") long umbralBytes) throws IOException {
        this.jdbc = jdbc;
        this.verificador = verificador;
        this.evidencia = evidencia;
        this.procesador = procesador;
        this.kafka = kafka;
        this.json = json;
        this.registro = registro;
        this.metricas = metricas;
        this.custodia = Files.createDirectories(Path.of(custodia));
        this.porTamano = !"unico".equals(ruteo);
        this.umbralBytes = umbralBytes;
    }

    @PostMapping("/archivos")
    public ResponseEntity<Map<String, Object>> recibir(HttpServletRequest req) throws IOException {
        Path tmp = custodia.resolve(UUID.randomUUID() + ".tmp");
        FlujoCustodiado flujo;
        LectorArchivo.Contenido c = null;
        String causa = null;
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16)) {
            flujo = new FlujoCustodiado(req.getInputStream(), out);
            try {
                c = LectorArchivo.leer(flujo, null);
            } catch (ArchivoInvalido e) {
                causa = e.causa();
            }
            if (flujo.drenar() && causa == null) {
                causa = "ESTRUCTURA_INVALIDA";
            }
        }
        Instant t0 = flujo.ultimoByte() != null ? flujo.ultimoByte() : Instant.now();
        // Sin encabezado legible, el archivo se sigue por el identificador de correlación
        // que trae la petición (R-001a): así el rechazo también entra a la conciliación.
        String correlacion = req.getHeader("X-Correlacion");
        String llave = c != null ? c.llave() : correlacion != null ? correlacion : "SIN-LLAVE-" + tmp.getFileName();
        Map<String, Object> tam = Map.of("bytes", flujo.bytes());
        registro.anotar(flujo.primerByte() != null ? flujo.primerByte() : t0, "archivo.recepcion.inicio", llave, tam);
        registro.anotar(t0, "archivo.recibido", llave, tam);
        if (causa != null) {
            return rechazar(tmp, llave, causa, flujo.bytes(), t0);
        }

        VerificadorCriptografico.Veredicto v = verificador.verificar(c, flujo.resumen());
        if (v.estado() == VerificadorCriptografico.Estado.RECHAZADO) {
            return rechazar(tmp, llave, v.causa(), flujo.bytes(), t0);
        }

        String hash = Formato.hex(flujo.hash());
        String carril = porTamano && flujo.bytes() > umbralBytes ? Cola.CARRIL_B : Cola.CARRIL_A;
        boolean pendiente = v.estado() == VerificadorCriptografico.Estado.PENDIENTE;
        String estado = pendiente ? "VERIFICACION_PENDIENTE" : "ACUSADO";

        // La llave se registra con unicidad en la misma transacción que el archivo (ADR-004).
        int nuevo = jdbc.update("""
                INSERT INTO operacion.archivo (llave, remitente, carril, registros, bytes, hash, estado, serial,
                                               estampa, recibido_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (llave) DO NOTHING""",
                llave, c.remitente(), carril, c.registros(), flujo.bytes(), hash, estado, v.serial(),
                c.tiempoEstampa(), java.sql.Timestamp.from(t0));
        if (nuevo == 0) {
            Files.deleteIfExists(tmp);
            registro.archivo("archivo.duplicado", llave, tam);
            metricas.counter("orquidea.acuses", "estado", "DUPLICADO").increment();
            return ResponseEntity.status(409).body(Map.of("llave", llave, "estado", "DUPLICADO"));
        }
        String ruta = Procesador.archivoDeCustodia(llave);
        Files.move(tmp, custodia.resolve(ruta), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        Cola.ArchivoEnCola mensaje = new Cola.ArchivoEnCola(llave, ruta, flujo.bytes(), c.registros(), carril);
        if (pendiente) {
            procesador.dejarPendiente(mensaje, "VERIFICACION_PENDIENTE", 1, 0, "recepcion");
        } else {
            evidencia.registrar(llave, hash, v, c.tiempoEstampa());
            Cola.publicar(kafka, json, carril, llave, mensaje);
        }
        Instant acuse = Instant.now();
        long ms = Duration.between(t0, acuse).toMillis();
        registro.anotar(acuse, "archivo.acusado", llave, Map.of("estado", estado, "carril", carril,
                "registros", c.registros(), "bytes", flujo.bytes(), "acuseMs", ms));
        metricas.timer("orquidea.acuse", "estado", estado, "carril", carril).record(Duration.between(t0, acuse));
        metricas.counter("orquidea.acuses", "estado", estado).increment();
        return ResponseEntity.accepted().body(Map.of("llave", llave, "estado", estado, "carril", carril, "acuseMs", ms));
    }

    private ResponseEntity<Map<String, Object>> rechazar(Path tmp, String llave, String causa, long bytes, Instant t0) throws IOException {
        Files.deleteIfExists(tmp);
        jdbc.update("INSERT INTO operacion.archivo_rechazado (llave, causa, bytes) VALUES (?, ?, ?)", llave, causa, bytes);
        registro.archivo("archivo.rechazado", llave, Map.of("causa", causa, "bytes", bytes,
                "acuseMs", Duration.between(t0, Instant.now()).toMillis()));
        metricas.counter("orquidea.acuses", "estado", "RECHAZADO").increment();
        metricas.counter("orquidea.rechazos", "causa", causa).increment();
        return ResponseEntity.unprocessableEntity().body(Map.of("llave", llave, "estado", "RECHAZADO", "causa", causa));
    }

    /** Gancho de prueba (S-2): vence la copia local de la CRL. */
    @PostMapping("/admin/revocacion/vencer")
    public Map<String, Object> vencer() {
        verificador.cache().vencer();
        return Map.of("vigente", verificador.cache().vigente());
    }

    /** S-3: audita el registro de evidencia completo. */
    @PostMapping("/evidencia/auditar")
    public Map<String, Object> auditar() {
        return evidencia.auditar();
    }

    /** Preparación de cada ejecución: vacía la custodia. */
    @PostMapping("/admin/reiniciar")
    public Map<String, Object> reiniciar() throws IOException {
        long borrados;
        try (Stream<Path> s = Files.list(custodia)) {
            borrados = s.filter(p -> p.toFile().delete()).count();
        }
        return Map.of("borrados", borrados);
    }
}
