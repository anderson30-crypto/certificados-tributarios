package dao;

import conexion.ConexionBD;
import servicio.GenerarCertificado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de los correos enviados (tabla envio_certificado) y datos para enviarlos.
 */
public class EnvioCertificadoDAO {

    // Tabla estados: 3 = Enviado, 5 = Error
    private static final int ESTADO_ENVIADO = 3;
    private static final int ESTADO_ERROR = 5;

    /** Correo, nombre y PDF del contratista dueño de un certificado. null si el certificado no existe. */
    public Map<String, Object> datosParaEnvio(int idCertificado) throws SQLException {

        String sql = "SELECT ct.t_correo_contratista, ct.t_nombres, ct.t_primer_apellido, " +
            "ct.n_numero_documento, ce.t_ruta_pdf " +
            "FROM certificados ce " +
            "JOIN contratos co ON ce.id_contrato = co.id_contrato " +
            "JOIN contratistas ct ON co.id_contratista = ct.id_contratista " +
            "WHERE ce.id_certificado = ?";

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idCertificado);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("correo", rs.getString("t_correo_contratista"));
                datos.put("nombre", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido"));
                datos.put("documento", rs.getString("n_numero_documento"));
                datos.put("rutaPdf", rs.getString("t_ruta_pdf"));
                return datos;
            }
        }
    }

    /** Guarda el resultado de un envío. Si salió bien, el certificado pasa a estado Enviado. */
    public void registrar(int idCertificado, String correoDestino, boolean exito, String error) throws SQLException {

        String sql = "INSERT INTO envio_certificado " +
            "(id_certificado, id_usuario, t_correo_destino, d_fecha_envio, b_envio_correo, id_estado, t_mensaje_error) " +
            "VALUES (?, ?, ?, CURDATE(), ?, ?, ?)";

        try (Connection conn = ConexionBD.obtenerConexion()) {

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, idCertificado);
                ps.setInt(2, GenerarCertificado.ID_USUARIO_SISTEMA);
                ps.setString(3, correoDestino);
                ps.setBoolean(4, exito);
                ps.setInt(5, exito ? ESTADO_ENVIADO : ESTADO_ERROR);
                // La columna admite máximo 255 caracteres
                ps.setString(6, error == null ? null : error.substring(0, Math.min(error.length(), 255)));
                ps.executeUpdate();
            }

            if (exito) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE certificados SET id_estado = ?, b_generar_correo = 1 WHERE id_certificado = ?")) {
                    ps.setInt(1, ESTADO_ENVIADO);
                    ps.setInt(2, idCertificado);
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Últimos envíos, del más reciente al más antiguo (para la página enviar-correo.html). */
    public List<Map<String, Object>> listar() throws SQLException {

        String sql = "SELECT e.id_envio, e.t_correo_destino, e.d_fecha_envio, e.t_mensaje_error, " +
            "es.t_nombre_estado, ct.t_nombres, ct.t_primer_apellido, ct.n_numero_documento " +
            "FROM envio_certificado e " +
            "JOIN estados es ON e.id_estado = es.id_estado " +
            "JOIN certificados ce ON e.id_certificado = ce.id_certificado " +
            "JOIN contratos co ON ce.id_contrato = co.id_contrato " +
            "JOIN contratistas ct ON co.id_contratista = ct.id_contratista " +
            "ORDER BY e.id_envio DESC LIMIT 200";

        List<Map<String, Object>> lista = new ArrayList<>();

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> envio = new LinkedHashMap<>();
                envio.put("destino", rs.getString("t_correo_destino"));
                envio.put("contratista", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido") +
                    " - " + rs.getString("n_numero_documento"));
                envio.put("fecha", String.valueOf(rs.getDate("d_fecha_envio")));
                envio.put("estado", rs.getString("t_nombre_estado"));
                envio.put("error", rs.getString("t_mensaje_error"));
                lista.add(envio);
            }
        }

        return lista;
    }
}
