package co.mati.reto4.comun;

/** Un archivo que no pasa la validación de la entrada o la verificación criptográfica, con su causa. */
public final class ArchivoInvalido extends RuntimeException {

    private final String causa;

    public ArchivoInvalido(String causa, String detalle) {
        super(causa + (detalle == null ? "" : ": " + detalle), null, false, false);
        this.causa = causa;
    }

    public String causa() {
        return causa;
    }
}
