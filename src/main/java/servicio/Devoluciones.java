package servicio;

import Seguridad.EsquemaBD;
import conexion.ConexionBD;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Certificados devueltos por los contratistas.
 *
 * Un certificado devuelto NUNCA se edita: queda guardado tal como se envió (estado Devuelto) y se
 * genera uno nuevo con los datos actuales de la base de datos (estado Corregido), que apunta al
 * devuelto en id_certificado_anterior. Cada paso queda en historial_certificados.
 */
public final class Devoluciones {

    private static final int ESTADO_DEVUELTO = EsquemaBD.ESTADO_DEVUELTO;

    /** Error de una regla de negocio (se muestra tal cual al usuario). codigo sirve a la página para decidir qué hacer. */
    public static class ReglaException extends Exception {
        private final String codigo;
        public ReglaException(String codigo, String mensaje) { super(mensaje); this.codigo = codigo; }
        public String codigo() { return codigo; }
    }

    private Devoluciones() { }

    /**
     * Registra la devolución del último certificado emitido al contratista con ese documento.
     * Devuelve el id del certificado devuelto.
     */
    public static int registrar(String documento, String motivo, int idUsuario) throws SQLException, ReglaException {
        String doc = documento == null ? "" : documento.replace(".", "").replace(",", "").trim();
        if (doc.isEmpty()) throw new ReglaException("DATOS", "Escriba el número de documento del contratista.");
        if (motivo == null || motivo.isBlank()) throw new ReglaException("DATOS", "Escriba el motivo de la devolución.");

        String sql = "SELECT ce.id_certificado, ce.id_estado FROM certificados ce " +
            "JOIN contratos co ON co.id_contrato = ce.id_contrato " +
            "JOIN contratistas ct ON ct.id_contratista = co.id_contratista " +
            "WHERE REPLACE(ct.n_numero_documento, '.', '') = ? ORDER BY ce.id_certificado DESC LIMIT 1";

        try (Connection conn = ConexionBD.obtenerConexion()) {
            int id, estado;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, doc);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ReglaException("NO_EXISTE", "No hay certificados generados para el documento " + documento.trim() + ".");
                    }
                    id = rs.getInt(1);
                    estado = rs.getInt(2);
                }
            }
            if (estado == ESTADO_DEVUELTO) {
                throw new ReglaException("YA_DEVUELTO", "El último certificado de ese documento ya está registrado como devuelto.");
            }
            if (estado == Firmas.ESTADO_GENERADO || estado == Firmas.ESTADO_CORREGIDO) {
                throw new ReglaException("SIN_ENVIAR", "El último certificado de ese documento todavía está pendiente de firma " +
                    "y no se ha enviado, así que no puede estar devuelto.");
            }

            conn.setAutoCommit(false);
            try {
                cambiarEstado(conn, id, ESTADO_DEVUELTO);
                historial(conn, id, idUsuario, estado, ESTADO_DEVUELTO, "Devolución", motivo.trim());
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            return id;
        }
    }

    /** Devoluciones registradas: las pendientes de regenerar primero y luego las ya resueltas. */
    public static List<Map<String, Object>> listar() throws SQLException {
        String sql = "SELECT ce.id_certificado, ce.t_ruta_pdf, ct.t_nombres, ct.t_primer_apellido, ct.t_segundo_apellido, " +
            "ct.n_numero_documento, ct.t_correo_contratista, ct.id_contratista, " +
            "(SELECT h.d_fecha_historial FROM historial_certificados h WHERE h.id_certificado = ce.id_certificado " +
            "   AND h.t_accion = 'Devolución' ORDER BY h.id_historial DESC LIMIT 1) AS fecha_devolucion, " +
            "(SELECT h.t_observacion FROM historial_certificados h WHERE h.id_certificado = ce.id_certificado " +
            "   AND h.t_accion = 'Devolución' ORDER BY h.id_historial DESC LIMIT 1) AS motivo, " +
            "nu.id_certificado AS id_nuevo, nu.t_ruta_pdf AS ruta_nueva, nu.d_fecha_correccion AS fecha_nueva, nu.id_estado AS estado_nuevo " +
            "FROM certificados ce " +
            "JOIN contratos co ON co.id_contrato = ce.id_contrato " +
            "JOIN contratistas ct ON ct.id_contratista = co.id_contratista " +
            "LEFT JOIN certificados nu ON nu.id_certificado_anterior = ce.id_certificado " +
            "WHERE ce.id_estado = ? ORDER BY (nu.id_certificado IS NULL) DESC, ce.id_certificado DESC";

        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ESTADO_DEVUELTO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    String segundo = rs.getString("t_segundo_apellido");
                    d.put("idCertificado", rs.getInt("id_certificado"));
                    d.put("contratista", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido") +
                        (segundo == null ? "" : " " + segundo));
                    d.put("documento", GenerarCertificado.formatearDocumento(rs.getString("n_numero_documento")));
                    d.put("correo", rs.getString("t_correo_contratista"));
                    d.put("archivo", nombreArchivo(rs.getString("t_ruta_pdf")));
                    Timestamp f = rs.getTimestamp("fecha_devolucion");
                    d.put("fechaDevolucion", f == null ? null : f.toLocalDateTime().toString());
                    d.put("motivo", rs.getString("motivo"));
                    if (rs.getObject("id_nuevo") != null) {
                        Timestamp fn = rs.getTimestamp("fecha_nueva");
                        int estadoNuevo = rs.getInt("estado_nuevo");
                        d.put("regenerado", Map.of(
                            "idCertificado", rs.getInt("id_nuevo"),
                            "archivo", nombreArchivo(rs.getString("ruta_nueva")),
                            "fecha", fn == null ? "" : fn.toLocalDateTime().toString(),
                            "firmado", estadoNuevo == Firmas.ESTADO_FIRMADO || estadoNuevo == Firmas.ESTADO_ENVIADO));
                    }
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    /**
     * Vuelve a generar un certificado devuelto. Validaciones:
     *  1. el certificado existe, está devuelto y no se ha regenerado antes;
     *  2. los datos del contratista cambiaron desde el certificado devuelto (si no cambiaron se
     *     avisa, porque saldría idéntico; con forzar=true se genera de todas formas);
     *  3. el PDF nuevo se puede abrir, tiene una página y trae el nombre y el documento correctos.
     * Si algo falla no queda nada a medias: se deshace el registro y se borra el PDF.
     */
    public static Map<String, Object> regenerar(int idDevuelto, boolean forzar, int idUsuario) throws Exception {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            int idContratista;
            String huellaAnterior;
            String sql = "SELECT ce.id_estado, ce.t_huella_datos, co.id_contratista, " +
                "(SELECT COUNT(*) FROM certificados n WHERE n.id_certificado_anterior = ce.id_certificado) AS reemplazos " +
                "FROM certificados ce JOIN contratos co ON co.id_contrato = ce.id_contrato WHERE ce.id_certificado = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idDevuelto);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new ReglaException("NO_EXISTE", "El certificado " + idDevuelto + " no existe.");
                    if (rs.getInt("reemplazos") > 0) {
                        throw new ReglaException("YA_REGENERADO", "Este certificado devuelto ya se volvió a generar.");
                    }
                    if (rs.getInt("id_estado") != ESTADO_DEVUELTO) {
                        throw new ReglaException("NO_DEVUELTO", "Solo se pueden volver a generar certificados registrados como devueltos.");
                    }
                    idContratista = rs.getInt("id_contratista");
                    huellaAnterior = rs.getString("t_huella_datos");
                }
            }

            GenerarCertificado.Datos datos = GenerarCertificado.leerDatos(conn, idContratista);
            if (!forzar && huellaAnterior != null && huellaAnterior.equals(GenerarCertificado.huella(datos))) {
                throw new ReglaException("SIN_CAMBIOS", "Los datos de " + datos.nombreCompleto() + " no han cambiado desde el " +
                    "certificado devuelto, así que el nuevo saldría igual. Si el error estaba en los datos, corrija el reporte " +
                    "y vuelva a cargarlo antes de regenerar.");
            }

            conn.setAutoCommit(false);
            GenerarCertificado.Resultado nuevo = null;
            try {
                nuevo = GenerarCertificado.generarReemplazo(conn, idContratista, idUsuario, idDevuelto);
                String problema = GenerarCertificado.verificarPdf(nuevo.rutaPdf(), datos);
                if (problema != null) {
                    throw new ReglaException("PDF_INVALIDO", "El certificado nuevo no pasó la verificación: " + problema);
                }
                historial(conn, idDevuelto, idUsuario, ESTADO_DEVUELTO, ESTADO_DEVUELTO, "Regenerado",
                    "Reemplazado por el certificado " + nuevo.idCertificado());
                historial(conn, nuevo.idCertificado(), idUsuario, ESTADO_DEVUELTO, GenerarCertificado.ESTADO_CORREGIDO,
                    "Generado por corrección", "Reemplaza al certificado devuelto " + idDevuelto +
                    (forzar && huellaAnterior != null && huellaAnterior.equals(GenerarCertificado.huella(datos))
                        ? " (sin cambios en los datos, confirmado por el usuario)" : ""));
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (nuevo != null) Files.deleteIfExists(Paths.get(nuevo.rutaPdf()));
                throw e;
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("idCertificado", nuevo.idCertificado());
            r.put("archivo", nombreArchivo(nuevo.rutaPdf()));
            r.put("contratista", datos.nombreCompleto());
            r.put("correo", correo(conn, idContratista));
            r.put("sinCambios", huellaAnterior != null && huellaAnterior.equals(GenerarCertificado.huella(datos)));
            return r;
        }
    }

    // ---------- Apoyo ----------

    private static void cambiarEstado(Connection conn, int idCertificado, int estado) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE certificados SET id_estado = ? WHERE id_certificado = ?")) {
            ps.setInt(1, estado);
            ps.setInt(2, idCertificado);
            ps.executeUpdate();
        }
    }

    private static void historial(Connection conn, int idCertificado, int idUsuario, int anterior, int nuevo,
                                  String accion, String observacion) throws SQLException {
        String sql = "INSERT INTO historial_certificados (id_certificado, id_usuario, id_estado_anterior, id_estado_nuevo, " +
            "t_accion, t_observacion) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCertificado);
            ps.setInt(2, idUsuario);
            ps.setInt(3, anterior);
            ps.setInt(4, nuevo);
            ps.setString(5, accion);
            ps.setString(6, observacion);
            ps.executeUpdate();
        }
    }

    private static String correo(Connection conn, int idContratista) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT t_correo_contratista FROM contratistas WHERE id_contratista = ?")) {
            ps.setInt(1, idContratista);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static String nombreArchivo(String ruta) {
        return ruta == null ? null : Paths.get(ruta).getFileName().toString();
    }
}
