package servicio;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import conexion.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

public class GenerarCertificado {

    private static final DecimalFormat FORMATO = new DecimalFormat("#,##0");

    // Carpeta (en la raíz del proyecto) donde quedan los PDF generados
    public static final String CARPETA_SALIDA = "certificados_generados";

    // Usuario que queda registrado como generador mientras no exista inicio de sesión (tabla usuarios)
    public static final int ID_USUARIO_SISTEMA = 1;

    // Tabla estados: 2 = Generado
    private static final int ESTADO_GENERADO = 2;

    // Lo que devuelve la generación: el registro en la tabla certificados y el PDF creado
    public record Resultado(int idCertificado, String rutaPdf) { }

    public static void main(String[] args) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            int idContratista = obtenerUltimoIdContratista(conn);
            Resultado resultado = generarParaContratista(idContratista);
            System.out.println("Certificado generado: " + resultado.rutaPdf() +
                " (id_certificado " + resultado.idCertificado() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Resultado generarParaContratista(int idContratista) throws Exception {

        try (Connection conn = ConexionBD.obtenerConexion()) {

            String sql = "SELECT c.t_primer_apellido, c.t_segundo_apellido, c.t_nombres, " +
                "c.n_numero_documento, co.id_contrato, " +
                "co.n_salario, co.n_honorarios, co.n_servicios, co.n_comisiones, co.n_prestaciones, " +
                "co.n_pago_viaticos, co.n_gastos, co.n_otros_ingresos, co.n_cesantias_empleado, " +
                "co.n_cesantias_fondo, co.n_pensiones, co.n_total_ing_brutos, co.n_ret_ica, co.n_anio " +
                "FROM contratistas c JOIN contratos co ON c.id_contratista = co.id_contratista " +
                "WHERE c.id_contratista = ? " +
                "ORDER BY co.n_anio DESC LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, idContratista);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    throw new Exception("No se encontró el contratista con id " + idContratista);
                }

                String segundoApellido = rs.getString("t_segundo_apellido");
                String nombreCompleto = rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido") +
                    (segundoApellido == null ? "" : " " + segundoApellido);
                String documento = rs.getString("n_numero_documento");
                int anio = rs.getInt("n_anio");

                // Cargar plantilla
                String html;
                try (InputStream plantilla = GenerarCertificado.class.getResourceAsStream("/plantilla_certificado.html")) {
                    if (plantilla == null) {
                        throw new Exception("No se encontró plantilla_certificado.html en src/main/resources");
                    }
                    html = new String(plantilla.readAllBytes(), StandardCharsets.UTF_8);
                }

                LocalDate hoy = LocalDate.now();
                String mes = hoy.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));

                // Reemplazar placeholders
                html = html.replace("{{NOMBRE_COMPLETO}}", nombreCompleto.toUpperCase())
                           .replace("{{DOCUMENTO}}", documento)
                           .replace("{{ANIO}}", String.valueOf(anio))
                           .replace("{{ANIO_EXPEDICION}}", String.valueOf(hoy.getYear()))
                           .replace("{{DIA}}", String.valueOf(hoy.getDayOfMonth()))
                           .replace("{{MES}}", mes)
                           .replace("{{SALARIO}}", formatear(rs.getDouble("n_salario")))
                           .replace("{{HONORARIOS}}", formatear(rs.getDouble("n_honorarios")))
                           .replace("{{SERVICIOS}}", formatear(rs.getDouble("n_servicios")))
                           .replace("{{COMISIONES}}", formatear(rs.getDouble("n_comisiones")))
                           .replace("{{PRESTACIONES}}", formatear(rs.getDouble("n_prestaciones")))
                           .replace("{{VIATICOS}}", formatear(rs.getDouble("n_pago_viaticos")))
                           .replace("{{GASTOS_REP}}", formatear(rs.getDouble("n_gastos")))
                           .replace("{{OTROS_INGRESOS}}", formatear(rs.getDouble("n_otros_ingresos")))
                           .replace("{{CESANTIAS_EMPLEADO}}", formatear(rs.getDouble("n_cesantias_empleado")))
                           .replace("{{CESANTIAS_FONDO}}", formatear(rs.getDouble("n_cesantias_fondo")))
                           .replace("{{PENSIONES}}", formatear(rs.getDouble("n_pensiones")))
                           .replace("{{TOTAL_INGRESOS}}", formatear(rs.getDouble("n_total_ing_brutos")))
                           .replace("{{RET_ICA}}", formatear(rs.getDouble("n_ret_ica")));

                Files.createDirectories(Paths.get(CARPETA_SALIDA));
                String rutaSalida = CARPETA_SALIDA + "/certificado_" + documento + ".pdf";

                try (FileOutputStream os = new FileOutputStream(rutaSalida)) {
                    PdfRendererBuilder builder = new PdfRendererBuilder();
                    builder.withHtmlContent(html, null);
                    builder.toStream(os);
                    builder.run();
                }

                int idCertificado = registrarCertificado(conn, rs.getInt("id_contrato"), rutaSalida);
                return new Resultado(idCertificado, rutaSalida);
            }
        }
    }

    // Deja constancia del PDF en la tabla certificados (la necesita envio_certificado para el historial)
    private static int registrarCertificado(Connection conn, int idContrato, String rutaPdf) throws Exception {
        String sql = "INSERT INTO certificados " +
            "(id_contrato, id_usuario, id_estado, d_fecha_generacion, t_ruta_pdf, t_formato_certificado) " +
            "VALUES (?, ?, ?, CURDATE(), ?, 'PDF')";

        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idContrato);
            ps.setInt(2, ID_USUARIO_SISTEMA);
            ps.setInt(3, ESTADO_GENERADO);
            ps.setString(4, rutaPdf);
            ps.executeUpdate();

            try (ResultSet claves = ps.getGeneratedKeys()) {
                claves.next();
                return claves.getInt(1);
            }
        }
    }

    private static String formatear(double valor) {
        if (valor == 0) return "0";
        return "$" + FORMATO.format(valor);
    }

    private static int obtenerUltimoIdContratista(Connection conn) throws Exception {
        String sql = "SELECT MAX(id_contratista) AS ultimo FROM contratistas";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("ultimo");
        }
    }
}