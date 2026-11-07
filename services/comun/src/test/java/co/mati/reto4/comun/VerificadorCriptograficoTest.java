package co.mati.reto4.comun;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * El camino criptográfico de punta a punta, sin contenedores: el remitente firma,
 * el flujo custodiado resume en streaming, el lector valida la estructura y el
 * verificador decide. Requiere la PKI de prueba (make pki).
 */
class VerificadorCriptograficoTest {

    private static HttpServer ecd;
    private static VerificadorCriptografico verificador;

    @BeforeAll
    static void arrancar() throws Exception {
        byte[] crl = Files.readAllBytes(Pki.dir().resolve("crl.der"));
        ecd = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ecd.createContext("/crl", x -> {
            x.sendResponseHeaders(200, crl.length);
            x.getResponseBody().write(crl);
            x.close();
        });
        ecd.start();
        CacheRevocacion cache = new CacheRevocacion("http://127.0.0.1:" + ecd.getAddress().getPort() + "/crl",
                Duration.ofHours(1), Duration.ofMillis(500), null);
        verificador = new VerificadorCriptografico(List.of(Pki.certificado("raiz")), Pki.certificado("tsa"), cache,
                Duration.ofMinutes(5), Duration.ofHours(24));
    }

    @AfterAll
    static void parar() {
        ecd.stop(0);
    }

    private static byte[] documento(String remitente, String firmante, Instant estampa, int registros) {
        byte[] enc = Formato.encabezado(remitente, "2026-11-07", "prueba", "0000001", registros);
        StringBuilder sb = new StringBuilder("<declaraciones>\n");
        for (int i = 1; i <= registros; i++) {
            long c = Personas.cedula(i);
            sb.append(Formato.declaracion(i, c, Personas.nombre(c), 1000 + i, "detalle"));
        }
        byte[] cuerpo = sb.append("</declaraciones>\n").toString().getBytes(StandardCharsets.UTF_8);
        byte[] firma = Formato.firma(Formato.resumen(enc, cuerpo), Pki.llave(firmante), Pki.certificado(firmante),
                Pki.llave("tsa"), estampa);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(enc);
        out.writeBytes(cuerpo);
        out.writeBytes(firma);
        return out.toByteArray();
    }

    private static String verificar(byte[] doc) throws Exception {
        ByteArrayOutputStream custodia = new ByteArrayOutputStream();
        FlujoCustodiado f = new FlujoCustodiado(new ByteArrayInputStream(doc), custodia);
        LectorArchivo.Contenido c;
        try {
            c = LectorArchivo.leer(f, null);
        } catch (ArchivoInvalido e) {
            return e.causa();
        }
        if (f.drenar()) {
            return "ESTRUCTURA_INVALIDA";
        }
        assertArrayEquals(doc, custodia.toByteArray(), "la custodia guarda los mismos bytes");
        VerificadorCriptografico.Veredicto v = verificador.verificar(c, f.resumen());
        return v.estado() == VerificadorCriptografico.Estado.VERIFICADO ? "VERIFICADO" : v.causa();
    }

    @Test
    void archivoValidoSeVerifica() throws Exception {
        assertEquals("VERIFICADO", verificar(documento("R001", "R001", Instant.now(), 30)));
    }

    @Test
    void elResumenEnStreamingEsElDeLosBytesFirmados() throws Exception {
        byte[] doc = documento("R002", "R002", Instant.now(), 5);
        String s = new String(doc, StandardCharsets.UTF_8);
        byte[] prefijo = s.substring(0, s.indexOf("<firma>")).getBytes(StandardCharsets.UTF_8);
        FlujoCustodiado f = new FlujoCustodiado(new ByteArrayInputStream(doc), null);
        LectorArchivo.leer(f, null);
        f.drenar();
        assertArrayEquals(Formato.resumen(prefijo), f.resumen());
    }

    @Test
    void catalogoCriptografico() throws Exception {
        byte[] valido = documento("R001", "R001", Instant.now(), 10);
        String s = new String(valido, StandardCharsets.UTF_8);

        String alterado = s.replaceFirst("<monto>1001</monto>", "<monto>9001</monto>");
        assertEquals("RESUMEN_NO_COINCIDE", verificar(alterado.getBytes(StandardCharsets.UTF_8)));

        String envuelto = s.replace("</firma></archivo>", "</firma><declaraciones></declaraciones></archivo>");
        assertEquals("ESTRUCTURA_INVALIDA", verificar(envuelto.getBytes(StandardCharsets.UTF_8)));

        String xxe = s.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE archivo [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>\n");
        assertEquals("ESTRUCTURA_INVALIDA", verificar(xxe.getBytes(StandardCharsets.UTF_8)));

        String comentario = s.replace("<declaraciones>", "<!-- <firma> --><declaraciones>");
        assertEquals("ESTRUCTURA_INVALIDA", verificar(comentario.getBytes(StandardCharsets.UTF_8)));

        assertEquals("FIRMANTE_NO_ES_REMITENTE", verificar(documento("R001", "R003", Instant.now(), 3)));
        assertEquals("CERTIFICADO_REVOCADO", verificar(documento("R900", "R900", Instant.now(), 3)));
        assertEquals("CADENA_NO_ACEPTADA", verificar(documento("R001", "R001-intruso", Instant.now(), 3)));
        assertEquals("ESTAMPA_DESFASADA", verificar(documento("R001", "R001", Instant.now().plusSeconds(3600), 3)));
        assertEquals("ESTAMPA_VENCIDA", verificar(documento("R001", "R001", Instant.now().minusSeconds(48 * 3600), 3)));
    }
}
