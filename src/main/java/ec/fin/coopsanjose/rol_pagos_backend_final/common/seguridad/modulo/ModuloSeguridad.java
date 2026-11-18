package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.List;

/**
 * La "cara de seguridad" de un módulo: qué permisos existen en él y qué rutas protege cada uno.
 *
 * Es el punto de enchufe (plug and play): un módulo nuevo (NÓMINA, VACACIONES) crea UNA clase que implementa
 * esta interfaz, marcada con @Component, y listo. SecurityConfig aplica sus reglas y, al arrancar, sus permisos se
 * registran en la base. No hay que modificar ninguna clase existente (principio abierto/cerrado, la O de SOLID).
 */
public interface ModuloSeguridad {

    /** Nombre corto del módulo, en MAYÚSCULAS: ROLPAGO, NOMINA. Agrupa los permisos en la administración. */
    String modulo();

    /** Los permisos que el código del módulo comprueba. */
    List<DefinicionPermiso> permisos();

    /**
     * Las reglas HTTP del módulo, normalmente una por prefijo de ruta:
     * {@code reglas.requestMatchers("/nomina/**").hasAuthority(PermisosNomina.CALCULAR)}.
     * Se aplican antes de la regla final "toda ruta exige sesión".
     */
    void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reglas);
}
