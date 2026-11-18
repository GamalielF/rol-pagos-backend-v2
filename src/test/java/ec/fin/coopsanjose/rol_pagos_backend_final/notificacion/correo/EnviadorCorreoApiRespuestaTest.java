package ec.fin.coopsanjose.rol_pagos_backend_final.notificacion.correo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cómo se interpreta lo que responde la API de correo, que contesta HTTP 200 aunque el envío haya fallado. */
class EnviadorCorreoApiRespuestaTest {

    @Test
    void laRespuestaRealDeLaApiCuandoEnviaSeAcepta() {
        String real = "{\"status\":\"200\",\"message\":\"Correo de Prueba enviado correctamente: NotificationResult("
                + "destinatario=fsepa@coopsanjose.fin.ec, success=true, providerMessageId=null, httpStatus=202)\"}";

        assertThat(EnviadorCorreoApi.problemaEnRespuesta(real)).isNull();
    }

    @Test
    void unEstadoDeErrorDentroDelEnvoltorioSeRechaza() {
        assertThat(EnviadorCorreoApi.problemaEnRespuesta("{\"status\":\"500\",\"message\":\"Error interno\"}"))
                .contains("estado 500");
        assertThat(EnviadorCorreoApi.problemaEnRespuesta("{\"status\":400}")).contains("estado 400");
    }

    @Test
    void unResultadoFallidoDentroDelMensajeSeRechaza() {
        String fallo = "{\"status\":\"200\",\"message\":\"NotificationResult(destinatario=x@y.ec, success = false, errorMessage=Buzón inexistente)\"}";

        assertThat(EnviadorCorreoApi.problemaEnRespuesta(fallo)).isEqualTo("La API de correo indicó que el envío falló");
    }

    @Test
    void loQueNoEsUnErrorClaroSeDaPorAceptado() {
        assertThat(EnviadorCorreoApi.problemaEnRespuesta("{\"success\":true,\"status\":\"PROCESSING\"}")).isNull();
        assertThat(EnviadorCorreoApi.problemaEnRespuesta("OK, recibido")).isNull();
        assertThat(EnviadorCorreoApi.problemaEnRespuesta("")).isNull();
        assertThat(EnviadorCorreoApi.problemaEnRespuesta("[1,2]")).isNull();
    }

    @Test
    void elResumenParaElLogOcultaLosCorreosYSeAcota() {
        String resumen = EnviadorCorreoApi.resumen("{\"message\":\"enviado a aherrera@coopsanjose.fin.ec\n ok\"}");

        assertThat(resumen).isEqualTo("{\"message\":\"enviado a ah***@coopsanjose.fin.ec ok\"}");
        assertThat(EnviadorCorreoApi.resumen("x".repeat(700))).hasSize(601);
        assertThat(EnviadorCorreoApi.resumen(null)).isEqualTo("(vacía)");
    }

    @Test
    void enmascararDejaVerAQuienFueSinElCorreoCompleto() {
        assertThat(EnviadorCorreoApi.enmascarar("aherrera@coopsanjose.fin.ec")).isEqualTo("ah***@coopsanjose.fin.ec");
        assertThat(EnviadorCorreoApi.enmascarar("a@x.ec")).isEqualTo("***@x.ec");
        assertThat(EnviadorCorreoApi.enmascarar(null)).isEqualTo("-");
    }
}
