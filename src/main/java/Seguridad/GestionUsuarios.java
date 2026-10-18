package Seguridad;

import conexion.ConexionBD;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Usuarios del sistema: los administra el Administrador (admin-usuarios.html) y cada usuario
 * edita su propio perfil y su contraseña.
 * Un usuario con registros (certificados, envíos, auditorías...) no se borra: se desactiva,
 * para no perder el rastro de quién hizo cada cosa.
 */
public final class GestionUsuarios {

    public static final int LONGITUD_MINIMA_PASSWORD = 10;

    public static class ReglaException extends Exception {
        public ReglaException(String mensaje) {
            super(mensaje);
        }
    }

    private GestionUsuarios() { }

    // Cuántos registros de otras tablas apuntan al usuario (las 5 tablas con llave a usuarios)
    private static final String REGISTROS =
        "(SELECT COUNT(*) FROM certificados x WHERE x.id_usuario = u.id_usuario OR x.id_usuario_firma = u.id_usuario) + " +
        "(SELECT COUNT(*) FROM envio_certificado x WHERE x.id_usuario = u.id_usuario) + " +
        "(SELECT COUNT(*) FROM auditorias x WHERE x.id_usuario = u.id_usuario) + " +
        "(SELECT COUNT(*) FROM historial_certificados x WHERE x.id_usuario = u.id_usuario) + " +
        "(SELECT COUNT(*) FROM solicitud_certificados x WHERE x.id_usuario = u.id_usuario)";

    private static final String SELECT_USUARIO =
        "SELECT u.id_usuario, u.t_nombre_usuario, u.t_username, u.t_correo, u.t_telefono, u.b_estado, r.t_rol, " +
        REGISTROS + " AS registros FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol ";

    /** null si la contraseña cumple la política; si no, el motivo. */
    public static String problemaPassword(String password) {
        if (password == null || password.length() < LONGITUD_MINIMA_PASSWORD) {
            return "La contraseña debe tener al menos " + LONGITUD_MINIMA_PASSWORD + " caracteres.";
        }
        for (char c : password.toCharArray()) {
            if (c < 33 || c > 126) {
                return "La contraseña no puede tener espacios, tildes ni ñ.";
            }
        }
        return null;
    }

    public static List<Map<String, Object>> listar() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(SELECT_USUARIO + "ORDER BY u.b_estado DESC, u.t_nombre_usuario");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(aMapa(rs));
            }
        }
        return lista;
    }

    public static Map<String, Object> buscar(int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            return buscar(conn, idUsuario);
        }
    }

    /** Crea un usuario con su rol y contraseña. Devuelve el id nuevo. */
    public static int crear(String nombre, String username, String correo, String telefono, String rol, String password)
            throws SQLException, ReglaException {

        nombre = obligatorio(nombre, "nombre");
        username = obligatorio(username, "usuario");
        correo = obligatorio(correo, "correo");
        if (!username.matches("[A-Za-z0-9._-]{3,50}")) {
            throw new ReglaException("El usuario debe tener entre 3 y 50 caracteres: letras sin tilde, números, punto, guion o guion bajo.");
        }
        if (!Roles.NOMBRES.containsKey(rol)) {
            throw new ReglaException("Rol no válido.");
        }
        String problema = problemaPassword(password);
        if (problema != null) {
            throw new ReglaException(problema);
        }

        try (Connection conn = ConexionBD.obtenerConexion()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM usuarios WHERE t_username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new ReglaException("Ya existe un usuario '" + username + "'.");
                    }
                }
            }
            String sql = "INSERT INTO usuarios (id_rol, t_nombre_usuario, t_correo, t_username, t_password_hash, b_estado, t_telefono) " +
                "VALUES (?, ?, ?, ?, ?, 1, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, idRol(conn, rol));
                ps.setString(2, nombre);
                ps.setString(3, correo);
                ps.setString(4, username);
                ps.setString(5, Autenticacion.hash(password));
                ps.setString(6, vacioANull(telefono));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }

    /**
     * Cambia rol, estado y, si viene, la contraseña de un usuario (lo hace el Administrador).
     * Devuelve un texto con lo que cambió, para la auditoría.
     */
    public static String actualizar(int idUsuario, String rol, boolean activo, String nuevaPassword, int idQuienCambia)
            throws SQLException, ReglaException {

        if (!Roles.NOMBRES.containsKey(rol)) {
            throw new ReglaException("Rol no válido.");
        }
        boolean cambiaPassword = nuevaPassword != null && !nuevaPassword.isEmpty();
        if (cambiaPassword) {
            String problema = problemaPassword(nuevaPassword);
            if (problema != null) {
                throw new ReglaException(problema);
            }
        }

        try (Connection conn = ConexionBD.obtenerConexion()) {
            Map<String, Object> u = buscar(conn, idUsuario);
            if (u == null) {
                throw new ReglaException("El usuario ya no existe.");
            }
            boolean mismo = idUsuario == idQuienCambia;
            if (mismo && !rol.equals(u.get("rol"))) {
                throw new ReglaException("No puede cambiar su propio rol.");
            }
            if (mismo && !activo) {
                throw new ReglaException("No puede desactivar su propio usuario.");
            }
            boolean dejaDeSerAdminActivo = !rol.equals(Roles.ADMIN) || !activo;
            if (dejaDeSerAdminActivo) {
                exigirOtroAdministradorActivo(conn, u);
            }

            String sql = "UPDATE usuarios SET id_rol = ?, b_estado = ?" + (cambiaPassword ? ", t_password_hash = ?" : "") +
                " WHERE id_usuario = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int i = 1;
                ps.setInt(i++, idRol(conn, rol));
                ps.setBoolean(i++, activo);
                if (cambiaPassword) {
                    ps.setString(i++, Autenticacion.hash(nuevaPassword));
                }
                ps.setInt(i, idUsuario);
                ps.executeUpdate();
            }

            List<String> cambios = new ArrayList<>();
            if (!rol.equals(u.get("rol"))) {
                cambios.add("rol: " + u.get("rolNombre") + " → " + Roles.NOMBRES.get(rol));
            }
            if (activo != (boolean) u.get("activo")) {
                cambios.add(activo ? "activado" : "desactivado");
            }
            if (cambiaPassword) {
                cambios.add("contraseña asignada por el administrador");
            }
            return u.get("username") + (cambios.isEmpty() ? " · sin cambios" : " · " + String.join(" · ", cambios));
        }
    }

    /** Borra un usuario sin registros asociados. Devuelve su nombre de usuario. */
    public static String eliminar(int idUsuario, int idQuienBorra) throws SQLException, ReglaException {
        if (idUsuario == idQuienBorra) {
            throw new ReglaException("No puede borrar su propio usuario.");
        }
        try (Connection conn = ConexionBD.obtenerConexion()) {
            Map<String, Object> u = buscar(conn, idUsuario);
            if (u == null) {
                throw new ReglaException("El usuario ya no existe.");
            }
            int registros = (int) u.get("registros");
            if (registros > 0) {
                throw new ReglaException("No se puede borrar a '" + u.get("username") + "': tiene " + registros +
                    " registro(s) asociado(s) (certificados, envíos u otros). Desactívelo para que no pueda entrar " +
                    "y se conserve el historial.");
            }
            exigirOtroAdministradorActivo(conn, u);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM firmas_usuario WHERE id_usuario = ?")) {
                ps.setInt(1, idUsuario);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM usuarios WHERE id_usuario = ?")) {
                ps.setInt(1, idUsuario);
                ps.executeUpdate();
            }
            return (String) u.get("username");
        }
    }

    /** Cada usuario actualiza su nombre, correo y teléfono. */
    public static void actualizarPerfil(int idUsuario, String nombre, String correo, String telefono)
            throws SQLException, ReglaException {
        nombre = obligatorio(nombre, "nombre");
        correo = obligatorio(correo, "correo");
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE usuarios SET t_nombre_usuario = ?, t_correo = ?, t_telefono = ? WHERE id_usuario = ?")) {
            ps.setString(1, nombre);
            ps.setString(2, correo);
            ps.setString(3, vacioANull(telefono));
            ps.setInt(4, idUsuario);
            ps.executeUpdate();
        }
    }

    /** Cada usuario cambia su propia contraseña, confirmando la actual. */
    public static void cambiarPasswordPropia(int idUsuario, String actual, String nueva)
            throws SQLException, ReglaException {
        String problema = problemaPassword(nueva);
        if (problema != null) {
            throw new ReglaException(problema);
        }
        try (Connection conn = ConexionBD.obtenerConexion()) {
            String hash;
            try (PreparedStatement ps = conn.prepareStatement("SELECT t_password_hash FROM usuarios WHERE id_usuario = ?")) {
                ps.setInt(1, idUsuario);
                try (ResultSet rs = ps.executeQuery()) {
                    hash = rs.next() ? rs.getString(1) : null;
                }
            }
            if (hash == null || !hash.startsWith("$2") || actual == null || !BCrypt.checkpw(actual, hash)) {
                throw new ReglaException("La contraseña actual no es correcta.");
            }
            if (BCrypt.checkpw(nueva, hash)) {
                throw new ReglaException("La contraseña nueva debe ser distinta de la actual.");
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET t_password_hash = ? WHERE id_usuario = ?")) {
                ps.setString(1, Autenticacion.hash(nueva));
                ps.setInt(2, idUsuario);
                ps.executeUpdate();
            }
        }
    }

    // Si el usuario es un administrador activo, debe quedar al menos otro administrador activo
    private static void exigirOtroAdministradorActivo(Connection conn, Map<String, Object> u)
            throws SQLException, ReglaException {
        if (!Roles.ADMIN.equals(u.get("rol")) || !(boolean) u.get("activo")) {
            return;
        }
        String sql = "SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol " +
            "WHERE r.t_rol = ? AND u.b_estado = 1 AND u.id_usuario <> ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Roles.NOMBRES.get(Roles.ADMIN));
            ps.setInt(2, (int) u.get("id"));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    throw new ReglaException("'" + u.get("username") + "' es el único administrador activo; debe quedar al menos uno.");
                }
            }
        }
    }

    private static Map<String, Object> buscar(Connection conn, int idUsuario) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_USUARIO + "WHERE u.id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? aMapa(rs) : null;
            }
        }
    }

    private static Map<String, Object> aMapa(ResultSet rs) throws SQLException {
        String rol = Roles.clave(rs.getString("t_rol"));
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", rs.getInt("id_usuario"));
        u.put("nombre", rs.getString("t_nombre_usuario"));
        u.put("username", rs.getString("t_username"));
        u.put("correo", rs.getString("t_correo"));
        u.put("telefono", rs.getString("t_telefono") == null ? "" : rs.getString("t_telefono"));
        u.put("rol", rol);
        u.put("rolNombre", Roles.NOMBRES.get(rol));
        u.put("funciones", Roles.FUNCIONES.get(rol));
        u.put("activo", rs.getBoolean("b_estado"));
        u.put("registros", rs.getInt("registros"));
        return u;
    }

    private static int idRol(Connection conn, String rol) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id_rol FROM roles WHERE t_rol = ?")) {
            ps.setString(1, Roles.NOMBRES.get(rol));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No existe el rol '" + Roles.NOMBRES.get(rol) + "' en la tabla roles.");
                }
                return rs.getInt(1);
            }
        }
    }

    private static String obligatorio(String valor, String campo) throws ReglaException {
        if (valor == null || valor.isBlank()) {
            throw new ReglaException("El campo " + campo + " es obligatorio.");
        }
        return valor.trim();
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
