package co.mati.reto4.carril;

import co.mati.reto4.comun.Cola;
import co.mati.reto4.comun.Procesador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Un carril de procesamiento (ADR-005, bulkhead). La misma imagen corre dos veces:
 * el carril A consume {@code carril-a} (pequeños) y el carril B consume
 * {@code carril-b} (medianos y masivos). Cada uno tiene su contenedor, su límite de
 * CPU y su pool: tantos hilos consumidores como {@code CONCURRENCIA}, cada uno con
 * un archivo a la vez. En el control sin carriles (L-3), la recepción publica todo
 * en {@code carril-a} y el carril A atiende también a los masivos.
 */
@SpringBootApplication(scanBasePackages = "co.mati.reto4")
public class CarrilApp {
    public static void main(String[] args) {
        SpringApplication.run(CarrilApp.class, args);
    }

    @Component
    static class Consumidor {

        private final Procesador procesador;
        private final ObjectMapper json;
        private final String componente;
        private final int lote;

        Consumidor(Procesador procesador, ObjectMapper json, @Value("${orquidea.carril}") String carril,
                   @Value("${orquidea.lote}") int lote) {
            this.procesador = procesador;
            this.json = json;
            this.componente = "carril-" + carril.toLowerCase();
            this.lote = lote;
        }

        @KafkaListener(topics = "${orquidea.topico}", groupId = "${orquidea.topico}", concurrency = "${orquidea.concurrencia}")
        void consumir(ConsumerRecord<String, String> r) throws Exception {
            Cola.ArchivoEnCola a = json.readValue(r.value(), Cola.ArchivoEnCola.class);
            procesador.procesar(a, 1, 0, componente, lote, null);
        }
    }
}
