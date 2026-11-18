package ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.DetalleAcceso;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuarioAutenticado;
import ec.fin.coopsanjose.rol_pagos_backend_final.rolpago.seguridad.SeguridadRolPago;
import org.springframework.stereotype.Component;

/** Única fuente de verdad sobre quién puede ver qué rol de pago. */
@Component
public class PoliticaAccesoRol {

    /**
     * Consulta propia: la cédula tecleada debe ser la de la sesión, que sale del directorio del AD.
     * Una cuenta sin cédula en el directorio (genérica, o inexistente) no puede consultar nada.
     */
    public DetalleAcceso evaluarConsultaPropia(UsuarioAutenticado usuario, String cedulaIngresada) {
        if (usuario == null || usuario.cedula() == null || usuario.cedula().isBlank()) {
            return DetalleAcceso.SIN_CEDULA_EN_DIRECTORIO;
        }
        if (!usuario.cedula().equals(cedulaIngresada)) {
            return DetalleAcceso.CEDULA_NO_COINCIDE;
        }
        return DetalleAcceso.OK;
    }

    /**
     * Decide un PERMISO, no un rol: lo tiene ADMIN, el RRHH al que se le dio, y cualquier rol que se cree mañana con
     * ese permiso (por ejemplo un "Auditor interno") sin cambiar esta clase.
     */
    public boolean puedeConsultarAjeno(UsuarioAutenticado usuario) {
        return usuario != null && usuario.tienePermiso(SeguridadRolPago.VER_AJENOS);
    }
}