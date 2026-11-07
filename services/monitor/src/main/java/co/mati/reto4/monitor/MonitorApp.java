package co.mati.reto4.monitor;

import co.mati.reto4.comun.Registro;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Monitor de terceros (ADR-002: DIS-01 ping/echo, DIS-03 monitor, DIS-04 timeout).
 *
 * <p>Sondea a cada tercero cada {@code SONDEO_MS} con un timeout de
 * {@code SONDEO_TIMEOUT_MS}: un tercero lento cuenta como caído. Con {@code SONDEO_N}
 * fallos seguidos lo declara CAIDO; con el primer eco después, DE_VUELTA; con el
 * siguiente, DISPONIBLE. Una llamada vencida que reporta un adaptador cuenta como un
 * fallo más, así la caída se puede declarar antes del siguiente sondeo.
 */
@SpringBootApplication(scanBasePackages = {"co.mati.reto4.monitor"})
@org.springframework.context.annotation.Import(Registro.class)
public class MonitorApp {
    public static void main(String[] args) {
        SpringApplication.run(MonitorApp.class, args);
    }

    @RestController
    static class Monitor {

        private final Map<String, String> urls = new LinkedHashMap<>();
        private final Map<String, String> estados = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> fallos = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> nivel = new ConcurrentHashMap<>();
        private final Registro registro;
        private final int n;
        private final Duration timeout;
        private final HttpClient http;
        private final ScheduledExecutorService sondeo;

        Monitor(@Value("${orquidea.terceros}") String terceros, @Value("${orquidea.sondeo-ms}") long periodoMs,
                @Value("${orquidea.sondeo-timeout-ms}") long timeoutMs, @Value("${orquidea.sondeo-n}") int n,
                Registro registro, MeterRegistry metricas) {
            this.registro = registro;
            this.n = n;
            this.timeout = Duration.ofMillis(timeoutMs);
            this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
            for (String par : terceros.split(",")) {
                String[] kv = par.split("=", 2);
                urls.put(kv[0].trim(), kv[1].trim());
                estados.put(kv[0].trim(), "DISPONIBLE");
                fallos.put(kv[0].trim(), new AtomicInteger());
                AtomicInteger g = new AtomicInteger(0);
                nivel.put(kv[0].trim(), g);
                // 0 disponible · 1 de vuelta · 2 caído
                metricas.gauge("orquidea.tercero.estado", java.util.List.of(io.micrometer.core.instrument.Tag.of("tercero", kv[0].trim())), g);
            }
            sondeo = Executors.newScheduledThreadPool(urls.size(), Thread.ofPlatform().name("sondeo").daemon().factory());
            for (String t : urls.keySet()) {
                sondeo.scheduleAtFixedRate(() -> sondear(t), periodoMs, periodoMs, TimeUnit.MILLISECONDS);
            }
            registro.hecho("monitor.arranque", Map.of("periodoMs", periodoMs, "timeoutMs", timeoutMs, "n", n));
        }

        private void sondear(String tercero) {
            boolean eco;
            try {
                HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(urls.get(tercero) + "/eco"))
                        .timeout(timeout).GET().build(), HttpResponse.BodyHandlers.discarding());
                eco = r.statusCode() == 200;
            } catch (Exception e) {
                eco = false;
            }
            if (eco) {
                exito(tercero);
            } else {
                fallo(tercero, "sondeo");
            }
        }

        private synchronized void exito(String tercero) {
            fallos.get(tercero).set(0);
            switch (estados.get(tercero)) {
                case "CAIDO" -> cambiar(tercero, "DE_VUELTA", 1);
                case "DE_VUELTA" -> cambiar(tercero, "DISPONIBLE", 0);
                default -> { }
            }
        }

        private synchronized void fallo(String tercero, String origen) {
            int f = fallos.get(tercero).incrementAndGet();
            if (f >= n && !"CAIDO".equals(estados.get(tercero))) {
                cambiar(tercero, "CAIDO", 2);
                registro.hecho("tercero.caido_por", Map.of("tercero", tercero, "origen", origen, "fallos", f));
            }
        }

        private void cambiar(String tercero, String estado, int g) {
            estados.put(tercero, estado);
            nivel.get(tercero).set(g);
            registro.hecho("tercero." + estado.toLowerCase(), Map.of("tercero", tercero, "estado", estado));
        }

        @GetMapping("/estado")
        Map<String, String> estado() {
            return estados;
        }

        @PostMapping("/reportes/{tercero}")
        Map<String, Object> reporte(@PathVariable String tercero) {
            if (urls.containsKey(tercero)) {
                fallo(tercero, "adaptador");
            }
            return Map.of("tercero", tercero, "estado", estados.getOrDefault(tercero, "?"));
        }
    }
}
