package co.mati.reto4.reprocesador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "co.mati.reto4")
public class ReprocesadorApp {
    public static void main(String[] args) {
        SpringApplication.run(ReprocesadorApp.class, args);
    }
}
