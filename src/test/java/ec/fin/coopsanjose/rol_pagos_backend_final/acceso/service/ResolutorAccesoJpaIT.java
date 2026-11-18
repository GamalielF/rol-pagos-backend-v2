package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.dto.request.CrearRolRequest;
import ec.fin.coopsanjose.rol_pagos_backend_final.administracion.seguridad.SeguridadAdministracion;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.ResolutorAcceso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.ResolutorAcceso.RolesYPermisos;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.SoporteRolesPago;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad.SeguridadRolPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Roles y permisos efectivos de un usuario, leídos de la base (USUARIO_ROLES, ROL_PERMISOS, USUARIO_PERMISOS). */
class ResolutorAccesoJpaIT extends SoporteRolesPago {

    @Autowired private ResolutorAcceso resolutor;
    @Autowired private AsignacionAccesoService acceso;
    @Autowired private GestionRolesService gestion;

    @BeforeEach
    void aislarDeLosDatosReales() {
        apartarAsignacionesDeLosAdministradoresDeArranque();
    }

    private void asignar(String usuario, String rol) {
        acceso.asignarRol(usuarios.obtenerOCrear(usuario), acceso.rolAsignable(rol));
    }

    @Test
    void todoUsuarioEsEmpleadoYTieneLosPermisosDeEmpleado() {
        RolesYPermisos resultado = resolutor.resolver("zz.cualquiera");

        assertThat(resultado.roles()).containsExactly(RolesDelSistema.EMPLEADO);
        assertThat(resultado.permisos()).containsExactly(SeguridadRolPago.CONSULTAR_PROPIO);
    }

    @Test
    void sinNombreSoloEsEmpleado() {
        assertThat(resolutor.resolver(null).roles()).containsExactly(RolesDelSistema.EMPLEADO);
        assertThat(resolutor.resolver("   ").roles()).containsExactly(RolesDelSistema.EMPLEADO);
    }

    @Test
    void losAdministradoresDeArranqueSonAdminPorLaConfiguracionYTienenTodosLosPermisos() {
        for (String usuario : new String[]{"fsepa", "MSAVALA", "  fsepa "}) {
            RolesYPermisos resultado = resolutor.resolver(usuario);

            assertThat(resultado.roles()).containsExactlyInAnyOrder(RolesDelSistema.ADMIN, RolesDelSistema.EMPLEADO);
            assertThat(resultado.permisos()).contains(SeguridadRolPago.VER_AJENOS, SeguridadRolPago.GESTIONAR,
                    SeguridadAdministracion.USUARIOS);
        }
    }

    @Test
    void rrhhTraeLosPermisosDeSuRolYElPermisoSueltoSeSuma() {
        asignar("zz.ana", RolesDelSistema.RRHH);
        asignar("zz.beto", RolesDelSistema.RRHH);
        acceso.darPermiso(usuarios.obtenerOCrear("zz.ana"), acceso.permiso(SeguridadRolPago.VER_AJENOS));

        RolesYPermisos ana = resolutor.resolver("zz.ana");
        RolesYPermisos beto = resolutor.resolver("zz.beto");

        assertThat(ana.roles()).containsExactlyInAnyOrder(RolesDelSistema.EMPLEADO, RolesDelSistema.RRHH);
        assertThat(ana.permisos()).containsExactlyInAnyOrder(SeguridadRolPago.CONSULTAR_PROPIO,
                SeguridadRolPago.GESTIONAR, SeguridadRolPago.VER_AJENOS);
        assertThat(beto.permisos()).containsExactlyInAnyOrder(SeguridadRolPago.CONSULTAR_PROPIO, SeguridadRolPago.GESTIONAR);
    }

    @Test
    void unRolRevocadoOUnPermisoQuitadoYaNoCuentan() {
        asignar("zz.ana", RolesDelSistema.RRHH);
        acceso.darPermiso(usuarios.obtenerOCrear("zz.ana"), acceso.permiso(SeguridadRolPago.VER_AJENOS));

        acceso.revocarRol(usuarios.obtenerOCrear("zz.ana"), acceso.rolAsignable(RolesDelSistema.RRHH));
        acceso.quitarPermiso(usuarios.obtenerOCrear("zz.ana"), acceso.permiso(SeguridadRolPago.VER_AJENOS));

        RolesYPermisos resultado = resolutor.resolver("zz.ana");
        assertThat(resultado.roles()).containsExactly(RolesDelSistema.EMPLEADO);
        assertThat(resultado.permisos()).containsExactly(SeguridadRolPago.CONSULTAR_PROPIO);
    }

    @Test
    void unRolNuevoDaSusPermisosSinTocarCodigo() {
        gestion.crear(new CrearRolRequest("ZZ_AUDITOR", "Auditor de prueba", null, List.of(SeguridadRolPago.VER_AJENOS)));
        asignar("zz.carla", "ZZ_AUDITOR");

        RolesYPermisos resultado = resolutor.resolver("zz.carla");

        assertThat(resultado.roles()).contains("ZZ_AUDITOR");
        assertThat(resultado.permisos()).contains(SeguridadRolPago.VER_AJENOS).doesNotContain(SeguridadRolPago.GESTIONAR);
    }
}
