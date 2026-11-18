package ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.seguridad;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.DefinicionPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.ModuloSeguridad;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import java.util.List;

/** Permisos y rutas de la auditoría. */
@Component
public class SeguridadAuditoria implements ModuloSeguridad {

    public static final String CONSULTAR = "ADMIN_AUDITORIA";

    @Override
    public String modulo() {
        return "AUDITORIA";
    }

    @Override
    public List<DefinicionPermiso> permisos() {
        return List.of(
                DefinicionPermiso.de(CONSULTAR, "Consultar y exportar la auditoria"));
    }

    @Override
    public void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reglas) {
        reglas.requestMatchers("/admin/auditoria/**").hasAuthority(CONSULTAR);
    }
}
