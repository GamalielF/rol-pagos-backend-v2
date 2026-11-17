package ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Identidad de un usuario del sistema. Su ID (secuencial, heredado de BaseEntity) es lo que las demás tablas
 * guardan para referirse a él; el código del AD es solo un dato suyo, único, que puede cambiar sin romper nada.
 * No depende del AD: existe aunque la persona aún no se haya sincronizado o ya esté dada de baja.
 */
@Entity
@Table(name = "USUARIOS",
        uniqueConstraints = @UniqueConstraint(name = "UK_USUARIOS_USERNAME", columnNames = "USERNAME_AD"))
@Getter
@NoArgsConstructor
public class Usuario extends BaseEntity {

    public static final int LARGO_MAXIMO_USERNAME = 50;

    @Column(name = "USERNAME_AD", nullable = false, length = LARGO_MAXIMO_USERNAME)
    private String usernameAd;

    public Usuario(String usernameAd) {
        this.usernameAd = usernameAd;
    }
}
