package main.web.services.fitsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @ConfigurationPropertiesScan registra ReplicateProperties. Sin el, un record
 * anotado con @ConfigurationProperties no se convierte en bean y el adaptador de
 * IA no puede construirse, aunque la IA este apagada: el bean se crea igual.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FitsenseApplication {
	public static void main(String[] args) {
		SpringApplication.run(FitsenseApplication.class, args);
	}
}