package main.web.services.fitsense.configuration.domain.exceptions;

/**
 * Sin configuracion activa no se puede calcular adherencia ni decidir un ajuste.
 * Es un fallo de despliegue (falta V7 o alguien desactivo la fila), no un caso
 * de negocio: por eso revienta en vez de aplicar valores por defecto silenciosos.
 */
public class NoActiveConfigurationException extends IllegalStateException {
    public NoActiveConfigurationException() {
        super("No hay una fila activa en calculation_configs. Revisa la migracion V7.");
    }
}
