package ec.fin.coopsanjose.rol_pagos_backend_final.acceso.entity;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.entity.BaseEntity;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un rol de acceso de un usuario. INACTIVO = revocado (nunca se borra: queda la historia). */
@Entity
@Table(name = "USUARIO_ROLES", uniqueConstraints = @UniqueConstraint(name = "UK_USUARIO_ROLES", columnNames = {"USUARIO_ID", "ROL_ID"}))
@Getter
@Setter
@NoArgsConstructor
public class UsuarioRol extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ROL_ID", nullable = false, updatable = false)
    private Rol rol;

    public UsuarioRol(Usuario usuario, Rol rol) {
        this.usuario = usuario;
        this.rol = rol;
    }
}
