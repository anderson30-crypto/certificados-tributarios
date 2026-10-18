package Seguridad;

import conexion.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de auditoría (tabla auditorias): quién hizo qué y cuándo.
 * Columnas usadas: t_tabla_afectada = módulo, t_accion = acción, t_datos_nuevos = detalle.
 */
public final class RegistroAuditoria {

    private RegistroAuditoria() { }

    /**
     * Guarda un evento. Nunca lanza excepción: si la auditoría falla, la acción del usuario no se detiene
     * (el error se informa en la consola del WebServer).
     */
    public static void registrar(Integer idUsuario, String modulo, String accion, String detalle) {
        String sql = "INSERT INTO auditorias (id_usuario, t_tabla_afectada, t_accion, t_datos_nuevos) VALUES (?, ?, ?, ?)";
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (idUsuario == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, idUsuario);
            }
            ps.setString(2, recortar(modulo, 50));
            ps.setString(3, recortar(accion, 100));
            ps.setString(4, detalle);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("No se pudo registrar la auditoría (" + accion + "): " + e.getMessage());
        }
    }

    /** Últimos eventos, del más reciente al más antiguo. */
    public static List<Map<String, Object>> listar(int limite) throws SQLException {
        String sql = "SELECT a.d_fecha_registro, a.t_tabla_afectada, a.t_accion, a.t_datos_nuevos, " +
            "u.t_nombre_usuario, u.t_username, r.t_rol " +
            "FROM auditorias a LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario " +
            "LEFT JOIN roles r ON r.id_rol = u.id_rol ORDER BY a.id_log DESC LIMIT ?";

        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp fecha = rs.getTimestamp("d_fecha_registro");
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("fecha", fecha == null ? null : fecha.toLocalDateTime().toString());
                    e.put("usuario", rs.getString("t_nombre_usuario") != null ? rs.getString("t_nombre_usuario") : "(desconocido)");
                    e.put("rol", rs.getString("t_rol") != null ? Roles.clave(rs.getString("t_rol")) : "");
                    e.put("modulo", valor(rs.getString("t_tabla_afectada"), "General"));
                    e.put("accion", valor(rs.getString("t_accion"), ""));
                    e.put("detalle", valor(rs.getString("t_datos_nuevos"), ""));
                    lista.add(e);
                }
            }
        }
        return lista;
    }

    private static String recortar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private static String valor(String texto, String porDefecto) {
        return texto == null ? porDefecto : texto;
    }
}
