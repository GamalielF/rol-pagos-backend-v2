package ec.fin.coopsanjose.rol_pagos_backend_final.administracion.seguridad;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.DefinicionPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.ModuloSeguridad;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import java.util.List;

/** Permisos y rutas de la administración de usuarios, roles y permisos. */
@Component
public class SeguridadAdministracion implements ModuloSeguridad {

    public static final String USUARIOS = "ADMIN_USUARIOS";

    @Override
    public String modulo() {
        return "ADMINISTRACION";
    }

    @Override
    public List<DefinicionPermiso> permisos() {
        return List.of(
                DefinicionPermiso.de(USUARIOS, "Dar y quitar roles, y definir roles y permisos"));
    }

    @Override
    public void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reglas) {
        reglas.requestMatchers("/admin/usuarios/**", "/admin/roles/**", "/admin/permisos/**").hasAuthority(USUARIOS);
    }
}
