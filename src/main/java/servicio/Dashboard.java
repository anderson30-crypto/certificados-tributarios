package servicio;

import conexion.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Dashboard de Nómina: histórico por año de certificados generados, firmados, enviados,
 * devueltos y corregidos. El año es el de la fecha en que ocurrió cada cosa (generación,
 * firma, envío o devolución).
 */
public final class Dashboard {

    private Dashboard() { }

    public static Map<String, Object> resumen(Integer anioPedido) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {

            // Años con actividad (siempre incluye el actual)
            TreeSet<Integer> anios = new TreeSet<>();
            anios.add(LocalDate.now().getYear());
            String sqlAnios = "SELECT DISTINCT YEAR(d_fecha_generacion) FROM certificados WHERE d_fecha_generacion IS NOT NULL " +
                "UNION SELECT DISTINCT YEAR(d_fecha_envio) FROM envio_certificado WHERE d_fecha_envio IS NOT NULL";
            try (PreparedStatement ps = conn.prepareStatement(sqlAnios); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) anios.add(rs.getInt(1));
            }
            int anio = anioPedido != null ? anioPedido : anios.last();

            // Mes a mes: una fila por mes con cada indicador
            int[][] meses = new int[12][6];   // generados, corregidos, firmados, enviados, erroresEnvio, devueltos
            sumarPorMes(conn, meses, 0, "SELECT MONTH(d_fecha_generacion), COUNT(*) FROM certificados " +
                "WHERE YEAR(d_fecha_generacion) = ? AND b_certificado_corregido = 0 GROUP BY 1", anio);
            sumarPorMes(conn, meses, 1, "SELECT MONTH(d_fecha_generacion), COUNT(*) FROM certificados " +
                "WHERE YEAR(d_fecha_generacion) = ? AND b_certificado_corregido = 1 GROUP BY 1", anio);
            sumarPorMes(conn, meses, 2, "SELECT MONTH(d_fecha_firma), COUNT(*) FROM certificados " +
                "WHERE YEAR(d_fecha_firma) = ? GROUP BY 1", anio);
            sumarPorMes(conn, meses, 3, "SELECT MONTH(d_fecha_envio), COUNT(*) FROM envio_certificado " +
                "WHERE YEAR(d_fecha_envio) = ? AND b_envio_correo = 1 GROUP BY 1", anio);
            sumarPorMes(conn, meses, 4, "SELECT MONTH(d_fecha_envio), COUNT(*) FROM envio_certificado " +
                "WHERE YEAR(d_fecha_envio) = ? AND b_envio_correo = 0 GROUP BY 1", anio);
            sumarPorMes(conn, meses, 5, "SELECT MONTH(d_fecha_historial), COUNT(*) FROM historial_certificados " +
                "WHERE YEAR(d_fecha_historial) = ? AND t_accion = 'Devolución' GROUP BY 1", anio);

            String[] claves = { "generados", "corregidos", "firmados", "enviados", "erroresEnvio", "devueltos" };
            List<Map<String, Object>> porMes = new ArrayList<>();
            Map<String, Integer> totales = new LinkedHashMap<>();
            for (String c : claves) totales.put(c, 0);
            for (int m = 0; m < 12; m++) {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("mes", m + 1);
                for (int k = 0; k < claves.length; k++) {
                    fila.put(claves[k], meses[m][k]);
                    totales.merge(claves[k], meses[m][k], Integer::sum);
                }
                porMes.add(fila);
            }

            // Estado actual (no depende del año): lo que hay por hacer hoy
            Map<String, Integer> ahora = new LinkedHashMap<>();
            ahora.put("pendientesFirma", contar(conn, "SELECT COUNT(*) FROM certificados WHERE id_estado IN (?, ?)",
                Firmas.ESTADO_GENERADO, Firmas.ESTADO_CORREGIDO));
            ahora.put("listosParaEnviar", contar(conn, "SELECT COUNT(*) FROM certificados WHERE id_estado = ?",
                Firmas.ESTADO_FIRMADO));
            ahora.put("devueltosPendientes", contar(conn, "SELECT COUNT(*) FROM certificados ce WHERE ce.id_estado = 6 " +
                "AND NOT EXISTS (SELECT 1 FROM certificados n WHERE n.id_certificado_anterior = ce.id_certificado)"));

            // Últimos movimientos del año
            List<Map<String, Object>> recientes = new ArrayList<>();
            String sqlRecientes = "SELECT ce.id_certificado, ce.d_fecha_generacion, es.t_nombre_estado, ce.b_certificado_corregido, " +
                "ct.t_nombres, ct.t_primer_apellido, ct.n_numero_documento, co.n_anio " +
                "FROM certificados ce JOIN estados es ON es.id_estado = ce.id_estado " +
                "JOIN contratos co ON co.id_contrato = ce.id_contrato JOIN contratistas ct ON ct.id_contratista = co.id_contratista " +
                "WHERE YEAR(ce.d_fecha_generacion) = ? ORDER BY ce.id_certificado DESC LIMIT 15";
            try (PreparedStatement ps = conn.prepareStatement(sqlRecientes)) {
                ps.setInt(1, anio);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> c = new LinkedHashMap<>();
                        c.put("idCertificado", rs.getInt("id_certificado"));
                        c.put("contratista", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido"));
                        c.put("documento", GenerarCertificado.formatearDocumento(rs.getString("n_numero_documento")));
                        c.put("anioGravable", rs.getInt("n_anio"));
                        c.put("fecha", String.valueOf(rs.getDate("d_fecha_generacion")));
                        c.put("estado", rs.getString("t_nombre_estado"));
                        c.put("corregido", rs.getBoolean("b_certificado_corregido"));
                        recientes.add(c);
                    }
                }
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("anio", anio);
            r.put("anios", new ArrayList<>(anios.descendingSet()));
            r.put("totales", totales);
            r.put("ahora", ahora);
            r.put("porMes", porMes);
            r.put("recientes", recientes);
            return r;
        }
    }

    /** Historial de Nómina: certificados generados (los más recientes primero), sin los reemplazados. */
    public static List<Map<String, Object>> historial(int limite) throws SQLException {
        String sql = "SELECT ce.id_certificado, ce.d_fecha_generacion, ce.t_ruta_pdf, ce.b_certificado_corregido, es.t_nombre_estado, " +
            "ct.t_nombres, ct.t_primer_apellido, ct.t_segundo_apellido, ct.n_numero_documento, co.n_anio " +
            "FROM certificados ce JOIN estados es ON es.id_estado = ce.id_estado " +
            "JOIN contratos co ON co.id_contrato = ce.id_contrato JOIN contratistas ct ON ct.id_contratista = co.id_contratista " +
            "WHERE ce.id_estado <> 8 ORDER BY ce.id_certificado DESC LIMIT ?";
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.obtenerConexion(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    String segundo = rs.getString("t_segundo_apellido");
                    String ruta = rs.getString("t_ruta_pdf");
                    c.put("idCertificado", rs.getInt("id_certificado"));
                    c.put("contratista", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido") + (segundo == null ? "" : " " + segundo));
                    c.put("documento", GenerarCertificado.formatearDocumento(rs.getString("n_numero_documento")));
                    c.put("anioGravable", rs.getInt("n_anio"));
                    c.put("fecha", String.valueOf(rs.getDate("d_fecha_generacion")));
                    c.put("estado", rs.getString("t_nombre_estado"));
                    c.put("corregido", rs.getBoolean("b_certificado_corregido"));
                    c.put("archivo", ruta == null ? null : java.nio.file.Paths.get(ruta).getFileName().toString());
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    /**
     * Notificaciones de error: envíos de correo que fallaron (con el motivo y si después se envió bien)
     * y reportes rechazados por documentos repetidos.
     */
    public static List<Map<String, Object>> notificaciones() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.obtenerConexion()) {
            String sqlEnvios = "SELECT e.id_envio, e.d_fecha_envio, e.t_correo_destino, e.t_mensaje_error, ce.id_estado, " +
                "ct.t_nombres, ct.t_primer_apellido, ct.n_numero_documento FROM envio_certificado e " +
                "JOIN certificados ce ON ce.id_certificado = e.id_certificado JOIN contratos co ON co.id_contrato = ce.id_contrato " +
                "JOIN contratistas ct ON ct.id_contratista = co.id_contratista " +
                "WHERE e.b_envio_correo = 0 ORDER BY e.id_envio DESC LIMIT 200";
            try (PreparedStatement ps = conn.prepareStatement(sqlEnvios); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("tipo", "envio");
                    n.put("fecha", String.valueOf(rs.getDate("d_fecha_envio")));
                    n.put("titulo", "No se pudo enviar el certificado de " + rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido"));
                    n.put("detalle", "Documento " + GenerarCertificado.formatearDocumento(rs.getString("n_numero_documento")) +
                        " · " + rs.getString("t_correo_destino") + " · " + rs.getString("t_mensaje_error"));
                    n.put("resuelto", rs.getInt("id_estado") == Firmas.ESTADO_ENVIADO);
                    lista.add(n);
                }
            }
            String sqlReportes = "SELECT d_fecha_registro, t_datos_nuevos FROM auditorias WHERE t_accion IN ('Reporte rechazado', 'Sábana rechazada') " +
                "ORDER BY id_log DESC LIMIT 50";
            try (PreparedStatement ps = conn.prepareStatement(sqlReportes); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> n = new LinkedHashMap<>();
                    n.put("tipo", "reporte");
                    n.put("fecha", String.valueOf(rs.getTimestamp("d_fecha_registro").toLocalDateTime().toLocalDate()));
                    n.put("titulo", "Reporte rechazado por documentos repetidos");
                    n.put("detalle", rs.getString("t_datos_nuevos"));
                    n.put("resuelto", false);
                    lista.add(n);
                }
            }
        }
        lista.sort((x, y) -> String.valueOf(y.get("fecha")).compareTo(String.valueOf(x.get("fecha"))));
        return lista;
    }

    private static void sumarPorMes(Connection conn, int[][] meses, int columna, String sql, int anio) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, anio);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) meses[rs.getInt(1) - 1][columna] += rs.getInt(2);
            }
        }
    }

    private static int contar(Connection conn, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) ps.setObject(i + 1, parametros[i]);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
