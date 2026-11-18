package ec.fin.coopsanjose.rol_pagos_backend_final.administracion.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuariosDePrueba;
import ec.fin.coopsanjose.rol_pagos_backend_final.administracion.dto.response.UsuarioAdminDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.UsuarioRol;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.UsuarioRolRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.ResolutorAcceso;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.AccionRolPago;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.TipoAccion;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.repository.AccionRolPagoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.EstadoRegistro;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.ConflictoException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.RecursoNoEncontradoException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.exception.SolicitudInvalidaException;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.response.PaginaDto;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad.SeguridadRolPago;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.directorio.entity.DirectorioAd;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.SoporteRolesPago;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministracionUsuariosServiceIT extends SoporteRolesPago {

    @Autowired private AdministracionUsuariosService servicio;
    @Autowired private ResolutorAcceso roles;
    @Autowired private UsuarioRolRepository asignaciones;
    @Autowired private AccionRolPagoRepository acciones;

    @BeforeEach
    void escenario() {
        SecurityContextHolder.clearContext();
        apartarAsignacionesDeLosAdministradoresDeArranque();
        persona("zz.pedro", CEDULA_JUAN, NOMBRE_JUAN, true);
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    /** Solo los eventos de los usuarios de prueba (zz.*): la bitácora real de desarrollo no interviene. */
    private List<AccionRolPago> eventos(TipoAccion tipo) {
        em.flush();
        return acciones.findAll().stream()
                .filter(a -> a.getTipoAccion() == tipo && a.getDetalle() != null && a.getDetalle().startsWith("usuario=zz."))
                .sorted(Comparator.comparing(AccionRolPago::getId))
                .toList();
    }

    private List<UsuarioRol> filasDe(String usuario) {
        em.flush();
        return asignaciones.findAll().stream().filter(a -> usuario.equals(a.getUsuario().getUsernameAd())).toList();
    }

    private void actuarComo(String usuario) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UsuariosDePrueba.con(usuario, null, null, Set.of(RolesDelSistema.ADMIN)), null, List.of()));
    }

    // ------------------------------------------------------------ otorgar

    @Test
    void otorgarRrhhPorDefectoConcedeElPermisoYQuedaRegistrado() {
        UsuarioAdminDto dto = servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);

        assertThat(dto.estadoDirectorio()).isEqualTo("ACTIVO");
        assertThat(dto.roles()).singleElement().satisfies(r -> {
            assertThat(r.rol()).isEqualTo("RRHH");
            assertThat(r.origen()).isEqualTo("ASIGNADO");
            assertThat(r.verRolesAjenos()).isTrue();
        });
        assertThat(roles.resolver("zz.pedro").permisos()).contains(SeguridadRolPago.VER_AJENOS, SeguridadRolPago.GESTIONAR);
        assertThat(eventos(TipoAccion.USUARIO_ROL_OTORGADO)).singleElement()
                .satisfies(a -> assertThat(a.getDetalle()).isEqualTo("usuario=zz.pedro, rol=RRHH, verRolesAjenos=true"));
    }

    @Test
    void otorgarRrhhSinElPermisoDeVerRolesAjenos() {
        UsuarioAdminDto dto = servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, false);

        assertThat(dto.roles()).singleElement().satisfies(r -> assertThat(r.verRolesAjenos()).isFalse());
        assertThat(roles.resolver("zz.pedro").roles()).contains(RolesDelSistema.RRHH);
        assertThat(roles.resolver("zz.pedro").permisos()).contains(SeguridadRolPago.GESTIONAR).doesNotContain(SeguridadRolPago.VER_AJENOS);
        assertThat(eventos(TipoAccion.USUARIO_ROL_OTORGADO)).singleElement()
                .satisfies(a -> assertThat(a.getDetalle()).isEqualTo("usuario=zz.pedro, rol=RRHH, verRolesAjenos=false"));
    }

    @Test
    void otorgarEsIdempotenteYNoDuplicaFilasNiEventos() {
        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);
        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);

        assertThat(filasDe("zz.pedro")).hasSize(1);
        assertThat(eventos(TipoAccion.USUARIO_ROL_OTORGADO)).hasSize(1);
    }

    @Test
    void cambiarElPermisoDeUnRrhhExistenteRegistraOtroEvento() {
        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, true);
        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, false);

        assertThat(filasDe("zz.pedro")).hasSize(1);
        assertThat(roles.resolver("zz.pedro").permisos()).doesNotContain(SeguridadRolPago.VER_AJENOS);
        assertThat(eventos(TipoAccion.USUARIO_ROL_OTORGADO)).hasSize(2);
    }

    @Test
    void elAdminSiempreTieneElPermisoSinImportarLoPedido() {
        UsuarioAdminDto dto = servicio.otorgar("zz.pedro", RolesDelSistema.ADMIN, false);

        assertThat(dto.roles()).singleElement().satisfies(r -> {
            assertThat(r.rol()).isEqualTo("ADMIN");
            assertThat(r.verRolesAjenos()).isTrue();
        });
        assertThat(roles.resolver("zz.pedro").roles()).contains(RolesDelSistema.ADMIN);
        assertThat(eventos(TipoAccion.USUARIO_ROL_OTORGADO)).singleElement()
                .satisfies(a -> assertThat(a.getDetalle()).isEqualTo("usuario=zz.pedro, rol=ADMIN"));
    }

    @Test
    void noSeOtorgaAQuienNoExisteNiAQuienEstaInactivoEnElAd() {
        persona("zz.baja", CEDULA_MARIA, NOMBRE_MARIA, false);

        assertThatThrownBy(() -> servicio.otorgar("zz.no.existe", RolesDelSistema.RRHH, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.otorgar("zz.baja", RolesDelSistema.RRHH, null))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("inactivo");
        assertThat(filasDe("zz.baja")).isEmpty();
    }

    @Test
    void soloSeAsignanAdminYRrhhYElCodigoDebeSerValido() {
        assertThatThrownBy(() -> servicio.otorgar("zz.pedro", RolesDelSistema.EMPLEADO, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("EMPLEADO no se asigna");
        assertThatThrownBy(() -> servicio.otorgar("a b", RolesDelSistema.RRHH, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("inválido");
        assertThatThrownBy(() -> servicio.otorgar("../etc", RolesDelSistema.RRHH, null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.revocar("zz.pedro", RolesDelSistema.EMPLEADO))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void unAdministradorDeArranqueNoSeReasignaNiSeRevoca() {
        persona("fsepa", CEDULA_MARIA, NOMBRE_MARIA, true);

        assertThatThrownBy(() -> servicio.otorgar("fsepa", RolesDelSistema.ADMIN, null))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("arranque");
        assertThatThrownBy(() -> servicio.revocar("fsepa", RolesDelSistema.ADMIN))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("arranque");
        assertThat(filasDe("fsepa")).isEmpty();
    }

    // ------------------------------------------------------------ revocar

    @Test
    void revocarInactivaLaAsignacionSinBorrarlaYSurteEfecto() {
        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);

        UsuarioAdminDto dto = servicio.revocar("zz.pedro", RolesDelSistema.RRHH);

        assertThat(dto.roles()).isEmpty();
        assertThat(roles.resolver("zz.pedro").roles()).containsExactly(RolesDelSistema.EMPLEADO);
        assertThat(filasDe("zz.pedro")).singleElement()
                .satisfies(a -> assertThat(a.getEstadoRegistro()).isEqualTo(EstadoRegistro.INACTIVO));
        assertThat(eventos(TipoAccion.USUARIO_ROL_REVOCADO)).singleElement()
                .satisfies(a -> assertThat(a.getDetalle()).isEqualTo("usuario=zz.pedro, rol=RRHH"));
    }

    @Test
    void revocarUnRolQueNoSeTieneDa404() {
        assertThatThrownBy(() -> servicio.revocar("zz.pedro", RolesDelSistema.RRHH)).isInstanceOf(RecursoNoEncontradoException.class);

        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);
        servicio.revocar("zz.pedro", RolesDelSistema.RRHH);

        assertThatThrownBy(() -> servicio.revocar("zz.pedro", RolesDelSistema.RRHH)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void volverAOtorgarDespuesDeRevocarReactivaLaMismaFila() {
        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);
        servicio.revocar("zz.pedro", RolesDelSistema.RRHH);

        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);

        assertThat(filasDe("zz.pedro")).singleElement()
                .satisfies(a -> assertThat(a.getEstadoRegistro()).isEqualTo(EstadoRegistro.ACTIVO));
        assertThat(roles.resolver("zz.pedro").roles()).contains(RolesDelSistema.RRHH);
    }

    @Test
    void nadieSeQuitaElAdminASiMismoPeroUnAdminSiPuedeQuitarloAOtro() {
        persona("zz.admin1", CEDULA_MARIA, NOMBRE_MARIA, true);
        persona("zz.admin2", "1723456784", "SILVA TORRES LUIS ALBERTO", true);
        servicio.otorgar("zz.admin1", RolesDelSistema.ADMIN, null);
        servicio.otorgar("zz.admin2", RolesDelSistema.ADMIN, null);
        actuarComo("zz.admin1");

        assertThatThrownBy(() -> servicio.revocar("zz.admin1", RolesDelSistema.ADMIN))
                .isInstanceOf(ConflictoException.class).hasMessageContaining("a sí mismo");
        assertThat(servicio.revocar("zz.admin2", RolesDelSistema.ADMIN).roles()).isEmpty();
        assertThat(roles.resolver("zz.admin1").roles()).contains(RolesDelSistema.ADMIN);
    }

    // ------------------------------------------------------------ consultas

    @Test
    void buscarFiltraPorNombreOCodigoSoloActivosYNeutralizaLosComodines() {
        persona("zz.ana", CEDULA_MARIA, "PEREZ ANA LUCIA", true);
        persona("zz.beto", "1723456784", "RUIZ BETO", true);
        persona("zz.baja", "1711111110", "PEREZ ANA BAJA", false);
        servicio.otorgar("zz.ana", RolesDelSistema.RRHH, null);

        assertThat(servicio.buscar("ana", 0, 50).elementos()).extracting(UsuarioAdminDto::username).containsExactly("zz.ana");
        assertThat(servicio.buscar("ZZ.BE", 0, 50).elementos()).extracting(UsuarioAdminDto::username).containsExactly("zz.beto");
        assertThat(servicio.buscar("nadie", 0, 50).totalElementos()).isZero();
        // los comodines de LIKE tecleados no se comportan como comodines: equivalen a "sin filtro" (3 activos)
        assertThat(servicio.buscar("%", 0, 50).totalElementos()).isEqualTo(3);
        assertThat(servicio.buscar("_", 0, 50).totalElementos()).isEqualTo(3);
        assertThat(servicio.buscar(null, 0, 50).totalElementos()).isEqualTo(3);
        // el rol aparece junto al usuario, y nunca se muestra a los inactivos
        assertThat(servicio.buscar("zz.ana", 0, 50).elementos()).singleElement()
                .satisfies(u -> assertThat(u.roles()).extracting(r -> r.rol()).containsExactly("RRHH"));
        assertThat(servicio.buscar("zz.baja", 0, 50).totalElementos()).isZero();
    }

    @Test
    void laPaginacionSeAcota() {
        PaginaDto<UsuarioAdminDto> enorme = servicio.buscar(null, 0, 10_000);
        PaginaDto<UsuarioAdminDto> raro = servicio.buscar(null, -3, 0);

        assertThat(enorme.tamano()).isEqualTo(200);
        assertThat(raro.pagina()).isZero();
        assertThat(raro.tamano()).isEqualTo(50);
    }

    @Test
    void privilegiadosIncluyeArranqueAsignadosYDadosDeBaja() {
        servicio.otorgar("zz.pedro", RolesDelSistema.RRHH, null);
        persona("zz.otro", CEDULA_MARIA, NOMBRE_MARIA, true);
        servicio.otorgar("zz.otro", RolesDelSistema.RRHH, null);
        DirectorioAd otro = directorioRepository.findByUsernameAd("zz.otro").orElseThrow();
        otro.inactivar();   // lo dieron de baja en el AD: sigue apareciendo para poder quitarle el rol
        directorioRepository.save(otro);
        em.flush();

        List<UsuarioAdminDto> lista = servicio.privilegiados();

        assertThat(lista).filteredOn(u -> u.username().equals("fsepa")).singleElement().satisfies(u -> {
            assertThat(u.roles()).singleElement().satisfies(r -> {
                assertThat(r.rol()).isEqualTo("ADMIN");
                assertThat(r.origen()).isEqualTo("CONFIGURACION");
            });
            assertThat(u.estadoDirectorio()).isEqualTo("NO_EXISTE");   // en la prueba el directorio está vacío
        });
        assertThat(lista).extracting(UsuarioAdminDto::username).contains("msavala", "zz.pedro", "zz.otro");
        assertThat(lista).filteredOn(u -> u.username().equals("zz.otro")).singleElement()
                .satisfies(u -> assertThat(u.estadoDirectorio()).isEqualTo("INACTIVO"));
    }
}