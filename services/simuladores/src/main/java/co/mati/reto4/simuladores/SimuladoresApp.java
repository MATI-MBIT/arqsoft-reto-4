package co.mati.reto4.simuladores;

import co.mati.reto4.comun.Personas;
import co.mati.reto4.comun.Pki;
import co.mati.reto4.comun.Registro;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los simuladores del experimento. Una imagen, tres papeles según {@code ROL}:
 * <ul>
 *   <li>{@code rnic}: el RNIC, que cruza cédula contra nombre. Basta un tercero:
 *       STM, ANTA y MAMC se comportan igual ante una caída.</li>
 *   <li>{@code ecd}: la ECD de prueba, que publica su CRL.</li>
 *   <li>{@code remitente}: los IMC y titulares, que arman, firman y envían archivos.</li>
 * </ul>
 * El RNIC y la ECD se alcanzan a través de Toxiproxy, que es quien los corta o les
 * agrega latencia; el monitor y los adaptadores no saben que hay un proxy.
 */
@SpringBootApplication
@Import(Registro.class)
public class SimuladoresApp {
    public static void main(String[] args) {
        SpringApplication.run(SimuladoresApp.class, args);
    }

    @RestController
    @ConditionalOnProperty(name = "rol", havingValue = "rnic")
    static class Rnic {

        record Consulta(long c, String n) {}

        @GetMapping("/eco")
        String eco() {
            return "rnic";
        }

        @PostMapping("/personas/verificar")
        boolean[] verificar(@RequestBody List<Consulta> lote) {
            boolean[] r = new boolean[lote.size()];
            for (int i = 0; i < r.length; i++) {
                Consulta q = lote.get(i);
                r[i] = Personas.existe(q.c()) && Personas.nombre(q.c()).equals(q.n());
            }
            return r;
        }
    }

    @RestController
    @ConditionalOnProperty(name = "rol", havingValue = "ecd")
    static class Ecd {

        @GetMapping("/eco")
        String eco() {
            return "ecd";
        }

        @GetMapping(value = "/crl", produces = "application/pkix-crl")
        ResponseEntity<byte[]> crl() throws IOException {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/pkix-crl"))
                    .body(Files.readAllBytes(Pki.dir().resolve("crl.der")));
        }

        @GetMapping("/estado")
        Map<String, Object> estado() {
            return Map.of("rol", "ecd");
        }
    }
}
