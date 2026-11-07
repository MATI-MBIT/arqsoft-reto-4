package co.mati.reto4.comun;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El verificador criptográfico de ADR-006 con la caché de revocación de ADR-007.
 * Solo existe donde se configura la URL de la ECD: la recepción y el reprocesador.
 *
 * <p>Los parámetros que BCR no ha fijado (desfase y antigüedad de la estampa,
 * vigencia de la copia de la CRL) son variables de entorno con valores de prueba.
 */
@Configuration
@ConditionalOnProperty("orquidea.ecd-url")
public class ConfigVerificacion {

    @Bean
    CacheRevocacion cacheRevocacion(@Value("${orquidea.ecd-url}") String ecd,
                                    @Value("${orquidea.revocacion-vigencia-s:3600}") long vigenciaS,
                                    @Value("${orquidea.ecd-timeout-ms:500}") long timeoutMs,
                                    Registro registro) {
        return new CacheRevocacion(ecd + "/crl", Duration.ofSeconds(vigenciaS), Duration.ofMillis(timeoutMs), registro);
    }

    @Bean
    VerificadorCriptografico verificador(CacheRevocacion cache,
                                         @Value("${orquidea.estampa-desfase-s:300}") long desfaseS,
                                         @Value("${orquidea.estampa-antiguedad-h:24}") long antiguedadH) {
        return new VerificadorCriptografico(List.of(Pki.certificado("raiz")), Pki.certificado("tsa"), cache,
                Duration.ofSeconds(desfaseS), Duration.ofHours(antiguedadH));
    }

    @Bean
    Evidencia evidencia(org.springframework.jdbc.core.JdbcTemplate jdbc, Registro registro,
                        com.fasterxml.jackson.databind.ObjectMapper json) {
        return new Evidencia(jdbc, registro, json);
    }
}
