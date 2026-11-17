package ec.fin.coopsanjose.rol_pagos_backend_final.usuario.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuarioActual;
import ec.fin.coopsanjose.rol_pagos_backend_final.common.seguridad.UsuarioAutenticado;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistroUsuariosJpa implements RegistroUsuarios {

    private final UsuarioRepository repositorio;

    @Override
    @Transactional
    public Usuario obtenerOCrear(String username) {
        String codigo = normalizar(username);
        return repositorio.findByUsernameAd(codigo).orElseGet(() -> {
            repositorio.insertarSiNoExiste(codigo, actor(), LocalDateTime.now());
            return repositorio.buscarConfirmado(codigo)
                    .orElseThrow(() -> new IllegalStateException("No se pudo registrar el usuario " + codigo));
        });
    }

    @Override
    @Transactional
    public Map<String, Usuario> obtenerOCrearTodos(Collection<String> usernames) {
        Set<String> codigos = new LinkedHashSet<>();
        usernames.forEach(u -> codigos.add(normalizar(u)));
        Map<String, Usuario> resultado = new LinkedHashMap<>();
        if (codigos.isEmpty()) {
            return resultado;
        }
        repositorio.findByUsernameAdIn(codigos).forEach(u -> resultado.put(clave(u.getUsernameAd()), u));
        codigos.stream()
                .filter(codigo -> !resultado.containsKey(clave(codigo)))
                .forEach(codigo -> resultado.put(clave(codigo), obtenerOCrear(codigo)));
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscar(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return repositorio.findByUsernameAd(normalizar(username));
    }

    private static String normalizar(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("El código de usuario es obligatorio");
        }
        String codigo = username.trim();
        return codigo.length() > Usuario.LARGO_MAXIMO_USERNAME ? codigo.substring(0, Usuario.LARGO_MAXIMO_USERNAME) : codigo;
    }

    private static String clave(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static String actor() {
        return UsuarioActual.obtener().map(UsuarioAutenticado::username).orElse(USUARIO_SISTEMA);
    }
}
