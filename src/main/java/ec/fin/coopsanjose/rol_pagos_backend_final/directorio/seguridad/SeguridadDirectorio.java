package ec.fin.coopsanjose.rol_pagos_backend_final.directorio.seguridad;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.DefinicionPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.ModuloSeguridad;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import java.util.List;

/** Permisos y rutas del directorio del AD. */
@Component
public class SeguridadDirectorio implements ModuloSeguridad {

    public static final String ADMINISTRAR = "ADMIN_DIRECTORIO";

    @Override
    public String modulo() {
        return "DIRECTORIO";
    }

    @Override
    public List<DefinicionPermiso> permisos() {
        return List.of(
                DefinicionPermiso.de(ADMINISTRAR, "Sincronizar y consultar el directorio del AD"));
    }

    @Override
    public void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reglas) {
        reglas.requestMatchers("/admin/directorio/**").hasAuthority(ADMINISTRAR);
    }
}
