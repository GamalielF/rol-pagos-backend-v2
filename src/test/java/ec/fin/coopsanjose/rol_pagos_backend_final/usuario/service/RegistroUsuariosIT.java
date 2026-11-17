package ec.fin.coopsanjose.rol_pagos_backend_final.usuario.service;

import ec.fin.coopsanjose.rol_pagos_backend_final.usuario.entity.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RegistroUsuariosIT {

    @Autowired private RegistroUsuarios registro;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager transacciones;

    @Test
    @Transactional
    void creaElUsuarioUnaSolaVezYLuegoDevuelveElMismo() {
        Usuario primero = registro.obtenerOCrear("zz.nuevo");
        Usuario segundo = registro.obtenerOCrear("zz.nuevo");

        assertThat(primero.getId()).isNotNull();
        assertThat(segundo.getId()).isEqualTo(primero.getId());
    }

    @Test
    @Transactional
    void mayusculasYEspaciosNoCreanOtroUsuario() {
        Usuario original = registro.obtenerOCrear("zz.juan");

        assertThat(registro.obtenerOCrear("  ZZ.Juan ").getId()).isEqualTo(original.getId());
        assertThat(registro.buscar("ZZ.JUAN")).map(Usuario::getId).contains(original.getId());
    }

    @Test
    @Transactional
    void losIdsSonSecuenciales() {
        long a = registro.obtenerOCrear("zz.sec.a").getId();
        long b = registro.obtenerOCrear("zz.sec.b").getId();

        assertThat(b).isGreaterThan(a);
    }

    @Test
    @Transactional
    void obtenerVariosCreaSoloLosQueFaltan() {
        Usuario existente = registro.obtenerOCrear("zz.existe");

        Map<String, Usuario> todos = registro.obtenerOCrearTodos(List.of("ZZ.EXISTE", "zz.falta", "zz.falta"));

        assertThat(todos).containsOnlyKeys("zz.existe", "zz.falta");
        assertThat(todos.get("zz.existe").getId()).isEqualTo(existente.getId());
        assertThat(todos.get("zz.falta").getId()).isNotNull();
    }

    @Test
    @Transactional
    void elUsuarioDeLosProcesosAutomaticosExisteDesdeLaMigracion() {
        assertThat(registro.buscar(RegistroUsuarios.USUARIO_SISTEMA)).isPresent();
        assertThat(registro.sistema().getUsernameAd()).isEqualToIgnoringCase("SISTEMA");
    }

    @Test
    @Transactional
    void unCodigoVacioSeRechaza() {
        assertThatThrownBy(() -> registro.obtenerOCrear("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(registro.buscar(" ")).isEmpty();
    }

    /** Dos peticiones a la vez con un usuario nuevo: ninguna falla y ambas reciben el MISMO usuario. */
    @RepeatedTest(20)
    void dosPeticionesSimultaneasNoDuplicanNiFallan() throws Exception {
        String codigo = "zz.simultaneo." + UUID.randomUUID().toString().substring(0, 8);
        TransactionTemplate enTransaccion = new TransactionTemplate(transacciones);
        CountDownLatch salida = new CountDownLatch(1);
        Callable<Long> peticion = () -> {
            salida.await();
            return enTransaccion.execute(estado -> registro.obtenerOCrear(codigo).getId());
        };
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<Long> uno = hilos.submit(peticion);
            Future<Long> dos = hilos.submit(peticion);
            salida.countDown();

            assertThat(uno.get()).isNotNull().isEqualTo(dos.get());
        } finally {
            hilos.shutdownNow();
            enTransaccion.executeWithoutResult(estado -> em.createNativeQuery("DELETE FROM USUARIOS WHERE USERNAME_AD = ?1")
                    .setParameter(1, codigo).executeUpdate());
        }
    }
}
