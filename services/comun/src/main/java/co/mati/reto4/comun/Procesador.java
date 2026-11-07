package co.mati.reto4.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Procesa las declaraciones de un archivo custodiado. Lo usan los dos carriles
 * (ADR-005) y el reprocesador de la ventana nocturna (ADR-003).
 *
 * <ol>
 *   <li>Vuelve a calcular el hash de los bytes custodiados y lo compara con el de la
 *       recepción: procesa exactamente lo que se verificó (ADR-006).</li>
 *   <li>Lee las declaraciones en streaming, por lotes. Antes de cada lote consulta el
 *       estado del RNIC en el monitor; si está caído, o si el adaptador vence su
 *       timeout, el archivo queda <b>pendiente</b> desde ese lote: nunca se rechaza
 *       por la caída (ADR-003).</li>
 *   <li>Escribe el resultado de cada declaración una sola vez por la llave
 *       (archivo, declaración) y el resultado definitivo del archivo una sola vez por
 *       la llave del archivo (ADR-004).</li>
 * </ol>
 *
 * El costo de las reglas cambiarias se simula con {@code COSTO_REGLA_US} de CPU por
 * declaración: sin él, un masivo duraría segundos y el aislamiento de ASR-L04 no se
 * pondría a prueba.
 */
@Component
public final class Procesador {

    public enum Desenlace { RESUELTO, PENDIENTE, YA_RESUELTO, OMITIDO }

    /** Lanzada desde el lector para cortar el recorrido: el archivo queda pendiente desde {@code desdeN}. */
    private static final class Corte extends RuntimeException {
        final int desdeN;

        Corte(int desdeN) {
            super(null, null, false, false);
            this.desdeN = desdeN;
        }
    }

    private static final String INSERT_RESULTADO = """
            INSERT INTO operacion.resultado_declaracion (llave, n, resultado, causa)
            VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING""";

    private final JdbcTemplate jdbc;
    private final AdaptadorRnic rnic;
    private final MonitorCliente monitor;
    private final Registro registro;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final MeterRegistry metricas;
    private final Path custodia;
    private final long costoReglaNanos;
    private final AtomicInteger enCurso = new AtomicInteger();

    public Procesador(JdbcTemplate jdbc, AdaptadorRnic rnic, MonitorCliente monitor, Registro registro,
                      KafkaTemplate<String, String> kafka, ObjectMapper json, MeterRegistry metricas,
                      @Value("${orquidea.custodia-dir:/custodia}") String custodia,
                      @Value("${orquidea.costo-regla-us:0}") long costoReglaUs) {
        this.jdbc = jdbc;
        this.rnic = rnic;
        this.monitor = monitor;
        this.registro = registro;
        this.kafka = kafka;
        this.json = json;
        this.metricas = metricas;
        this.custodia = Path.of(custodia);
        this.costoReglaNanos = costoReglaUs * 1000;
        metricas.gauge("orquidea.procesamiento.en_curso", enCurso);
    }

    public static String archivoDeCustodia(String llave) {
        return llave.replace('|', '_') + ".xml";
    }

    /**
     * @param quien   componente que procesa: {@code carril-a}, {@code carril-b} o {@code reprocesador}
     * @param lote    declaraciones por llamada al RNIC
     * @param ritmo   tope de declaraciones por segundo, o null sin tope (el reprocesador fija el suyo)
     */
    public Desenlace procesar(Cola.ArchivoEnCola a, int desdeN, int intentos, String quien, int lote, Ritmo ritmo) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT hash, estado, resultado FROM operacion.archivo WHERE llave = ?", a.llave());
        if (filas.isEmpty()) {
            return Desenlace.OMITIDO;   // mensaje de una ejecución anterior
        }
        Map<String, Object> fila = filas.get(0);
        if (!"ACUSADO".equals(fila.get("estado"))) {
            return Desenlace.OMITIDO;   // retenido por verificación pendiente o rechazado
        }
        String tipo = a.registros() > 50 ? "masivo" : "pequeno";
        Timer.Sample muestra = Timer.start(metricas);
        enCurso.incrementAndGet();
        registro.archivo("archivo.procesamiento.inicio", a.llave(), Map.of("componente", quien, "desdeN", desdeN));
        try {
            Path ruta = custodia.resolve(a.ruta());
            String hash = Formato.hex(hashDe(ruta));
            if (!hash.equals(fila.get("hash"))) {
                return finalizar(a, quien, hash, "INTEGRIDAD");
            }
            List<LectorArchivo.Declaracion> pendientes = new ArrayList<>(lote);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(ruta), 1 << 16)) {
                LectorArchivo.leer(in, d -> {
                    if (d.n() < desdeN) {
                        return;
                    }
                    pendientes.add(d);
                    if (pendientes.size() == lote) {
                        lote(a.llave(), pendientes, ritmo);
                    }
                });
            }
            if (!pendientes.isEmpty()) {
                lote(a.llave(), pendientes, ritmo);
            }
            return finalizar(a, quien, hash, null);
        } catch (Corte c) {
            dejarPendiente(a, "TERCERO_CAIDO", c.desdeN, intentos, quien);
            return Desenlace.PENDIENTE;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            enCurso.decrementAndGet();
            muestra.stop(metricas.timer("orquidea.procesamiento", "componente", quien, "tipo", tipo));
        }
    }

    private void lote(String llave, List<LectorArchivo.Declaracion> lote, Ritmo ritmo) {
        int primera = lote.get(0).n();
        if (monitor.caido(AdaptadorRnic.TERCERO)) {
            throw new Corte(primera);
        }
        if (ritmo != null) {
            ritmo.adquirir(lote.size());
        }
        reglas(lote.size());
        boolean[] coinciden;
        try {
            coinciden = rnic.verificar(llave, lote);
        } catch (AdaptadorRnic.TerceroNoDisponible e) {
            throw new Corte(primera);
        }
        List<Object[]> filas = new ArrayList<>(lote.size());
        for (int i = 0; i < lote.size(); i++) {
            boolean ok = i < coinciden.length && coinciden[i];
            filas.add(new Object[] {llave, lote.get(i).n(), ok ? "ACEPTADA" : "RECHAZADA", ok ? null : "NOMBRE_NO_COINCIDE"});
        }
        jdbc.batchUpdate(INSERT_RESULTADO, filas);
        metricas.counter("orquidea.declaraciones").increment(lote.size());
        lote.clear();
    }

    /** CPU de las reglas cambiarias, simulada. */
    private void reglas(int declaraciones) {
        if (costoReglaNanos <= 0) {
            return;
        }
        long fin = System.nanoTime() + costoReglaNanos * declaraciones;
        while (System.nanoTime() < fin) {
            Thread.onSpinWait();
        }
    }

    /**
     * Escribe el resultado definitivo una sola vez: el UPDATE solo gana si el archivo
     * aún no tiene resultado. Solo quien gana emite el efecto (la notificación del
     * resultado), así que un mensaje repetido no produce un segundo efecto.
     */
    private Desenlace finalizar(Cola.ArchivoEnCola a, String quien, String hash, String causaArchivo) {
        Map<String, Object> c = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE resultado = 'ACEPTADA') AS aceptadas,
                       count(*) FILTER (WHERE resultado = 'RECHAZADA') AS rechazadas
                FROM operacion.resultado_declaracion WHERE llave = ?""", a.llave());
        long aceptadas = ((Number) c.get("aceptadas")).longValue();
        long rechazadas = ((Number) c.get("rechazadas")).longValue();
        String resultado;
        if (causaArchivo != null) {
            resultado = "RECHAZO";
        } else if (aceptadas + rechazadas < a.registros()) {
            throw new IllegalStateException("faltan resultados de " + a.llave());
        } else {
            resultado = rechazadas == 0 ? "TOTAL" : aceptadas == 0 ? "RECHAZO" : "PARCIAL";
        }
        int gano = jdbc.update("""
                UPDATE operacion.archivo SET resultado = ?, causa = coalesce(?, causa), resultado_en = clock_timestamp(),
                       hash_procesado = ?, resuelto_por = ?
                WHERE llave = ? AND resultado IS NULL AND estado = 'ACUSADO'""",
                resultado, causaArchivo, hash, quien, a.llave());
        if (gano == 0) {
            registro.archivo("resultado.repetido_evitado", a.llave(), Map.of("componente", quien));
            return Desenlace.YA_RESUELTO;
        }
        jdbc.update("INSERT INTO operacion.efecto_resultado (llave, componente) VALUES (?, ?)", a.llave(), quien);
        jdbc.update("UPDATE operacion.pendiente SET estado = 'RESUELTO', resuelto_en = clock_timestamp() WHERE llave = ? AND estado = 'PENDIENTE'", a.llave());
        registro.archivo("archivo.resultado", a.llave(), Map.of("resultado", resultado, "aceptadas", aceptadas,
                "rechazadas", rechazadas, "registros", a.registros(), "hash", hash, "componente", quien,
                "causa", causaArchivo == null ? "" : causaArchivo));
        return Desenlace.RESUELTO;
    }

    /** Deja el archivo en espera durable: fila en la base y mensaje en el tópico de pendientes. */
    public void dejarPendiente(Cola.ArchivoEnCola a, String motivo, int desdeN, int intentos, String quien) {
        jdbc.update("""
                INSERT INTO operacion.pendiente (llave, motivo, desde_n, intentos, estado, creado_en)
                VALUES (?, ?, ?, ?, 'PENDIENTE', clock_timestamp())
                ON CONFLICT (llave) DO UPDATE SET motivo = excluded.motivo, desde_n = excluded.desde_n,
                       intentos = excluded.intentos, estado = 'PENDIENTE'""",
                a.llave(), motivo, desdeN, intentos);
        Cola.publicar(kafka, json, Cola.PENDIENTES, a.llave(),
                new Cola.Pendiente(a.llave(), motivo, desdeN, intentos, a.ruta(), a.bytes(), a.registros()));
        registro.archivo("archivo.pendiente", a.llave(), Map.of("motivo", motivo, "desdeN", desdeN,
                "intentos", intentos, "componente", quien));
    }

    private static byte[] hashDe(Path ruta) throws IOException {
        var d = FlujoCustodiado.sha256();
        byte[] buf = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(ruta)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                d.update(buf, 0, n);
            }
        }
        return d.digest();
    }
}
