package servicio;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;

import Seguridad.Autenticacion;
import Seguridad.EsquemaBD;
import Seguridad.GestionUsuarios;
import Seguridad.RegistroAuditoria;
import Seguridad.Roles;
import conexion.ConexionBD;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.session.SessionHandler;
import dao.EnvioCertificadoDAO;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {

    // Carpeta (en la raíz del proyecto) donde se guardan las sábanas subidas
    private static final Path CARPETA_SUBIDAS = Paths.get("subidas");

    // Se agrega a cada correo: el PDF va cifrado y el contratista necesita saber cómo abrirlo
    private static final String NOTA_CONTRASENA = "\n\nEl certificado adjunto está protegido. Para abrirlo, use como " +
        "contraseña su número de documento, sin puntos ni espacios.";

    public static void main(String[] args) {

        // 1) Probar la conexión antes de levantar el servidor, para ver de una vez si MySQL responde
        probarConexion();

        // 2) Crear lo que falte en la base de datos (roles, columnas nuevas)
        EsquemaBD.asegurar();

        Javalin app = Javalin.create(config -> {
            // Páginas, scripts y estilos: el navegador siempre pregunta si cambiaron (evita usar versiones viejas)
            config.staticFiles.add(archivos -> {
                archivos.directory = "/public";
                archivos.location = Location.CLASSPATH;
                archivos.headers = Map.of("Cache-Control", "no-cache");
            });

            // Cookie de sesión: no la puede leer JavaScript (HttpOnly), no viaja desde otros sitios
            // (SameSite=Strict) y vence tras 30 minutos sin uso
            config.jetty.modifyServletContextHandler(handler -> {
                SessionHandler sesiones = handler.getSessionHandler();
                if (sesiones == null) {
                    sesiones = new SessionHandler();
                    handler.setSessionHandler(sesiones);
                }
                sesiones.setHttpOnly(true);
                sesiones.setSameSite(HttpCookie.SameSite.STRICT);
                sesiones.setMaxInactiveInterval(30 * 60);
            });
        }).start(7000);

        // Cualquier error no controlado: se muestra en la consola y la página recibe el mensaje
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        });

        // La pantalla inicial es el login: http://localhost:7000 lleva directo a login.html
        app.get("/", ctx -> ctx.redirect("/login.html"));

        // Control de acceso por sesión y por rol (ver Seguridad.Roles):
        // sin sesión solo se ve el login; con sesión, cada rol solo entra a sus pantallas y a su API
        app.before(ctx -> {
            String ruta = ctx.path();
            if (esRutaPublica(ruta)) {
                return;
            }
            Autenticacion.UsuarioSesion usuario = usuarioActual(ctx);

            // Si el usuario fue borrado o desactivado mientras tenía la sesión abierta, se le cierra
            if (usuario != null && !Autenticacion.sigueActivo(usuario.idUsuario())) {
                ctx.req().getSession().invalidate();
                usuario = null;
            }

            if (usuario == null) {
                if (ruta.equals("/login.html")) {
                    return;
                }
                if (ruta.startsWith("/api/")) {
                    ctx.status(401).json(Map.of("error", "La sesión no está iniciada o venció. Inicie sesión de nuevo."));
                } else {
                    ctx.redirect("/login.html");
                }
                ctx.skipRemainingHandlers();
                return;
            }

            String inicio = Roles.INICIO.get(usuario.claveRol());
            if (ruta.equals("/login.html")) {
                ctx.redirect(inicio);
                ctx.skipRemainingHandlers();
                return;
            }
            if (!Roles.puede(usuario.claveRol(), ruta, ctx.method().name())) {
                if (ruta.startsWith("/api/")) {
                    ctx.status(403).json(Map.of("error", "Su rol (" + Roles.NOMBRES.get(usuario.claveRol()) +
                        ") no tiene permiso para esta acción."));
                } else {
                    ctx.redirect(inicio);
                }
                ctx.skipRemainingHandlers();
            }
        });

        // Iniciar sesión. Cuerpo JSON: { "username": "...", "password": "..." }
        app.post("/api/login", ctx -> {
            Map<?, ?> cuerpo = ctx.bodyAsClass(Map.class);
            Autenticacion.UsuarioSesion usuario;

            try {
                usuario = Autenticacion.validar(texto(cuerpo.get("username")), texto(cuerpo.get("password")));
            } catch (Autenticacion.UsuarioBloqueadoException e) {
                ctx.status(429).json(Map.of("error", e.getMessage()));
                return;
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("error", "No se pudo validar el usuario. Revise la conexión a la base de datos."));
                return;
            }

            if (usuario == null) {
                // Mismo mensaje para usuario inexistente, contraseña mala o usuario inactivo
                ctx.status(401).json(Map.of("error", "Usuario o contraseña incorrectos."));
                return;
            }

            // Sesión nueva al iniciar sesión (evita reutilizar un identificador de sesión anterior)
            if (ctx.req().getSession(false) != null) {
                ctx.req().getSession(false).invalidate();
            }
            ctx.sessionAttribute(SESION_USUARIO, usuario);
            RegistroAuditoria.registrar(usuario.idUsuario(), "Autenticación", "Inicio de sesión", "Acceso exitoso");

            // La página lleva a cada rol a su propia pantalla de inicio
            ctx.json(Map.of("nombre", usuario.nombre(), "rol", usuario.claveRol(),
                "inicio", Roles.INICIO.get(usuario.claveRol()).substring(1)));
        });

        // Cerrar sesión
        app.post("/api/logout", ctx -> {
            Autenticacion.UsuarioSesion usuario = usuarioActual(ctx);
            if (usuario != null) {
                RegistroAuditoria.registrar(usuario.idUsuario(), "Autenticación", "Cierre de sesión", "");
            }
            if (ctx.req().getSession(false) != null) {
                ctx.req().getSession(false).invalidate();
            }
            ctx.json(Map.of("ok", true));
        });

        // Quién inició sesión: datos, rol, funciones y pantalla de inicio (lo usan todas las páginas)
        app.get("/api/sesion", ctx -> {
            Autenticacion.UsuarioSesion usuario = usuarioActual(ctx);
            Map<String, Object> datos = new HashMap<>(GestionUsuarios.buscar(usuario.idUsuario()));
            datos.put("inicio", Roles.INICIO.get(usuario.claveRol()).substring(1));
            datos.remove("registros");
            ctx.json(datos);
        });

        // --- Perfil propio (cualquier rol) ---

        // Cuerpo JSON: { "nombre": "...", "correo": "...", "telefono": "..." }
        app.put("/api/perfil", ctx -> {
            Autenticacion.UsuarioSesion usuario = usuarioActual(ctx);
            Map<?, ?> cuerpo = ctx.bodyAsClass(Map.class);
            try {
                GestionUsuarios.actualizarPerfil(usuario.idUsuario(), texto(cuerpo.get("nombre")),
                    texto(cuerpo.get("correo")), texto(cuerpo.get("telefono")));
                RegistroAuditoria.registrar(usuario.idUsuario(), "Perfil", "Actualización de perfil", "Datos de contacto actualizados");
                ctx.json(Map.of("ok", true));
            } catch (GestionUsuarios.ReglaException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        // Cuerpo JSON: { "actual": "...", "nueva": "..." }
        app.put("/api/perfil/password", ctx -> {
            Autenticacion.UsuarioSesion usuario = usuarioActual(ctx);
            Map<?, ?> cuerpo = ctx.bodyAsClass(Map.class);
            try {
                GestionUsuarios.cambiarPasswordPropia(usuario.idUsuario(), texto(cuerpo.get("actual")), texto(cuerpo.get("nueva")));
                RegistroAuditoria.registrar(usuario.idUsuario(), "Perfil", "Cambio de contraseña", "El usuario cambió su contraseña");
                ctx.json(Map.of("ok", true));
            } catch (GestionUsuarios.ReglaException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        // --- Usuarios (solo Administrador; lo valida Roles.puede) ---

        app.get("/api/usuarios", ctx -> {
            ctx.json(Map.of("usuarios", GestionUsuarios.listar(), "idActual", usuarioActual(ctx).idUsuario(),
                "roles", Roles.NOMBRES, "funciones", Roles.FUNCIONES,
                "longitudMinima", GestionUsuarios.LONGITUD_MINIMA_PASSWORD));
        });

        // Cuerpo JSON: { nombre, username, correo, telefono, rol, password }
        app.post("/api/usuarios", ctx -> {
            Map<?, ?> c = ctx.bodyAsClass(Map.class);
            try {
                int id = GestionUsuarios.crear(texto(c.get("nombre")), texto(c.get("username")), texto(c.get("correo")),
                    texto(c.get("telefono")), texto(c.get("rol")), texto(c.get("password")));
                RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), "Usuarios", "Creación de usuario",
                    texto(c.get("username")) + " · rol " + Roles.NOMBRES.get(texto(c.get("rol"))));
                ctx.json(Map.of("ok", true, "id", id));
            } catch (GestionUsuarios.ReglaException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        // Cuerpo JSON: { rol, activo, password (opcional: contraseña nueva) }
        app.put("/api/usuarios/{id}", ctx -> {
            Map<?, ?> c = ctx.bodyAsClass(Map.class);
            try {
                String cambios = GestionUsuarios.actualizar(Integer.parseInt(ctx.pathParam("id")), texto(c.get("rol")),
                    Boolean.TRUE.equals(c.get("activo")), texto(c.get("password")), usuarioActual(ctx).idUsuario());
                RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), "Usuarios", "Modificación de usuario", cambios);
                ctx.json(Map.of("ok", true));
            } catch (GestionUsuarios.ReglaException e) {
                ctx.status(409).json(Map.of("error", e.getMessage()));
            }
        });

        app.delete("/api/usuarios/{id}", ctx -> {
            try {
                String username = GestionUsuarios.eliminar(Integer.parseInt(ctx.pathParam("id")), usuarioActual(ctx).idUsuario());
                RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), "Usuarios", "Eliminación de usuario", username);
                ctx.json(Map.of("ok", true));
            } catch (GestionUsuarios.ReglaException e) {
                ctx.status(409).json(Map.of("error", e.getMessage()));
            }
        });

        // --- Auditorías ---

        // Últimos 5.000 eventos (solo Administrador)
        app.get("/api/auditorias", ctx -> ctx.json(RegistroAuditoria.listar(5000)));

        // Eventos que registra una página (por ejemplo, exportar a CSV). Cuerpo: { modulo, accion, detalle }
        app.post("/api/auditoria/evento", ctx -> {
            Map<?, ?> c = ctx.bodyAsClass(Map.class);
            String detalle = texto(c.get("detalle"));
            RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), texto(c.get("modulo")), texto(c.get("accion")),
                detalle.length() > 1000 ? detalle.substring(0, 1000) : detalle);
            ctx.json(Map.of("ok", true));
        });

        // Estado de la conexión (sirve para probar desde el navegador)
        app.get("/api/conexion", ctx -> {
            try (Connection conn = ConexionBD.obtenerConexion()) {
                ctx.json(Map.of("conectado", true));
            } catch (Exception e) {
                ctx.status(500).json(Map.of("conectado", false, "error", e.getMessage()));
            }
        });

        // Subir la sábana: guarda el archivo en la carpeta "subidas" y devuelve el nombre guardado
        app.post("/api/sabana", ctx -> {
            UploadedFile archivo = ctx.uploadedFile("archivo");

            if (archivo == null) {
                ctx.status(400).json(Map.of("error", "No se recibió ningún archivo"));
                return;
            }
            if (!archivo.filename().toLowerCase().endsWith(".xlsx")) {
                ctx.status(400).json(Map.of("error", "La sábana debe ser un archivo Excel .xlsx"));
                return;
            }

            Files.createDirectories(CARPETA_SUBIDAS);

            // Solo el nombre (sin carpetas) y con fecha delante para no sobreescribir
            String nombreOriginal = Paths.get(archivo.filename()).getFileName().toString();
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreGuardado = fecha + "_" + nombreOriginal;

            try (InputStream contenido = archivo.content()) {
                Files.copy(contenido, CARPETA_SUBIDAS.resolve(nombreGuardado), StandardCopyOption.REPLACE_EXISTING);
            }

            ctx.json(Map.of("archivo", nombreGuardado, "nombre", nombreOriginal));
        });

        // Ejecutar el CargueMasivo sobre una sábana ya subida
        app.post("/api/sabana/{archivo}/cargar", ctx -> {
            // getFileName() evita que se pidan archivos fuera de la carpeta "subidas"
            Path ruta = CARPETA_SUBIDAS.resolve(Paths.get(ctx.pathParam("archivo")).getFileName());

            if (!Files.exists(ruta)) {
                ctx.status(404).json(Map.of("error", "No existe el archivo subido " + ruta.getFileName()));
                return;
            }

            try {
                Map<String, Object> resultado = CargueMasivo.cargar(ruta);
                List<?> errores = (List<?>) resultado.get("errores");
                if (Boolean.TRUE.equals(resultado.get("rechazada"))) {
                    // Datos duplicados: no se guardó nada; la página muestra cada repetición
                    RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), "Certificados", "Sábana rechazada",
                        ruta.getFileName() + " · " + errores.size() + " dato(s) duplicado(s)");
                    ctx.status(422).json(Map.of("error", "La sábana tiene datos duplicados y no se cargó. Corrija el archivo y súbalo de nuevo.",
                        "errores", errores));
                    return;
                }
                RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), "Certificados", "Carga de sábana",
                    ruta.getFileName() + " · " + resultado.get("insertados") + " nuevos, " + resultado.get("actualizados") +
                    " actualizados, " + errores.size() + " con error");
                ctx.json(resultado);

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });

        // Lista de contratistas desde la base de datos
        app.get("/api/contratistas", ctx -> {
            List<Map<String, Object>> lista = new ArrayList<>();

            try (Connection conn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT id_contratista, t_nombres, t_primer_apellido, t_segundo_apellido, n_numero_documento " +
                     "FROM contratistas ORDER BY t_primer_apellido, t_nombres");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String segundoApellido = rs.getString("t_segundo_apellido");

                    Map<String, Object> c = new HashMap<>();
                    c.put("id", rs.getInt("id_contratista"));
                    c.put("nombre", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido") +
                        (segundoApellido == null ? "" : " " + segundoApellido));
                    c.put("documento", rs.getString("n_numero_documento"));
                    lista.add(c);
                }

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("error", e.getMessage()));
                return;
            }

            ctx.json(lista);
        });

        // Generar certificado para un contratista específico
        app.post("/api/certificados/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));

            try {
                GenerarCertificado.Resultado resultado = GenerarCertificado.generarParaContratista(id, usuarioActual(ctx).idUsuario());
                String nombrePdf = Paths.get(resultado.rutaPdf()).getFileName().toString();
                RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), "Certificados", "Generación de certificado",
                    nombrePdf + " · certificado " + resultado.idCertificado());
                Map<String, Object> datos = new EnvioCertificadoDAO().datosParaEnvio(resultado.idCertificado());

                Map<String, Object> respuesta = new HashMap<>();
                respuesta.put("exito", true);
                respuesta.put("idCertificado", resultado.idCertificado());
                respuesta.put("archivo", nombrePdf);
                respuesta.put("url", "/api/certificados/pdf/" + nombrePdf);
                respuesta.put("correo", datos.get("correo"));
                ctx.json(respuesta);

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("exito", false, "error", e.getMessage()));
            }
        });

        // --- Certificados devueltos (no se editan: se vuelven a generar) ---

        app.get("/api/devoluciones", ctx -> ctx.json(Devoluciones.listar()));

        // Cuerpo JSON: { documento, motivo }
        app.post("/api/devoluciones", ctx -> {
            Map<?, ?> c = ctx.bodyAsClass(Map.class);
            int idUsuario = usuarioActual(ctx).idUsuario();
            try {
                int id = Devoluciones.registrar(texto(c.get("documento")), texto(c.get("motivo")), idUsuario);
                RegistroAuditoria.registrar(idUsuario, "Certificados", "Devolución de certificado",
                    "Certificado " + id + " · documento " + texto(c.get("documento")) + " · " + texto(c.get("motivo")));
                ctx.json(Map.of("ok", true, "idCertificado", id));
            } catch (Devoluciones.ReglaException e) {
                ctx.status(409).json(Map.of("error", e.getMessage(), "codigo", e.codigo()));
            }
        });

        // Cuerpo JSON: { forzar: true|false }  (forzar: regenerar aunque los datos no hayan cambiado)
        app.post("/api/devoluciones/{id}/regenerar", ctx -> {
            int idDevuelto = Integer.parseInt(ctx.pathParam("id"));
            Map<?, ?> c = ctx.body().isBlank() ? Map.of() : ctx.bodyAsClass(Map.class);
            int idUsuario = usuarioActual(ctx).idUsuario();
            try {
                Map<String, Object> r = Devoluciones.regenerar(idDevuelto, Boolean.TRUE.equals(c.get("forzar")), idUsuario);
                RegistroAuditoria.registrar(idUsuario, "Certificados", "Regeneración de certificado",
                    "Certificado devuelto " + idDevuelto + " reemplazado por " + r.get("idCertificado") + " · " + r.get("archivo") +
                    (Boolean.TRUE.equals(r.get("sinCambios")) ? " · sin cambios en los datos (confirmado)" : ""));
                ctx.json(r);
            } catch (Devoluciones.ReglaException e) {
                ctx.status(409).json(Map.of("error", e.getMessage(), "codigo", e.codigo()));
            }
        });

        // --- Firma (Jefe de Nómina y Gestión Humana; lo valida Roles.puede) ---

        // Firma PNG del usuario con sesión: { tiene, imagen (data URI) }
        app.get("/api/firma/mi-firma", ctx -> {
            byte[] png = Firmas.obtenerFirma(usuarioActual(ctx).idUsuario());
            Map<String, Object> r = new HashMap<>();
            r.put("tiene", png != null);
            r.put("imagen", png == null ? null : "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png));
            ctx.json(r);
        });

        // Cuerpo JSON: { imagen: "data:image/png;base64,..." }
        app.put("/api/firma/mi-firma", ctx -> {
            String imagen = texto(ctx.bodyAsClass(Map.class).get("imagen"));
            int idUsuario = usuarioActual(ctx).idUsuario();
            try {
                if (!imagen.startsWith("data:image/png;base64,")) throw new Firmas.ReglaException("La firma debe ser una imagen PNG.");
                Firmas.guardarFirma(idUsuario, java.util.Base64.getDecoder().decode(imagen.substring(imagen.indexOf(',') + 1)));
                RegistroAuditoria.registrar(idUsuario, "Firma digital", "Registro de firma", "Firma PNG actualizada");
                ctx.json(Map.of("ok", true));
            } catch (Firmas.ReglaException | IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            }
        });

        app.delete("/api/firma/mi-firma", ctx -> {
            int idUsuario = usuarioActual(ctx).idUsuario();
            Firmas.eliminarFirma(idUsuario);
            RegistroAuditoria.registrar(idUsuario, "Firma digital", "Eliminación de firma", "Firma PNG eliminada");
            ctx.json(Map.of("ok", true));
        });

        // Pendientes de firma y últimos firmados
        app.get("/api/firma/pendientes", ctx -> {
            Map<String, Object> r = new HashMap<>();
            r.put("pendientes", Firmas.pendientes());
            r.put("recientes", Firmas.firmadosRecientes(30));
            r.put("tieneFirma", Firmas.obtenerFirma(usuarioActual(ctx).idUsuario()) != null);
            ctx.json(r);
        });

        // Cuerpo JSON: { ids: [1, 2, 3] }
        app.post("/api/firma/firmar", ctx -> {
            List<?> lista = (List<?>) ctx.bodyAsClass(Map.class).get("ids");
            int idUsuario = usuarioActual(ctx).idUsuario();
            if (lista == null || lista.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No se seleccionó ningún certificado."));
                return;
            }
            List<Integer> ids = new ArrayList<>();
            for (Object o : lista) ids.add(((Number) o).intValue());
            try {
                List<Map<String, Object>> resultados = Firmas.firmar(ids, idUsuario);
                long firmados = resultados.stream().filter(x -> Boolean.TRUE.equals(x.get("exito"))).count();
                StringBuilder nombres = new StringBuilder();
                for (Map<String, Object> x : resultados) {
                    if (Boolean.TRUE.equals(x.get("exito"))) nombres.append(nombres.length() > 0 ? ", " : "").append(x.get("contratista"));
                }
                RegistroAuditoria.registrar(idUsuario, "Firma digital", "Firma de certificados",
                    firmados + " firmado(s), " + (resultados.size() - firmados) + " con error" +
                    (nombres.length() > 0 ? " · " + nombres : ""));
                ctx.json(Map.of("resultados", resultados));
            } catch (Firmas.ReglaException e) {
                ctx.status(409).json(Map.of("error", e.getMessage()));
            }
        });

        // Lo que hicieron los usuarios de Nómina (para el Jefe y Gestión Humana)
        app.get("/api/historial-nomina", ctx -> {
            List<Map<String, Object>> eventos = new ArrayList<>();
            for (Map<String, Object> e : RegistroAuditoria.listar(5000)) {
                if (Roles.NOMINA.equals(e.get("rol"))) eventos.add(e);
            }
            ctx.json(eventos);
        });

        // --- Nómina: listos para enviar y dashboard ---

        app.get("/api/certificados/listos", ctx -> ctx.json(Firmas.listosParaEnviar()));

        app.get("/api/dashboard", ctx -> {
            String anio = ctx.queryParam("anio");
            ctx.json(Dashboard.resumen(anio == null || anio.isBlank() ? null : Integer.valueOf(anio)));
        });

        // Ver / descargar un PDF generado
        app.get("/api/certificados/pdf/{archivo}", ctx -> {
            Path ruta = Paths.get(GenerarCertificado.CARPETA_SALIDA)
                .resolve(Paths.get(ctx.pathParam("archivo")).getFileName());

            if (!Files.exists(ruta)) {
                ctx.status(404).result("No existe el certificado " + ruta.getFileName());
                return;
            }

            ctx.contentType("application/pdf");
            ctx.result(Files.newInputStream(ruta));
        });

        // Enviar por correo uno o varios certificados ya generados.
        // Cuerpo JSON: { "certificados": [1, 2], "cc": "", "asunto": "...", "mensaje": "..." }
        app.post("/api/correos/enviar", ctx -> {
            Map<?, ?> cuerpo = ctx.bodyAsClass(Map.class);
            List<?> ids = (List<?>) cuerpo.get("certificados");
            String cc = texto(cuerpo.get("cc"));
            String asunto = texto(cuerpo.get("asunto"));
            String mensaje = texto(cuerpo.get("mensaje"));

            if (ids == null || ids.isEmpty()) {
                ctx.status(400).json(Map.of("error", "No se indicó ningún certificado para enviar"));
                return;
            }

            CorreoServicio correo;
            try {
                correo = new CorreoServicio();
            } catch (Exception e) {
                ctx.status(500).json(Map.of("error", e.getMessage()));
                return;
            }

            EnvioCertificadoDAO envios = new EnvioCertificadoDAO();
            List<Map<String, Object>> resultados = new ArrayList<>();

            // Uno por uno y con pausa: Exchange limita los correos por minuto.
            // Si uno falla se registra el error y se sigue con los demás.
            for (int i = 0; i < ids.size(); i++) {
                int idCertificado = ((Number) ids.get(i)).intValue();
                Map<String, Object> resultado = new HashMap<>();
                resultado.put("idCertificado", idCertificado);

                Map<String, Object> datos = envios.datosParaEnvio(idCertificado);
                if (datos == null) {
                    resultado.put("exito", false);
                    resultado.put("error", "No existe el certificado " + idCertificado);
                    resultados.add(resultado);
                    continue;
                }

                String para = (String) datos.get("correo");
                resultado.put("contratista", datos.get("nombre"));

                // Solo se envían certificados firmados por el Jefe de Nómina o Gestión Humana
                int estado = (Integer) datos.get("estado");
                if (estado != Firmas.ESTADO_FIRMADO && estado != Firmas.ESTADO_ENVIADO) {
                    resultado.put("exito", false);
                    resultado.put("error", "todavía no está firmado por el Jefe de Nómina; no se envió");
                    resultados.add(resultado);
                    continue;
                }
                resultado.put("destino", correo.destinoEfectivo(para));

                try {
                    correo.enviar(para, cc, asunto, mensaje + NOTA_CONTRASENA, Paths.get((String) datos.get("rutaPdf")));
                    envios.registrar(idCertificado, correo.destinoEfectivo(para), true, null, usuarioActual(ctx).idUsuario());
                    resultado.put("exito", true);

                } catch (Exception e) {
                    envios.registrar(idCertificado, para, false, e.getMessage(), usuarioActual(ctx).idUsuario());
                    resultado.put("exito", false);
                    resultado.put("error", e.getMessage());
                }

                resultados.add(resultado);

                if (i < ids.size() - 1) {
                    correo.pausa();
                }
            }

            long enviados = resultados.stream().filter(r -> Boolean.TRUE.equals(r.get("exito"))).count();
            RegistroAuditoria.registrar(usuarioActual(ctx).idUsuario(), "Certificados", "Envío de certificados",
                enviados + " enviado(s), " + (resultados.size() - enviados) + " con error" +
                (correo.modoPrueba() ? " (modo prueba)" : ""));
            ctx.json(Map.of("modoPrueba", correo.modoPrueba(), "resultados", resultados));
        });

        // Historial de correos enviados (tabla envio_certificado)
        app.get("/api/correos", ctx -> {
            try {
                ctx.json(new EnvioCertificadoDAO().listar());
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("error", e.getMessage()));
            }
        });

        System.out.println("Servidor corriendo en http://localhost:7000");
    }

    private static final String SESION_USUARIO = "usuario";

    static Autenticacion.UsuarioSesion usuarioActual(io.javalin.http.Context ctx) {
        return ctx.sessionAttribute(SESION_USUARIO);
    }

    // Lo que se ve sin iniciar sesión: el login y los archivos de diseño (imágenes, estilos, scripts)
    private static boolean esRutaPublica(String ruta) {
        return ruta.equals("/") || ruta.equals("/api/login") || ruta.equals("/favicon.ico")
            || ruta.startsWith("/images/") || ruta.startsWith("/css/") || ruta.startsWith("/js/");
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static void probarConexion() {
        System.out.println("Probando conexión a " + ConexionBD.describirConexion() + " ...");
        try (Connection conn = ConexionBD.obtenerConexion()) {
            System.out.println("Conexión exitosa a la base de datos");
        } catch (Exception e) {
            System.out.println("ATENCIÓN: no se pudo conectar a la base de datos: " + e.getMessage());
            System.out.println("Revisa src/main/resources/config.properties y que MySQL esté encendido.");
        }
    }
}
