package co.mati.reto4.simuladores;

import co.mati.reto4.comun.Formato;
import co.mati.reto4.comun.Personas;
import co.mati.reto4.comun.Pki;
import co.mati.reto4.comun.Registro;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El remitente simulado: arma el XML, lo firma con la llave del remitente, le pone
 * la estampa de la ECD y lo envía a la recepción. Antes de enviar anota
 * {@code archivo.enviado} con lo que se espera de él: es el oráculo de la
 * conciliación de ASR-D01 (qué resultado habría tenido con el RNIC disponible).
 *
 * <p>Casos del catálogo criptográfico de S-1 ({@code caso}): {@code firma-alterada}
 * (una declaración cambia después de firmar), {@code resumen-recalculado} (cambia y
 * se recalcula el resumen sin poder volver a firmar), {@code firma-envuelta}
 * (declaraciones sin firmar después de la firma), {@code firmante-distinto},
 * {@code revocado}, {@code ca-no-aceptada}, {@code estampa-desfasada},
 * {@code estampa-vencida} y {@code entidad-externa} (XXE).
 */
@RestController
@ConditionalOnProperty(name = "rol", havingValue = "remitente")
public class Remitente {

    private static final int DETALLE = 345;
    private static final String RELLENO = ("Compra de divisas por importacion de bienes, numeral cambiario 1000, "
            + "canalizada por el mercado cambiario. ").repeat(6);
    private static final int GUARDADOS = 6000;

    private record Firmante(PrivateKey llave, X509Certificate crt) {}

    private record Cuerpo(byte[] bytes, int invalidas) {}

    private final Registro registro;
    private final JdbcTemplate jdbc;
    private final String recepcion;
    private final double tasaInvalidas;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Firmante> firmantes = new ConcurrentHashMap<>();
    private final Map<Integer, Cuerpo> masivos = new ConcurrentHashMap<>();
    private final Map<String, byte[]> enviados = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > GUARDADOS;
        }
    });
    private final AtomicLong consecutivo = new AtomicLong();
    private final PrivateKey tsa;
    private final ExecutorService envios = Executors.newVirtualThreadPerTaskExecutor();

    public Remitente(Registro registro, JdbcTemplate jdbc, @Value("${RECEPCION_URL:http://localhost:8081}") String recepcion,
                     @Value("${INVALIDAS:0.05}") double tasaInvalidas) {
        this.registro = registro;
        this.jdbc = jdbc;
        this.recepcion = recepcion;
        this.tasaInvalidas = tasaInvalidas;
        this.tsa = Pki.llave("tsa");
    }

    @PostMapping("/enviar")
    public Map<String, Object> enviar(@RequestParam(defaultValue = "pequeno") String tipo,
                                      @RequestParam(defaultValue = "manual") String corrida,
                                      @RequestParam(defaultValue = "0") int registros,
                                      @RequestParam(defaultValue = "valido") String caso) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = registros > 0 ? registros : "masivo".equals(tipo) ? 100_000 : rnd.nextInt(1, 51);
        String remitente = String.format("R%03d", rnd.nextInt(1, 11));
        String fecha = LocalDate.now(ZoneId.of("America/Bogota")).toString();
        String llave = Formato.llave(remitente, fecha, corrida, String.format("%07d", consecutivo.incrementAndGet()));
        String[] partes = llave.split("\\|");

        byte[] encabezado = Formato.encabezado(remitente, fecha, partes[2], partes[3], n);
        Cuerpo cuerpo = "masivo".equals(tipo) ? masivo(n) : cuerpo(n, new SplittableRandom(rnd.nextLong()));
        byte[] resumen = Formato.resumen(encabezado, cuerpo.bytes());

        String quienFirma = switch (caso) {
            case "firmante-distinto" -> remitente.equals("R003") ? "R004" : "R003";
            case "revocado" -> "R900";
            case "ca-no-aceptada" -> "R001-intruso";
            default -> remitente;
        };
        if (caso.equals("revocado") || caso.equals("ca-no-aceptada")) {
            // El encabezado nombra al dueño del certificado: lo que falla es la revocación o la cadena.
            String r2 = caso.equals("revocado") ? "R900" : "R001";
            llave = Formato.llave(r2, fecha, partes[2], partes[3]);
            encabezado = Formato.encabezado(r2, fecha, partes[2], partes[3], n);
            resumen = Formato.resumen(encabezado, cuerpo.bytes());
        }
        Instant tiempo = switch (caso) {
            case "estampa-desfasada" -> Instant.now().plus(Duration.ofHours(1));
            case "estampa-vencida" -> Instant.now().minus(Duration.ofHours(48));
            default -> Instant.now();
        };
        Firmante f = firmante(quienFirma);
        byte[] firma = Formato.firma(resumen, f.llave(), f.crt(), tsa, tiempo);
        byte[] cuerpoFinal = cuerpo.bytes();
        List<byte[]> doc = new ArrayList<>(List.of(encabezado, cuerpoFinal, firma));

        switch (caso) {
            case "firma-alterada" -> doc.set(1, alterar(cuerpoFinal));
            case "resumen-recalculado" -> {
                byte[] alterado = alterar(cuerpoFinal);
                byte[] otro = Formato.resumen(encabezado, alterado);
                String fs = new String(firma, StandardCharsets.UTF_8).replace(Formato.b64(resumen), Formato.b64(otro));
                doc.set(1, alterado);
                doc.set(2, fs.getBytes(StandardCharsets.UTF_8));
            }
            case "firma-envuelta" -> {
                String fs = new String(firma, StandardCharsets.UTF_8).replace("</archivo>\n",
                        "<declaraciones>" + Formato.declaracion(1, Personas.cedula(1), Personas.nombre(Personas.cedula(1)), 999_999_999, "x")
                                + "</declaraciones></archivo>\n");
                doc.set(2, fs.getBytes(StandardCharsets.UTF_8));
            }
            case "entidad-externa" -> {
                String e = new String(encabezado, StandardCharsets.UTF_8).replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE archivo [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>\n");
                doc.set(0, e.getBytes(StandardCharsets.UTF_8));
            }
            default -> { }
        }

        String esperado = !"valido".equals(caso) ? "RECHAZO_CRIPTOGRAFICO"
                : cuerpo.invalidas() == 0 ? "TOTAL" : cuerpo.invalidas() == n ? "RECHAZO" : "PARCIAL";
        long bytes = doc.stream().mapToLong(b -> b.length).sum();
        registro.archivo("archivo.enviado", llave, Map.of("tipo", tipo, "registros", n, "invalidas", cuerpo.invalidas(),
                "esperado", esperado, "caso", caso, "bytes", bytes, "reenvio", false));
        if (!"masivo".equals(tipo) && "valido".equals(caso)) {
            enviados.put(llave, concatenar(doc));
        }
        return enviarBytes(llave, doc);
    }

    /** Envía {@code n} archivos con hasta 8 en paralelo y resume las respuestas (catálogo, D-5). */
    @PostMapping("/enviar-lote")
    public Map<String, Object> enviarLote(@RequestParam(defaultValue = "pequeno") String tipo,
                                          @RequestParam(defaultValue = "manual") String corrida,
                                          @RequestParam(defaultValue = "1") int n,
                                          @RequestParam(defaultValue = "valido") String caso) throws Exception {
        List<Future<Map<String, Object>>> fs = new ArrayList<>();
        java.util.concurrent.Semaphore s = new java.util.concurrent.Semaphore(8);
        for (int i = 0; i < n; i++) {
            fs.add(envios.submit(() -> {
                s.acquire();
                try {
                    return enviar(tipo, corrida, 0, caso);
                } finally {
                    s.release();
                }
            }));
        }
        return resumir(fs);
    }

    /** D-5: reenvía con la misma llave (y los mismos bytes) {@code n} archivos que siguen pendientes. */
    @PostMapping("/reenviar")
    public Map<String, Object> reenviar(@RequestParam(defaultValue = "50") int n) throws Exception {
        List<String> candidatos;
        synchronized (enviados) {
            candidatos = new ArrayList<>(enviados.keySet());
        }
        List<String> pendientes = jdbc.queryForList(
                "SELECT llave FROM operacion.pendiente WHERE estado = 'PENDIENTE' AND llave = ANY (?)", String.class,
                (Object) candidatos.toArray(new String[0]));
        Collections.shuffle(pendientes);
        List<Future<Map<String, Object>>> fs = new ArrayList<>();
        for (String llave : pendientes.subList(0, Math.min(n, pendientes.size()))) {
            byte[] doc = enviados.get(llave);
            registro.archivo("archivo.enviado", llave, Map.of("tipo", "pequeno", "caso", "reenvio", "esperado", "DUPLICADO",
                    "bytes", doc.length, "reenvio", true, "registros", 0, "invalidas", 0));
            fs.add(envios.submit(() -> enviarBytes(llave, List.of(doc))));
        }
        return resumir(fs);
    }

    private Map<String, Object> enviarBytes(String llave, List<byte[]> doc) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(recepcion + "/archivos"))
                    .timeout(Duration.ofMinutes(10)).header("Content-Type", "application/xml").header("X-Correlacion", llave)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(doc)).build(), HttpResponse.BodyHandlers.ofString());
            return Map.of("llave", llave, "status", r.statusCode(), "respuesta", r.body());
        } catch (Exception e) {
            registro.archivo("archivo.envio_fallido", llave, Map.of("error", String.valueOf(e.getMessage())));
            return Map.of("llave", llave, "status", 0, "error", String.valueOf(e.getMessage()));
        }
    }

    private static Map<String, Object> resumir(List<Future<Map<String, Object>>> fs) throws Exception {
        Map<String, Integer> porStatus = new java.util.TreeMap<>();
        for (Future<Map<String, Object>> f : fs) {
            porStatus.merge(String.valueOf(f.get().get("status")), 1, Integer::sum);
        }
        return Map.of("enviados", fs.size(), "porStatus", porStatus);
    }

    private Cuerpo masivo(int n) {
        return masivos.computeIfAbsent(n, k -> cuerpo(k, new SplittableRandom(42)));
    }

    private Cuerpo cuerpo(int n, SplittableRandom rnd) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(n * 520 + 64);
        out.writeBytes("<declaraciones>\n".getBytes(StandardCharsets.UTF_8));
        int invalidas = 0;
        for (int i = 1; i <= n; i++) {
            long cedula = Personas.cedula(rnd.nextInt(Personas.TOTAL));
            boolean mala = rnd.nextDouble() < tasaInvalidas;
            invalidas += mala ? 1 : 0;
            String nombre = mala ? Personas.nombreErrado(cedula) : Personas.nombre(cedula);
            out.writeBytes(Formato.declaracion(i, cedula, nombre, rnd.nextLong(1_000, 500_000_000),
                    RELLENO.substring(0, DETALLE)).getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes("</declaraciones>\n".getBytes(StandardCharsets.UTF_8));
        return new Cuerpo(out.toByteArray(), invalidas);
    }

    /** Cambia un dígito del monto de la primera declaración. */
    private static byte[] alterar(byte[] cuerpo) {
        byte[] c = Arrays.copyOf(cuerpo, cuerpo.length);
        String s = new String(c, 0, Math.min(c.length, 600), StandardCharsets.UTF_8);
        int i = s.indexOf("<monto>") + "<monto>".length();
        c[i] = (byte) (c[i] == '9' ? '1' : c[i] + 1);
        return c;
    }

    private static byte[] concatenar(List<byte[]> partes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        partes.forEach(out::writeBytes);
        return out.toByteArray();
    }

    private Firmante firmante(String nombre) {
        return firmantes.computeIfAbsent(nombre, k -> new Firmante(Pki.llave(k), Pki.certificado(k)));
    }
}
