package com.upb.integrador;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

public class GenerarCertificado2 {

    private static final DecimalFormat FORMATO = new DecimalFormat("#,##0");

    public static void main(String[] args) {
        try (Connection conn = ConexionBD2.obtenerConexion()) {
            int idContratista = obtenerUltimoIdContratista(conn);
            String rutaPdf = generarParaContratista(idContratista);
            System.out.println("Certificado generado: " + rutaPdf);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String generarParaContratista(int idContratista) throws Exception {

        try (Connection conn = ConexionBD2.obtenerConexion()) {

            String sql = "SELECT c.t_primer_apellido, c.t_segundo_apellido, c.t_nombres, " +
                "c.n_numero_documento, " +
                "co.n_salario, co.n_honorarios, co.n_servicios, co.n_comisiones, co.n_prestaciones, " +
                "co.n_pago_viaticos, co.n_gastos, co.n_otros_ingresos, co.n_cesantias_empleado, " +
                "co.n_cesantias_fondo, co.n_pensiones, co.n_total_ing_brutos, co.n_ret_ica, co.n_anio " +
                "FROM contratistas c JOIN contratos co ON c.id_contratista = co.id_contratista " +
                "WHERE c.id_contratista = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, idContratista);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    throw new Exception("No se encontró el contratista con id " + idContratista);
                }

                String nombreCompleto = rs.getString("t_nombres") + " " +
                    rs.getString("t_primer_apellido") + " " + rs.getString("t_segundo_apellido");
                String documento = rs.getString("n_numero_documento");
                int anio = rs.getInt("n_anio");

                // Cargar plantilla
                String html = Files.readString(Paths.get("src/main/resources/plantilla_certificado.html"));

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

                String rutaSalida = "src/main/resources/certificado_" + documento + ".pdf";

                try (FileOutputStream os = new FileOutputStream(rutaSalida)) {
                    PdfRendererBuilder builder = new PdfRendererBuilder();
                    builder.withHtmlContent(html, null);
                    builder.toStream(os);
                    builder.run();
                }

                return rutaSalida;
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