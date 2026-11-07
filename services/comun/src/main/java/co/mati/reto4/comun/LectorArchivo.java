package co.mati.reto4.comun;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Lector en streaming del archivo, sin DTD ni entidades externas (validar la
 * entrada, SEG-08). Recorre una sola vez el documento con una máquina de estados
 * estricta: encabezado, declaraciones y firma, en ese orden y nada más. Rechaza
 * DTD, comentarios, instrucciones de proceso, CDATA, elementos desconocidos y
 * cualquier cosa después de la firma, que es como se cierra la firma envuelta.
 *
 * <p>Nunca arma el árbol: la memoria no crece con el tamaño del archivo.
 */
public final class LectorArchivo {

    private static final XMLInputFactory FABRICA = fabrica();

    public record Declaracion(int n, long cedula, String nombre, long monto) {}

    public record Contenido(String remitente, String fecha, String lote, String consecutivo, int registros,
                            int declaraciones, String resumen, String valor, String certificado,
                            String tiempoEstampa, String valorEstampa) {
        public String llave() {
            return Formato.llave(remitente, fecha, lote, consecutivo);
        }
    }

    private LectorArchivo() {}

    /** Lee el documento; si {@code alDeclarar} no es null, le entrega cada declaración a medida que la lee. */
    public static Contenido leer(InputStream in, Consumer<Declaracion> alDeclarar) {
        try {
            // El lector no cierra el flujo que no abrió: quien llama todavía lo drena.
            InputStream sinCierre = new java.io.FilterInputStream(in) {
                @Override
                public void close() {
                }
            };
            XMLStreamReader r = FABRICA.createXMLStreamReader(sinCierre, "UTF-8");
            try {
                return recorrer(r, alDeclarar);
            } finally {
                r.close();
            }
        } catch (XMLStreamException | NumberFormatException | NullPointerException e) {
            // Lo que no es del formato. Cualquier otra excepción (p. ej. la de un
            // tercero caído que lanza quien consume las declaraciones) sigue su curso.
            throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", e.getMessage());
        }
    }

    private static Contenido recorrer(XMLStreamReader r, Consumer<Declaracion> alDeclarar) throws XMLStreamException {
        abrir(r, "archivo");
        abrir(r, "encabezado");
        Map<String, String> enc = campos(r, "encabezado", "remitente", "fecha", "lote", "consecutivo", "registros");
        abrir(r, "declaraciones");
        int cuenta = 0;
        while (true) {
            int ev = siguiente(r);
            if (ev == XMLStreamConstants.END_ELEMENT) {
                exigir(r, "declaraciones");
                break;
            }
            exigirInicio(r, ev, "d");
            int n = Integer.parseInt(r.getAttributeValue(null, "n"));
            Map<String, String> d = campos(r, "d", "cedula", "nombre", "monto", "moneda", "detalle");
            cuenta++;
            if (n != cuenta) {
                throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "declaración fuera de orden: " + n);
            }
            if (alDeclarar != null) {
                alDeclarar.accept(new Declaracion(n, Long.parseLong(d.get("cedula")), d.get("nombre"), Long.parseLong(d.get("monto"))));
            }
        }
        abrir(r, "firma");
        Map<String, String> firma = new HashMap<>();
        for (String campo : new String[] {"resumen", "valor", "certificado"}) {
            abrir(r, campo);
            firma.put(campo, texto(r));
        }
        abrir(r, "estampa");
        Map<String, String> estampa = campos(r, "estampa", "tiempo", "valor");
        cerrar(r, "firma");
        cerrar(r, "archivo");
        if (siguiente(r) != XMLStreamConstants.END_DOCUMENT) {
            throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "contenido después del documento");
        }
        int registros = Integer.parseInt(enc.get("registros"));
        if (registros != cuenta) {
            throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "el encabezado dice " + registros + " registros y hay " + cuenta);
        }
        return new Contenido(enc.get("remitente"), enc.get("fecha"), enc.get("lote"), enc.get("consecutivo"), registros,
                cuenta, firma.get("resumen"), firma.get("valor"), firma.get("certificado"), estampa.get("tiempo"), estampa.get("valor"));
    }

    /** El siguiente evento significativo: salta espacios y rechaza lo que el formato no admite. */
    private static int siguiente(XMLStreamReader r) throws XMLStreamException {
        while (r.hasNext()) {
            int ev = r.next();
            switch (ev) {
                case XMLStreamConstants.SPACE -> { }
                case XMLStreamConstants.CHARACTERS -> {
                    if (!r.isWhiteSpace()) {
                        throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "texto fuera de lugar");
                    }
                }
                case XMLStreamConstants.START_ELEMENT, XMLStreamConstants.END_ELEMENT, XMLStreamConstants.END_DOCUMENT -> {
                    return ev;
                }
                default -> throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "construcción no admitida (evento " + ev + ")");
            }
        }
        return XMLStreamConstants.END_DOCUMENT;
    }

    private static void abrir(XMLStreamReader r, String nombre) throws XMLStreamException {
        exigirInicio(r, siguiente(r), nombre);
    }

    private static void exigirInicio(XMLStreamReader r, int ev, String nombre) {
        if (ev != XMLStreamConstants.START_ELEMENT || !nombre.equals(r.getLocalName()) || !Formato.NS.equals(r.getNamespaceURI())) {
            throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "se esperaba <" + nombre + ">");
        }
    }

    private static void cerrar(XMLStreamReader r, String nombre) throws XMLStreamException {
        if (siguiente(r) != XMLStreamConstants.END_ELEMENT) {
            throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "se esperaba </" + nombre + ">");
        }
        exigir(r, nombre);
    }

    private static void exigir(XMLStreamReader r, String nombre) {
        if (!nombre.equals(r.getLocalName())) {
            throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "se esperaba </" + nombre + ">");
        }
    }

    /** Lee los elementos simples en el orden dado y el cierre del padre. */
    private static Map<String, String> campos(XMLStreamReader r, String padre, String... nombres) throws XMLStreamException {
        Map<String, String> m = new HashMap<>();
        for (String n : nombres) {
            abrir(r, n);
            m.put(n, texto(r));
        }
        cerrar(r, padre);
        return m;
    }

    /** El texto de un elemento simple, ya abierto; deja el lector en su cierre. */
    private static String texto(XMLStreamReader r) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ev = r.next();
            if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.SPACE) {
                sb.append(r.getText());
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                return sb.toString();
            } else {
                throw new ArchivoInvalido("ESTRUCTURA_INVALIDA", "construcción no admitida dentro de un campo (evento " + ev + ")");
            }
        }
    }

    private static XMLInputFactory fabrica() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        f.setProperty(XMLInputFactory.IS_COALESCING, true);
        return f;
    }
}
