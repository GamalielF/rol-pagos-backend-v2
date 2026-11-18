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

/** Un permiso suelto de un usuario, además de los de sus roles. INACTIVO = quitado. */
@Entity
@Table(name = "USUARIO_PERMISOS", uniqueConstraints = @UniqueConstraint(name = "UK_USUARIO_PERMISOS", columnNames = {"USUARIO_ID", "PERMISO_ID"}))
@Getter
@Setter
@NoArgsConstructor
public class UsuarioPermiso extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "USUARIO_ID", nullable = false, updatable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "PERMISO_ID", nullable = false, updatable = false)
    private Permiso permiso;

    public UsuarioPermiso(Usuario usuario, Permiso permiso) {
        this.usuario = usuario;
        this.permiso = permiso;
    }
}
