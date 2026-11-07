package co.mati.reto4.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lo que cada consumidor sabe del monitor de terceros (ADR-002): consulta su
 * estado cada 200 ms y le reporta las llamadas vencidas.
 *
 * <p>Si el monitor no responde, se conserva el último estado conocido: el timeout
 * de cada adaptador sigue protegiendo (consecuencia de ADR-002).
 */
@Component
public final class MonitorCliente {

    private final String url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
    private final ObjectMapper json;
    private final Map<String, String> estados = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sondeo =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("monitor-cliente").daemon().factory());

    public MonitorCliente(@Value("${orquidea.monitor-url:}") String url, ObjectMapper json) {
        this.url = url;
        this.json = json;
        if (!url.isBlank()) {
            sondeo.scheduleWithFixedDelay(this::consultar, 0, 200, TimeUnit.MILLISECONDS);
        }
    }

    /** Caído para efectos de llamar: CAIDO. DE_VUELTA y DISPONIBLE se pueden llamar. */
    public boolean caido(String tercero) {
        return "CAIDO".equals(estados.get(tercero));
    }

    public String estado(String tercero) {
        return estados.getOrDefault(tercero, "DESCONOCIDO");
    }

    /** Pide el estado ya, sin esperar el próximo sondeo (el reprocesador lo usa antes de cada ventana). */
    public void consultarYa() {
        consultar();
    }

    @SuppressWarnings("unchecked")
    private void consultar() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url + "/estado"))
                    .timeout(Duration.ofMillis(500)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                estados.putAll(json.readValue(r.body(), Map.class));
            }
        } catch (Exception e) {
            // se conserva el último estado conocido
        }
    }

    public void reportarVencida(String tercero) {
        if (url.isBlank()) {
            return;
        }
        http.sendAsync(HttpRequest.newBuilder(URI.create(url + "/reportes/" + tercero))
                .timeout(Duration.ofMillis(500)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
    }
}
