package co.mati.reto4.reprocesador;

import co.mati.reto4.comun.AdaptadorRnic;
import co.mati.reto4.comun.CacheRevocacion;
import co.mati.reto4.comun.Cola;
import co.mati.reto4.comun.Evidencia;
import co.mati.reto4.comun.MonitorCliente;
import co.mati.reto4.comun.Procesador;
import co.mati.reto4.comun.Registro;
import co.mati.reto4.comun.Ritmo;
import co.mati.reto4.comun.VerificadorCriptografico;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La ventana nocturna de reproceso (ADR-003, DIS-12 reintento diferido por cola,
 * DIS-14 degradación con gracia) y la verificación pendiente (ADR-007).
 *
 * <p>En producción corre a hora fija; en el experimento la dispara el orquestador
 * con {@code POST /ventanas}: una «noche» dura lo que tarde la prueba en llamarla.
 *
 * <p>Al abrir la ventana toma el final de cada partición del tópico de pendientes y
 * lee hasta ahí. Lo que reprograma lo vuelve a publicar con un intento más, después
 * de ese corte, así que no se relee en la misma ventana: queda para la siguiente.
 * <ul>
 *   <li>Tercero caído: si el monitor lo da por caído, reprograma; si no, reprocesa a
 *       ritmo propio desde la declaración donde quedó. Si vuelve a caer, reprograma.</li>
 *   <li>Verificación pendiente: refresca la CRL de la ECD; si no responde, reprograma;
 *       si el certificado está vigente, anexa la evidencia y publica el archivo en su
 *       carril; si está revocado, lo rechaza con su causa.</li>
 *   <li>Al reprogramar por {@code DIAS_REINTENTO}-ésima vez notifica al remitente,
 *       sin sacar el archivo de los pendientes (D-3).</li>
 * </ul>
 */
@RestController
public class Ventana {

    private final JdbcTemplate jdbc;
    private final Procesador procesador;
    private final MonitorCliente monitor;
    private final VerificadorCriptografico verificador;
    private final Evidencia evidencia;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final Registro registro;
    private final String bootstrap;
    private final int diasReintento;
    private final double ritmo;
    private final AtomicInteger numero = new AtomicInteger();
    private final AtomicReference<Map<String, Object>> actual = new AtomicReference<>(Map.of("estado", "SIN_VENTANAS"));

    public Ventana(JdbcTemplate jdbc, Procesador procesador, MonitorCliente monitor, VerificadorCriptografico verificador,
                   Evidencia evidencia, KafkaTemplate<String, String> kafka, ObjectMapper json, Registro registro,
                   MeterRegistry metricas,
                   @Value("${spring.kafka.bootstrap-servers}") String bootstrap,
                   @Value("${orquidea.dias-reintento}") int diasReintento,
                   @Value("${orquidea.ritmo-declaraciones-s}") double ritmo) {
        this.jdbc = jdbc;
        this.procesador = procesador;
        this.monitor = monitor;
        this.verificador = verificador;
        this.evidencia = evidencia;
        this.kafka = kafka;
        this.json = json;
        this.registro = registro;
        this.bootstrap = bootstrap;
        this.diasReintento = diasReintento;
        this.ritmo = ritmo;
        metricas.gauge("orquidea.pendientes", jdbc, j -> {
            try {
                Long n = j.queryForObject("SELECT count(*) FROM operacion.pendiente WHERE estado = 'PENDIENTE'", Long.class);
                return n == null ? 0 : n;
            } catch (RuntimeException e) {
                return Double.NaN;
            }
        });
    }

    @PostMapping("/ventanas")
    public synchronized Map<String, Object> abrir() {
        if ("EN_CURSO".equals(actual.get().get("estado"))) {
            return actual.get();
        }
        int n = numero.incrementAndGet();
        Map<String, Object> estado = new LinkedHashMap<>(Map.of("numero", n, "estado", "EN_CURSO", "inicio", Instant.now().toString()));
        actual.set(estado);
        Thread.ofPlatform().name("ventana-" + n).start(() -> correr(n));
        return estado;
    }

    @GetMapping("/ventanas/actual")
    public Map<String, Object> estado() {
        return actual.get();
    }

    /** Preparación de cada ejecución: descarta los pendientes de la anterior y reinicia la numeración. */
    @PostMapping("/admin/reiniciar")
    public synchronized Map<String, Object> reiniciar() {
        try (KafkaConsumer<String, String> c = consumidor()) {
            List<TopicPartition> parts = particiones(c);
            c.assign(parts);
            Map<TopicPartition, Long> fin = c.endOffsets(parts);
            Map<TopicPartition, OffsetAndMetadata> commit = new HashMap<>();
            fin.forEach((p, o) -> commit.put(p, new OffsetAndMetadata(o)));
            c.commitSync(commit);
        }
        numero.set(0);
        actual.set(Map.of("estado", "SIN_VENTANAS"));
        return Map.of("ok", true);
    }

    private void correr(int n) {
        Instant inicio = Instant.now();
        monitor.consultarYa();
        registro.hecho("ventana.inicio", Map.of("numero", n, "rnic", monitor.estado(AdaptadorRnic.TERCERO)));
        int resueltos = 0, reprogramados = 0, notificados = 0, rechazados = 0, omitidos = 0, liberados = 0;
        Ritmo tope = new Ritmo(ritmo);
        try (KafkaConsumer<String, String> c = consumidor()) {
            List<TopicPartition> parts = particiones(c);
            c.assign(parts);
            Map<TopicPartition, Long> corte = c.endOffsets(parts);
            Map<TopicPartition, OffsetAndMetadata> comprometidos = new HashMap<>();
            for (TopicPartition p : parts) {
                OffsetAndMetadata o = c.committed(java.util.Set.of(p)).get(p);
                c.seek(p, o == null ? 0 : o.offset());
            }
            boolean quedan = true;
            while (quedan) {
                List<ConsumerRecord<String, String>> lote = new ArrayList<>();
                for (ConsumerRecord<String, String> r : c.poll(Duration.ofMillis(500))) {
                    if (r.offset() < corte.get(new TopicPartition(r.topic(), r.partition()))) {
                        lote.add(r);
                    }
                }
                for (ConsumerRecord<String, String> r : lote) {
                    Cola.Pendiente p = json.readValue(r.value(), Cola.Pendiente.class);
                    switch (atender(p, tope)) {
                        case "resuelto" -> resueltos++;
                        case "reprogramado" -> reprogramados++;
                        case "notificado" -> { reprogramados++; notificados++; }
                        case "rechazado" -> rechazados++;
                        case "liberado" -> liberados++;
                        default -> omitidos++;
                    }
                    comprometidos.put(new TopicPartition(r.topic(), r.partition()), new OffsetAndMetadata(r.offset() + 1));
                }
                if (!comprometidos.isEmpty()) {
                    c.commitSync(comprometidos);
                }
                quedan = false;
                for (TopicPartition p : parts) {
                    if (c.position(p) < corte.get(p)) {
                        quedan = true;
                    }
                }
            }
        } catch (Exception e) {
            registro.hecho("ventana.error", Map.of("numero", n, "error", String.valueOf(e.getMessage())));
        }
        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("numero", n);
        fin.put("estado", "TERMINADA");
        fin.put("resueltos", resueltos);
        fin.put("liberados", liberados);
        fin.put("reprogramados", reprogramados);
        fin.put("notificados", notificados);
        fin.put("rechazados", rechazados);
        fin.put("omitidos", omitidos);
        fin.put("duracionMs", Duration.between(inicio, Instant.now()).toMillis());
        registro.hecho("ventana.fin", fin);
        actual.set(fin);
    }

    /** Un pendiente: lo resuelve, lo libera a su carril, lo rechaza o lo reprograma. */
    private String atender(Cola.Pendiente p, Ritmo tope) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT intentos, estado FROM operacion.pendiente WHERE llave = ?", p.llave());
        // Un mensaje viejo (el archivo ya se resolvió o se reprogramó después) no se atiende dos veces.
        if (filas.isEmpty() || !"PENDIENTE".equals(filas.get(0).get("estado"))
                || ((Number) filas.get(0).get("intentos")).intValue() != p.intentos()) {
            return "omitido";
        }
        Cola.ArchivoEnCola a = new Cola.ArchivoEnCola(p.llave(), p.ruta(), p.bytes(), p.registros(), null);
        if ("VERIFICACION_PENDIENTE".equals(p.motivo())) {
            return verificarPendiente(p, a);
        }
        if (monitor.caido(AdaptadorRnic.TERCERO)) {
            return reprogramar(p, a);
        }
        Procesador.Desenlace d = procesador.procesar(a, p.desdeN(), p.intentos(), "reprocesador", 1000, tope);
        return switch (d) {
            case RESUELTO, YA_RESUELTO -> {
                registro.archivo("pendiente.resuelto", p.llave(), Map.of("intentos", p.intentos()));
                yield "resuelto";
            }
            // procesar() ya lo dejó pendiente con el mismo intento: se reprograma con uno más.
            case PENDIENTE -> reprogramar(p, a);
            case OMITIDO -> "omitido";
        };
    }

    private String verificarPendiente(Cola.Pendiente p, Cola.ArchivoEnCola a) {
        Map<String, Object> f = jdbc.queryForMap("SELECT serial, hash, carril, estampa FROM operacion.archivo WHERE llave = ?", p.llave());
        CacheRevocacion cache = verificador.cache();
        if (!cache.vigente() && !cache.refrescarYa()) {
            return reprogramar(p, a);
        }
        CacheRevocacion.Estado e = verificador.revocacion(new BigInteger((String) f.get("serial")));
        if (e == CacheRevocacion.Estado.DESCONOCIDO) {
            return reprogramar(p, a);
        }
        jdbc.update("UPDATE operacion.pendiente SET estado = 'RESUELTO', resuelto_en = clock_timestamp() WHERE llave = ?", p.llave());
        if (e == CacheRevocacion.Estado.REVOCADO) {
            jdbc.update("""
                    UPDATE operacion.archivo SET estado = 'RECHAZADO', causa = 'CERTIFICADO_REVOCADO', resultado = 'RECHAZO',
                           resultado_en = clock_timestamp(), resuelto_por = 'reprocesador'
                    WHERE llave = ? AND resultado IS NULL""", p.llave());
            registro.archivo("archivo.rechazado", p.llave(), Map.of("causa", "CERTIFICADO_REVOCADO", "componente", "reprocesador"));
            return "rechazado";
        }
        Map<String, String> comprobaciones = new LinkedHashMap<>(Map.of("resumen", "ok", "cadena", "ok", "firma", "ok",
                "firmante", "ok", "estampa", "ok", "revocacion", "ok-en-ventana"));
        evidencia.registrar(p.llave(), (String) f.get("hash"),
                new VerificadorCriptografico.Veredicto(VerificadorCriptografico.Estado.VERIFICADO, null, comprobaciones,
                        (String) f.get("serial"), p.llave().split("\\|")[0]), (String) f.get("estampa"));
        jdbc.update("UPDATE operacion.archivo SET estado = 'ACUSADO' WHERE llave = ?", p.llave());
        String carril = (String) f.get("carril");
        Cola.publicar(kafka, json, carril, p.llave(), new Cola.ArchivoEnCola(p.llave(), p.ruta(), p.bytes(), p.registros(), carril));
        registro.archivo("verificacion.completada", p.llave(), Map.of("carril", carril, "intentos", p.intentos()));
        return "liberado";
    }

    private String reprogramar(Cola.Pendiente p, Cola.ArchivoEnCola a) {
        int intentos = p.intentos() + 1;
        int desde = jdbc.queryForObject("SELECT desde_n FROM operacion.pendiente WHERE llave = ?", Integer.class, p.llave());
        procesador.dejarPendiente(a, p.motivo(), desde, intentos, "reprocesador");
        registro.archivo("pendiente.reprogramado", p.llave(), Map.of("intentos", intentos, "motivo", p.motivo()));
        if (intentos == diasReintento) {
            jdbc.update("UPDATE operacion.pendiente SET notificado = true WHERE llave = ?", p.llave());
            registro.archivo("remitente.notificado", p.llave(), Map.of("intentos", intentos, "motivo", p.motivo()));
            return "notificado";
        }
        return "reprogramado";
    }

    private KafkaConsumer<String, String> consumidor() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "reprocesador");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 200);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private static List<TopicPartition> particiones(KafkaConsumer<String, String> c) {
        List<TopicPartition> parts = new ArrayList<>();
        c.partitionsFor(Cola.PENDIENTES).forEach(i -> parts.add(new TopicPartition(i.topic(), i.partition())));
        return parts;
    }
}
