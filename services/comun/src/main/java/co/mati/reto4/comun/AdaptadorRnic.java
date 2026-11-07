package co.mati.reto4.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El adaptador del RNIC: cruza cédula contra nombre por lotes. Vence un timeout en
 * cada llamada (DIS-04) y reporta la llamada vencida al monitor, así la lentitud
 * cuenta como caída (ADR-002). Nunca deja un hilo esperando más que el timeout.
 */
@Component
public final class AdaptadorRnic {

    public static final String TERCERO = "rnic";

    /** El tercero no respondió a tiempo: la declaración queda pendiente, no se rechaza. */
    public static final class TerceroNoDisponible extends RuntimeException {
        public TerceroNoDisponible(String detalle) {
            super(detalle, null, false, false);
        }
    }

    private final String url;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json;
    private final MonitorCliente monitor;
    private final Registro registro;

    public AdaptadorRnic(@Value("${orquidea.rnic-url:http://localhost:8666}") String url,
                         @Value("${orquidea.rnic-timeout-ms:1000}") long timeoutMs,
                         ObjectMapper json, MonitorCliente monitor, Registro registro) {
        this.url = url;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.json = json;
        this.monitor = monitor;
        this.registro = registro;
    }

    /** Devuelve, por cada declaración, si la cédula corresponde al nombre. */
    public boolean[] verificar(String llave, List<LectorArchivo.Declaracion> lote) {
        List<Map<String, Object>> cuerpo = new ArrayList<>(lote.size());
        for (LectorArchivo.Declaracion d : lote) {
            cuerpo.add(Map.of("c", d.cedula(), "n", d.nombre()));
        }
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url + "/personas/verificar"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + r.statusCode());
            }
            return json.readValue(r.body(), boolean[].class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TerceroNoDisponible("interrumpido");
        } catch (Exception e) {
            monitor.reportarVencida(TERCERO);
            registro.archivo("adaptador.vencida", llave, Map.of("tercero", TERCERO, "error", String.valueOf(e.getClass().getSimpleName())));
            throw new TerceroNoDisponible(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
