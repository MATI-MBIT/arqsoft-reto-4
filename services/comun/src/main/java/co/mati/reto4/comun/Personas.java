package co.mati.reto4.comun;

/**
 * Las personas ficticias del RNIC simulado. El nombre de cada cédula sale de una
 * función determinista, así el remitente simulado y el RNIC simulado coinciden
 * sin compartir una tabla.
 */
public final class Personas {

    public static final int TOTAL = 100_000;
    private static final long BASE = 1_000_000_000L;
    private static final String[] NOMBRES = {"Ana", "Luis", "Carlos", "Maria", "Jorge", "Paula", "Andres", "Diana",
            "Camilo", "Laura", "Felipe", "Sofia", "Daniel", "Valentina", "Juan", "Natalia", "Mateo", "Carolina", "Diego", "Juliana"};
    private static final String[] APELLIDOS = {"Gomez", "Rodriguez", "Martinez", "Lopez", "Garcia", "Perez", "Sanchez",
            "Ramirez", "Torres", "Diaz", "Vargas", "Rojas", "Moreno", "Castro", "Ortiz", "Rubio", "Reyes", "Chaparro", "Rozo", "Suarez"};

    private Personas() {}

    public static long cedula(int i) {
        return BASE + Math.floorMod(i, TOTAL);
    }

    public static boolean existe(long cedula) {
        return cedula >= BASE && cedula < BASE + TOTAL;
    }

    public static String nombre(long cedula) {
        long h = (cedula * 2_654_435_761L) ^ (cedula >>> 7);
        int a = (int) Math.floorMod(h, NOMBRES.length);
        int b = (int) Math.floorMod(h / 31, APELLIDOS.length);
        int c = (int) Math.floorMod(h / 977, APELLIDOS.length);
        return NOMBRES[a] + " " + APELLIDOS[b] + " " + APELLIDOS[c];
    }

    /** Un nombre que no corresponde a la cédula: la declaración se rechaza por la regla. */
    public static String nombreErrado(long cedula) {
        String bueno = nombre(cedula);
        String otro = nombre(cedula + 7);
        return otro.equals(bueno) ? otro + " Otro" : otro;
    }
}
