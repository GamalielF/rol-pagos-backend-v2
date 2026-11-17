package ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.service.RegistroUsuarios;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.AccionRolPago;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.entity.TipoAccion;
import ec.fin.coopsanjose.rol_pagos_backend_final.auditoria.repository.AccionRolPagoRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuarioActual;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registra las acciones de RRHH/Admin. El actor y la IP salen del contexto de la petición, así que los servicios
 * no tienen que pasarlos. Sin sesión (un proceso interno) el actor es SISTEMA. Participa de la transacción
 * en curso: si la acción se revierte, su registro también.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaAccionesService {

    private final AccionRolPagoRepository repositorio;
    private final RegistroUsuarios usuarios;

    public void registrar(TipoAccion tipo, String periodo, Long rolPagoId, String detalle) {
        AccionRolPago accion = new AccionRolPago();
        accion.setTipoAccion(tipo);
        accion.setUsuario(usuarios.obtenerOCrear(
                UsuarioActual.obtener().map(UsuarioAutenticado::username).orElse(RegistroUsuarios.USUARIO_SISTEMA)));
        accion.setPeriodo(limitar(periodo, 7));
        accion.setRolPagoId(rolPagoId);
        accion.setDetalle(limitar(detalle, 500));
        accion.setIpOrigen(limitar(ipDeLaPeticion(), 45));
        repositorio.save(accion);
    }

    private static String ipDeLaPeticion() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static String limitar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() > maximo ? valor.substring(0, maximo) : valor;
    }
}
