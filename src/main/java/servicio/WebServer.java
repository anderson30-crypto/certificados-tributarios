package servicio;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;

import conexion.ConexionBD;

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

    public static void main(String[] args) {

        // 1) Probar la conexión antes de levantar el servidor, para ver de una vez si MySQL responde
        probarConexion();

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
        }).start(7000);

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

            try (InputStream excel = Files.newInputStream(ruta)) {
                ctx.json(CargueMasivo.cargar(excel));

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
                String rutaPdf = GenerarCertificado.generarParaContratista(id);
                String nombrePdf = Paths.get(rutaPdf).getFileName().toString();
                ctx.json(Map.of("exito", true, "archivo", nombrePdf, "url", "/api/certificados/pdf/" + nombrePdf));

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("exito", false, "error", e.getMessage()));
            }
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

        System.out.println("Servidor corriendo en http://localhost:7000");
    }

    private static void probarConexion() {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            System.out.println("Conexión exitosa a la base de datos");
        } catch (Exception e) {
            System.out.println("ATENCIÓN: no se pudo conectar a la base de datos: " + e.getMessage());
            System.out.println("Revisa src/main/resources/config.properties y que MySQL esté encendido.");
        }
    }
}
