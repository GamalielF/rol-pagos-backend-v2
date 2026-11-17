package ec.fin.coopsanjose.rol_pagos_backend_final.usuario.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Punto único para obtener la identidad (el ID) de un usuario a partir de su código del AD.
 * Los módulos (rol de pago, auditoría, notificaciones y los futuros: nómina, vacaciones) dependen de esta
 * interfaz y no de cómo se guarda el usuario.
 */
public interface RegistroUsuarios {

    /** Código con el que se registran los procesos automáticos (sincronización, avisos, tareas programadas). */
    String USUARIO_SISTEMA = "SISTEMA";

    /** Devuelve el usuario con ese código; si todavía no existe, lo crea. */
    Usuario obtenerOCrear(String username);

    /** Igual que obtenerOCrear, para muchos códigos a la vez. Las claves del mapa van en minúsculas. */
    Map<String, Usuario> obtenerOCrearTodos(Collection<String> usernames);

    Optional<Usuario> buscar(String username);

    /** El usuario de los procesos automáticos. */
    default Usuario sistema() {
        return obtenerOCrear(USUARIO_SISTEMA);
    }
}
