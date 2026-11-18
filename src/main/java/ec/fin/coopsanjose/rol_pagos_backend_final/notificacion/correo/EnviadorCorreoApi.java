package ec.fin.coopsanjose.rol_pagos_backend_final.notificacion.correo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ec.fin.coopsanjose.rol_pagos_backend_final.notificacion.config.CorreoIndividualProperties;
import ec.fin.coopsanjose.rol_pagos_backend_final.notificacion.config.CorreoProperties;
import ec.fin.coopsanjose.rol_pagos_backend_final.notificacion.config.EnvioCorreoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Envío mediante la API de correo de la cooperativa. Manda un aviso por llamada, en el formato
 * {"notifications":[{...}]}. La API acepta el lote y lo procesa después: un 200 significa "aceptado",
 * salvo que el cuerpo de la respuesta diga que falló (ver {@link #problemaEnRespuesta}).
 * <p>
 * Cada envío deja en el log a quién se pidió (con la dirección parcialmente oculta) y un resumen de lo que respondió
 * la API: sin eso no hay forma de saber si un correo que no llegó se quedó en el sistema o en la API.
 */
@Slf4j
public class EnviadorCorreoApi implements EnviadorCorreoConVariables {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int LARGO_RESUMEN = 600;
    private static final Pattern CORREO = Pattern.compile("([A-Za-z0-9._%+-]{1,2})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+)");

    record Notificacion(@JsonProperty("channel") String channel,
                        @JsonProperty("destinatario") String destinatario,
                        @JsonProperty("asunto") String asunto,
                        @JsonProperty("mensaje") String mensaje,
                        @JsonProperty("codigoPlantilla") @JsonInclude(JsonInclude.Include.NON_NULL) String codigoPlantilla,
                        @JsonProperty("variables") @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> variables) {
    }

    record SolicitudEnvio(@JsonProperty("notifications") List<Notificacion> notifications) {
    }

    private final CorreoProperties.Api api;
    private final RestClient restClient;
    private final EnvioCorreoProperties envio;
    private final CorreoIndividualProperties individual;

    public EnviadorCorreoApi(CorreoProperties.Api api, RestClient restClient) {
        this(api, restClient, EnvioCorreoProperties.SIN_AJUSTES);
    }

    public EnviadorCorreoApi(CorreoProperties.Api api, RestClient restClient, EnvioCorreoProperties envio) {
        this(api, restClient, envio, CorreoIndividualProperties.SIN_CONFIGURAR);
    }

    public EnviadorCorreoApi(CorreoProperties.Api api, RestClient restClient, EnvioCorreoProperties envio,
                             CorreoIndividualProperties individual) {
        this.api = api;
        this.restClient = restClient;
        this.envio = envio;
        this.individual = individual == null ? CorreoIndividualProperties.SIN_CONFIGURAR : individual;
        if (envio.redirige()) {
            log.warn("REDIRECCION DE CORREO ACTIVA: todos los avisos saldran a una unica direccion de prueba");
        }
    }

    @Override
    public boolean estaConfigurado() {
        return tieneTexto(api.url());
    }

    @Override
    public void enviar(String destinatario, String asunto, String texto, String referencia) {
        enviar(destinatario, asunto, texto, referencia, Map.of());
    }

    /**
     * Reenvía a una sola persona con el servicio individual de la API (.../send-prueba?destinatario=...). Sin esa URL
     * configurada usa el envío masivo con un solo aviso. El contenido lo define la plantilla de la API.
     */
    @Override
    public void reenviar(String destinatario, String asunto, String texto, String referencia,
                         Map<String, String> variables) {
        if (!individual.configurada()) {
            enviar(destinatario, asunto, texto, referencia, variables);
            return;
        }
        String problema = ValidadorConfiguracionCorreo.problemaDeUrl(individual.url());
        if (problema == null) {
            problema = ValidadorConfiguracionCorreo.problemaDeToken(api.token());
        }
        if (problema != null) {
            throw new EnvioCorreoException(problema, true, null);
        }
        HttpMethod metodo = metodoIndividual();
        String para = envio.redirige() ? envio.redirigirA().trim() : destinatario;
        log.info("Reenvío solicitado a la API individual para {}{}", enmascarar(para),
                envio.redirige() ? " (REDIRIGIDO)" : "");
        String respuesta = ejecutarIndividual(metodo, () -> restClient.method(metodo)
                .uri(direccionIndividual(para))
                .headers(cabeceras -> {
                    if (tieneTexto(api.token())) {
                        cabeceras.set(api.cabeceraAutorizacion(),
                                tieneTexto(api.esquema()) ? api.esquema() + " " + api.token() : api.token());
                    }
                    if (tieneTexto(api.cabeceraIdempotencia()) && referencia != null) {
                        cabeceras.set(api.cabeceraIdempotencia(), referencia);
                    }
                })
                .retrieve()
                .body(String.class));
        log.info("Respuesta de la API individual: {}", resumen(respuesta));
        verificarRespuesta(respuesta);
    }

    private URI direccionIndividual(String destinatario) {
        String parametro = tieneTexto(individual.parametroDestinatario()) ? individual.parametroDestinatario().trim() : "destinatario";
        return UriComponentsBuilder.fromUriString(individual.url().trim()).queryParam(parametro, destinatario).build().encode().toUri();
    }

    private HttpMethod metodoIndividual() {
        String metodo = tieneTexto(individual.metodo()) ? individual.metodo().trim().toUpperCase(Locale.ROOT) : "POST";
        if (!metodo.equals("GET") && !metodo.equals("POST")) {
            throw new EnvioCorreoException("El método de la API individual de correo debe ser GET o POST", false, null);
        }
        return HttpMethod.valueOf(metodo);
    }

    private String ejecutarIndividual(HttpMethod metodo, Supplier<String> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 405) {
                throw new EnvioCorreoException("La API de correo individual no acepta el método " + metodo
                        + " (HTTP 405): cambie app.correo.individual.metodo a " + (metodo == HttpMethod.GET ? "POST" : "GET"),
                        false, e);
            }
            throw clasificarEstado(e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw clasificarRed(e);
        } catch (RestClientException e) {
            throw new EnvioCorreoException("La API de correo respondió de forma inesperada", true, e);
        } catch (EnvioCorreoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EnvioCorreoException("No se pudo preparar el reenvío (" + e.getClass().getSimpleName()
                    + "): revise la URL y la configuración de la API de correo", true, e);
        }
    }

    @Override
    public void enviar(String destinatario, String asunto, String texto, String referencia,
                       Map<String, String> variables) {
        if (!estaConfigurado()) {
            throw new EnvioCorreoException("La API de correo no está configurada", true, null);
        }
        String problema = ValidadorConfiguracionCorreo.problemaDeUrl(api.url());
        if (problema == null) {
            problema = ValidadorConfiguracionCorreo.problemaDeToken(api.token());
        }
        if (problema != null) {
            throw new EnvioCorreoException(problema, true, null);
        }
        String respuesta;
        try {
            respuesta = restClient.post()
                    .uri(api.url().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(cabeceras -> {
                        if (tieneTexto(api.token())) {
                            cabeceras.set(api.cabeceraAutorizacion(),
                                    tieneTexto(api.esquema()) ? api.esquema() + " " + api.token() : api.token());
                        }
                        if (tieneTexto(api.cabeceraIdempotencia()) && referencia != null) {
                            cabeceras.set(api.cabeceraIdempotencia(), referencia);   // evita duplicados al reintentar
                        }
                    })
                    .body(new SolicitudEnvio(List.of(armar(destinatario, asunto, texto, variables))))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw clasificarEstado(e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw clasificarRed(e);
        } catch (RestClientException e) {
            throw new EnvioCorreoException("La API de correo respondió de forma inesperada", true, e);
        } catch (EnvioCorreoException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EnvioCorreoException("No se pudo preparar el envío (" + e.getClass().getSimpleName()
                    + "): revise la URL y la configuración de la API de correo", true, e);
        }
        log.info("Respuesta de la API masiva: {}", resumen(respuesta));
        verificarRespuesta(respuesta);
    }

    /** La redirección solo cambia a quién se envía: el asunto y el cuerpo viajan tal cual, porque los define la plantilla de la API. */
    private Notificacion armar(String destinatario, String asunto, String texto, Map<String, String> variablesDelAviso) {
        boolean redirigido = envio.redirige();
        String para = redirigido ? envio.redirigirA().trim() : destinatario;
        log.info("Correo solicitado a la API para {}{}", enmascarar(para),
                redirigido ? " (REDIRIGIDO; destinatario real " + enmascarar(destinatario) + ")" : "");
        Map<String, String> variables = null;
        if (envio.usaPlantilla()) {
            Map<String, String> mapa = new LinkedHashMap<>();
            Map<String, String> origen = variablesDelAviso == null ? Map.of() : variablesDelAviso;
            // Con redirección, la dirección real del empleado tampoco debe viajar dentro de las variables
            origen.forEach((nombre, valor) ->
                    mapa.put(nombre, redirigido && valor != null && valor.equals(destinatario) ? para : valor));
            mapa.putAll(envio.variables());   // lo fijado en la configuración tiene prioridad
            variables = mapa;
        }
        return new Notificacion(envio.canal(), para, asunto, texto,
                envio.usaPlantilla() ? envio.codigoPlantilla().trim() : null, variables);
    }

    /**
     * La API responde 200 aunque el trabajo se procese después. Se rechaza lo que ella misma marque como fallido;
     * cualquier otra cosa se da por aceptada, porque reintentar un correo ya enviado lo duplica.
     */
    private static void verificarRespuesta(String cuerpo) {
        String problema = problemaEnRespuesta(cuerpo);
        if (problema != null) {
            log.warn("La API de correo respondió con un fallo: {}", resumen(cuerpo));
            throw new EnvioCorreoException(problema, true, null);
        }
    }

    /**
     * Las tres formas en que la API avisa que algo falló, aunque el HTTP sea 200:
     * <ol>
     *   <li>{@code "success": false} en el primer nivel;</li>
     *   <li>un {@code "status"} numérico de error, como {@code "status": "500"} (la API envuelve sus respuestas así);</li>
     *   <li>el resultado del envío dentro del mensaje, como {@code "message": "... NotificationResult(... success=false ...)"}.</li>
     * </ol>
     * Un status no numérico ("PROCESSING") o un texto que no es JSON se dan por aceptados.
     *
     * @return el motivo del rechazo para RRHH (sin repetir lo que dijo la API), o null si se aceptó
     */
    static String problemaEnRespuesta(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        JsonNode nodo;
        try {
            nodo = JSON.readTree(cuerpo);
        } catch (RuntimeException e) {
            return null;
        }
        if (nodo == null || !nodo.isObject()) {
            return null;
        }
        JsonNode exito = nodo.get("success");
        if (exito != null && exito.isBoolean() && !exito.booleanValue()) {
            return "La API de correo indicó que no aceptó el envío";
        }
        Integer estado = estadoNumerico(nodo.get("status"));
        if (estado != null && estado >= 400) {
            return "La API de correo respondió con estado " + estado + " dentro de una respuesta HTTP 200";
        }
        JsonNode mensaje = nodo.get("message");
        if (mensaje != null && mensaje.isString() && mensaje.asString().replace(" ", "").contains("success=false")) {
            return "La API de correo indicó que el envío falló";
        }
        return null;
    }

    private static Integer estadoNumerico(JsonNode estado) {
        if (estado == null) {
            return null;
        }
        if (estado.isNumber()) {
            return estado.asInt();
        }
        if (estado.isString() && estado.asString().trim().matches("\\d{3}")) {
            return Integer.parseInt(estado.asString().trim());
        }
        return null;
    }

    private static EnvioCorreoException clasificarEstado(int codigo, Exception causa) {
        if (codigo == 401 || codigo == 403) {
            return new EnvioCorreoException("La API de correo rechazó las credenciales (HTTP " + codigo + ")", true, causa);
        }
        if (codigo == 404) {
            return new EnvioCorreoException("La ruta de la API de correo no existe (HTTP 404)", true, causa);
        }
        if (codigo == 408 || codigo == 429) {
            return new EnvioCorreoException("La API de correo pidió esperar (HTTP " + codigo + ")", true, causa);
        }
        if (codigo >= 500) {
            return new EnvioCorreoException("La API de correo respondió con un error interno (HTTP " + codigo + ")", true, causa);
        }
        // Otros 4xx (400, 422...): la solicitud o la dirección son inválidas; repetirla no la arregla
        return new EnvioCorreoException("La API de correo rechazó la solicitud (HTTP " + codigo + ")", false, causa);
    }

    private static EnvioCorreoException clasificarRed(Throwable error) {
        if (tiene(error, SSLException.class)) {
            return new EnvioCorreoException("No se pudo establecer una conexión segura con la API de correo", true, error);
        }
        if (tiene(error, HttpTimeoutException.class, SocketTimeoutException.class)) {
            return new EnvioCorreoException("La API de correo no respondió a tiempo", true, error);
        }
        if (tiene(error, ConnectException.class, UnknownHostException.class)) {
            return new EnvioCorreoException("No se pudo conectar con la API de correo", true, error);
        }
        return new EnvioCorreoException("Error de comunicación con la API de correo", true, error);
    }

    private static boolean tiene(Throwable raiz, Class<?>... tipos) {
        for (Throwable t = raiz; t != null; t = t.getCause()) {
            for (Class<?> tipo : tipos) {
                if (tipo.isInstance(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** aherrera@coopsanjose.fin.ec -> ah***@coopsanjose.fin.ec: se distingue a quién fue sin dejar el correo completo en el log. */
    static String enmascarar(String correo) {
        if (correo == null) {
            return "-";
        }
        int arroba = correo.indexOf('@');
        if (arroba < 2) {
            return "***" + (arroba >= 0 ? correo.substring(arroba) : "");
        }
        return correo.substring(0, 2) + "***" + correo.substring(arroba);
    }

    /** La respuesta en una sola línea, acotada y con los correos que traiga ocultos (lleva el del destinatario). */
    static String resumen(String respuesta) {
        if (respuesta == null || respuesta.isBlank()) {
            return "(vacía)";
        }
        String linea = CORREO.matcher(respuesta.replaceAll("\\s+", " ").trim()).replaceAll("$1***$2");
        return linea.length() > LARGO_RESUMEN ? linea.substring(0, LARGO_RESUMEN) + "…" : linea;
    }
}