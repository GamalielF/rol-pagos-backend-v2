package ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuariosDePrueba;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad.SeguridadRolPago;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuarioAutenticado;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ver roles ajenos es el permiso ROLPAGO_VER_AJENOS. Lo que cuenta es el permiso, no el nombre del rol: así un rol creado
 * por el Admin (un "auditor", por ejemplo) puede tenerlo. Llegar a la ruta /rrhh/** exige además ROLPAGO_GESTIONAR.
 */
class PoliticaAccesoRolPermisosTest {

    private final PoliticaAccesoRol politica = new PoliticaAccesoRol();

    private static UsuarioAutenticado usuario(Set<String> roles, Set<String> permisos) {
        return new UsuarioAutenticado("zz.prueba", null, null, roles, permisos);
    }

    @Test
    void elAdminVeRolesAjenosPorqueSuRolTieneTodosLosPermisos() {
        assertThat(politica.puedeConsultarAjeno(UsuariosDePrueba.con("zz.prueba", null, null, Set.of(RolesDelSistema.ADMIN)))).isTrue();
    }

    @Test
    void rrhhConElPermisoVeRolesAjenos() {
        assertThat(politica.puedeConsultarAjeno(usuario(Set.of(RolesDelSistema.RRHH), Set.of(SeguridadRolPago.VER_AJENOS)))).isTrue();
    }

    @Test
    void rrhhSinElPermisoNoVeRolesAjenos() {
        assertThat(politica.puedeConsultarAjeno(usuario(Set.of(RolesDelSistema.RRHH), Set.of()))).isFalse();
    }

    @Test
    void loQueCuentaEsElPermisoNoElNombreDelRol() {
        assertThat(politica.puedeConsultarAjeno(usuario(Set.of("AUDITOR"), Set.of(SeguridadRolPago.VER_AJENOS)))).isTrue();
        assertThat(politica.puedeConsultarAjeno(usuario(Set.of(RolesDelSistema.EMPLEADO), Set.of()))).isFalse();
    }
}