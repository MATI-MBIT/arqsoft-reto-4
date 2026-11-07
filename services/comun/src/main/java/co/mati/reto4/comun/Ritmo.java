package co.mati.reto4.comun;

/**
 * Tope de declaraciones por segundo (cubeta de fichas). El reprocesador lo usa para
 * reprocesar a ritmo propio y no al ritmo del regreso del tercero (C-04).
 */
public final class Ritmo {

    private final double porSegundo;
    private double fichas;
    private long ultimo = System.nanoTime();

    public Ritmo(double porSegundo) {
        this.porSegundo = porSegundo;
        this.fichas = porSegundo;
    }

    public synchronized void adquirir(int n) {
        while (true) {
            long ahora = System.nanoTime();
            fichas = Math.min(porSegundo, fichas + (ahora - ultimo) / 1e9 * porSegundo);
            ultimo = ahora;
            if (fichas >= n || fichas >= porSegundo) {
                fichas -= n;
                return;
            }
            long esperaMs = (long) Math.ceil((n - fichas) / porSegundo * 1000);
            try {
                wait(Math.max(1, esperaMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
