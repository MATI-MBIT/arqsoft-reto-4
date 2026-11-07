package co.mati.reto4.comun;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * La cola durable de ADR-001 (Kafka en modo KRaft, C-01): los tópicos y los
 * mensajes que circulan por ella.
 *
 * <ul>
 *   <li>{@code carril-a} y {@code carril-b}: un archivo acusado, en el tópico de su carril (ADR-005).</li>
 *   <li>{@code pendientes}: lo que espera la ventana nocturna (ADR-003 y ADR-007).</li>
 * </ul>
 */
@Configuration
public class Cola {

    public static final String CARRIL_A = "carril-a";
    public static final String CARRIL_B = "carril-b";
    public static final String PENDIENTES = "pendientes";

    /** Un archivo acusado y custodiado, listo para su carril. */
    public record ArchivoEnCola(String llave, String ruta, long bytes, int registros, String carril) {}

    /** Un archivo que espera la ventana: por un tercero caído o por la revocación sin verificar. */
    public record Pendiente(String llave, String motivo, int desdeN, int intentos, String ruta, long bytes, int registros) {}

    @Bean
    NewTopic carrilA() {
        return TopicBuilder.name(CARRIL_A).partitions(12).replicas(1).build();
    }

    @Bean
    NewTopic carrilB() {
        return TopicBuilder.name(CARRIL_B).partitions(12).replicas(1).build();
    }

    @Bean
    NewTopic pendientes() {
        return TopicBuilder.name(PENDIENTES).partitions(3).replicas(1).build();
    }

    /** Publica y espera la confirmación del bróker: lo que se acusa ya está en la cola. */
    public static void publicar(KafkaTemplate<String, String> kafka, ObjectMapper json, String topico, String llave, Object mensaje) {
        try {
            kafka.send(topico, llave, json.writeValueAsString(mensaje)).get(10, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException("el bróker no confirmó el mensaje de " + llave, e);
        }
    }
}
