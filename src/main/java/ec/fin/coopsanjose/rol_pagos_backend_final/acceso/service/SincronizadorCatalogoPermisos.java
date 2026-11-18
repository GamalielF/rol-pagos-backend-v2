package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Permiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.Rol;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity.RolPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.PermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolPermisoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.acceso.repository.RolRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.RolesDelSistema;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.DefinicionPermiso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.modulo.ModuloSeguridad;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Al arrancar, registra en la tabla PERMISOS los permisos que declaran los módulos (todas las clases que implementan
 * ModuloSeguridad). Un permiso NUEVO se da a ADMIN y a sus roles por defecto; uno que ya existía no se toca, porque el
 * administrador pudo haber cambiado a quién se lo da.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SincronizadorCatalogoPermisos implements ApplicationRunner {

    private final List<ModuloSeguridad> modulos;
    private final PermisoRepository permisos;
    private final RolRepository roles;
    private final RolPermisoRepository rolPermisos;

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        Map<String, String> moduloDe = verificarSinRepetidos();
        int nuevos = 0;
        for (ModuloSeguridad modulo : modulos) {
            for (DefinicionPermiso definicion : modulo.permisos()) {
                nuevos += registrar(modulo.modulo(), definicion) ? 1 : 0;
            }
        }
        log.info("Catálogo de permisos: {} módulos, {} permisos declarados, {} nuevos registrados",
                modulos.size(), moduloDe.size(), nuevos);
    }

    /** Dos módulos no pueden declarar el mismo código: la aplicación no arranca (mejor un error claro que un permiso ambiguo). */
    private Map<String, String> verificarSinRepetidos() {
        Map<String, String> moduloDe = new HashMap<>();
        for (ModuloSeguridad modulo : modulos) {
            for (DefinicionPermiso definicion : modulo.permisos()) {
                String anterior = moduloDe.putIfAbsent(definicion.codigo(), modulo.modulo());
                if (anterior != null) {
                    throw new IllegalStateException("El permiso " + definicion.codigo() + " lo declaran dos módulos: "
                            + anterior + " y " + modulo.modulo());
                }
            }
        }
        return moduloDe;
    }

    /** @return true si el permiso no existía y se creó */
    private boolean registrar(String modulo, DefinicionPermiso definicion) {
        Permiso existente = permisos.findByCodigo(definicion.codigo()).orElse(null);
        if (existente != null) {
            if (!existente.getDescripcion().equals(definicion.descripcion()) || !existente.getModulo().equals(modulo)) {
                existente.setDescripcion(definicion.descripcion());
                existente.setModulo(modulo);
                permisos.save(existente);
            }
            return false;
        }
        Permiso nuevo = new Permiso();
        nuevo.setCodigo(definicion.codigo());
        nuevo.setModulo(modulo);
        nuevo.setDescripcion(definicion.descripcion());
        permisos.save(nuevo);

        Set<String> destinatarios = new LinkedHashSet<>();
        destinatarios.add(RolesDelSistema.ADMIN);
        destinatarios.addAll(definicion.rolesPorDefecto());
        for (String codigoRol : destinatarios) {
            Rol rol = roles.findByCodigo(codigoRol).orElse(null);
            if (rol == null) {
                log.warn("El permiso {} pide el rol {}, que no existe: se omite", definicion.codigo(), codigoRol);
                continue;
            }
            rolPermisos.save(new RolPermiso(rol, nuevo));
        }
        log.info("Permiso nuevo {} ({}) para {}", definicion.codigo(), modulo, destinatarios);
        return true;
    }
}
