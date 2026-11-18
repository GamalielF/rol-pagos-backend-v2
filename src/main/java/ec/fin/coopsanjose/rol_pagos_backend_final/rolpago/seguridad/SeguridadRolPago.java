package ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.DefinicionPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.ModuloSeguridad;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import java.util.List;

/** Permisos y rutas del módulo de roles de pago. */
@Component
public class SeguridadRolPago implements ModuloSeguridad {

    public static final String CONSULTAR_PROPIO = "ROLPAGO_CONSULTAR_PROPIO";
    public static final String VER_AJENOS = "ROLPAGO_VER_AJENOS";
    /** Incluye los avisos por correo de cada período (rutas /rrhh/periodos/{p}/notificaciones). */
    public static final String GESTIONAR = "ROLPAGO_GESTIONAR";

    @Override
    public String modulo() {
        return "ROLPAGO";
    }

    @Override
    public List<DefinicionPermiso> permisos() {
        return List.of(
                DefinicionPermiso.de(CONSULTAR_PROPIO, "Consultar su propio rol de pago", RolesDelSistema.EMPLEADO),
                DefinicionPermiso.de(VER_AJENOS, "Abrir el rol de pago de otra persona (indicando el motivo)"),
                DefinicionPermiso.de(GESTIONAR, "Cargar, corregir y publicar roles de pago, y gestionar los avisos",
                        RolesDelSistema.RRHH));
    }

    @Override
    public void reglas(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry reglas) {
        reglas.requestMatchers("/rrhh/**").hasAuthority(GESTIONAR)
                .requestMatchers("/mi-rol-pago/**").hasAuthority(CONSULTAR_PROPIO);
    }
}
