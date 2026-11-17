package ec.fin.coopsanjose.rol_pagos_backend_final.usuario.repository;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.repository.BaseRepository;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends BaseRepository<Usuario> {

    /** La intercalación de MySQL no distingue mayúsculas: 'FSEPA' y 'fsepa' encuentran al mismo usuario. */
    Optional<Usuario> findByUsernameAd(String usernameAd);

    List<Usuario> findByUsernameAdIn(Collection<String> usernames);

    /**
     * Lectura CON BLOQUEO (SELECT ... FOR SHARE): ve lo último confirmado por otras transacciones, no la "foto"
     * que MySQL guarda para la transacción actual (aislamiento REPEATABLE READ). Se usa justo después de
     * insertarSiNoExiste, para encontrar el usuario aunque lo haya creado otra petición al mismo tiempo.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from Usuario u where u.usernameAd = :username")
    Optional<Usuario> buscarConfirmado(@Param("username") String username);

    /**
     * Crea el usuario si no existe, de forma segura ante peticiones simultáneas: si otra transacción lo acaba de
     * crear, la clave única hace que MySQL no inserte nada (ON DUPLICATE KEY) en lugar de lanzar un error.
     */
    @Modifying
    @Query(value = "INSERT INTO USUARIOS (USERNAME_AD, ESTADO_REGISTRO, CREADO_POR, CREADO_EN, MODIFICADO_POR, MODIFICADO_EN, VERSION) "
            + "VALUES (:username, 'ACTIVO', :actor, :ahora, :actor, :ahora, 0) "
            + "ON DUPLICATE KEY UPDATE ID = ID", nativeQuery = true)
    void insertarSiNoExiste(@Param("username") String username, @Param("actor") String actor,
                            @Param("ahora") LocalDateTime ahora);
}
