package ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad;

import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad.SeguridadRolPago;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioAutenticadoTest {

    @Test
    void elConstructorCortoDaElPermisoAPersonalDeRrhhYAdmin() {
        assertThat(UsuariosDePrueba.con("u", null, null, Set.of(RolesDelSistema.RRHH)).tienePermiso(SeguridadRolPago.VER_AJENOS)).isTrue();
        assertThat(UsuariosDePrueba.con("u", null, null, Set.of(RolesDelSistema.ADMIN)).tienePermiso(SeguridadRolPago.VER_AJENOS)).isTrue();
        assertThat(UsuariosDePrueba.con("u", null, null, Set.of(RolesDelSistema.EMPLEADO)).tienePermiso(SeguridadRolPago.VER_AJENOS)).isFalse();
    }

    @Test
    void elConstructorLargoRespetaLosPermisosExplicitos() {
        UsuarioAutenticado rrhhSinPermiso = new UsuarioAutenticado("u", null, null, Set.of(RolesDelSistema.RRHH), Set.of());

        assertThat(rrhhSinPermiso.tieneRol(RolesDelSistema.RRHH)).isTrue();
        assertThat(rrhhSinPermiso.tienePermiso(SeguridadRolPago.VER_AJENOS)).isFalse();
    }

    @Test
    void losConjuntosSeCopianYNoSePuedenAlterar() {
        Set<String> original = new HashSet<>(Set.of(RolesDelSistema.EMPLEADO));
        UsuarioAutenticado usuario = UsuariosDePrueba.con("u", null, null, original);

        original.add(RolesDelSistema.ADMIN);   // cambiar el conjunto original no afecta a la sesión

        assertThat(usuario.tieneRol(RolesDelSistema.ADMIN)).isFalse();
        assertThatThrownBy(() -> usuario.roles().add(RolesDelSistema.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> usuario.permisos().add(SeguridadRolPago.VER_AJENOS)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void elUsernameEsObligatorio() {
        assertThatThrownBy(() -> UsuariosDePrueba.con(null, null, null, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UsuariosDePrueba.con("  ", null, null, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losConjuntosNulosSeVuelvenVacios() {
        UsuarioAutenticado usuario = new UsuarioAutenticado("u", null, null, null, null);

        assertThat(usuario.roles()).isEmpty();
        assertThat(usuario.permisos()).isEmpty();
    }
}
