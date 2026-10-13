package Seguridad;

import conexion.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Ajustes que la base de datos necesita para esta versión. Se aplican solos al arrancar el WebServer
 * y solo cuando faltan, así funciona igual en cada PC y en el servidor sin correr scripts a mano.
 */
public final class EsquemaBD {

    private EsquemaBD() { }

    public static void asegurar() {
        try (Connection conn = ConexionBD.obtenerConexion()) {

            // 1) Los cuatro roles. El antiguo rol "Usuario" pasa a llamarse "Nómina"
            if (!existeRol(conn, Roles.NOMBRES.get(Roles.NOMINA)) && existeRol(conn, "Usuario")) {
                ejecutar(conn, "UPDATE roles SET t_rol = '" + Roles.NOMBRES.get(Roles.NOMINA) + "' WHERE t_rol = 'Usuario'");
                System.out.println("Base de datos: el rol 'Usuario' ahora se llama 'Nómina'");
            }
            for (String nombre : new String[] {
                    Roles.NOMBRES.get(Roles.ADMIN), Roles.NOMBRES.get(Roles.NOMINA),
                    Roles.NOMBRES.get(Roles.JEFE), Roles.NOMBRES.get(Roles.GH) }) {
                if (!existeRol(conn, nombre)) {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO roles (t_rol, b_estado_rol) VALUES (?, 1)")) {
                        ps.setString(1, nombre);
                        ps.executeUpdate();
                    }
                    System.out.println("Base de datos: rol '" + nombre + "' creado");
                }
            }

            // 2) Teléfono de contacto de los usuarios (lo usan las pantallas de perfil)
            if (!existeColumna(conn, "usuarios", "t_telefono")) {
                ejecutar(conn, "ALTER TABLE usuarios ADD COLUMN t_telefono VARCHAR(30) NULL");
                System.out.println("Base de datos: columna usuarios.t_telefono creada");
            }

            // 3) Tipo de documento de hasta 60 caracteres ("permiso por protección temporal" tiene 31)
            if (largoColumna(conn, "contratistas", "t_tipo_documento") < 60) {
                ejecutar(conn, "ALTER TABLE contratistas MODIFY t_tipo_documento VARCHAR(60) NOT NULL");
                System.out.println("Base de datos: contratistas.t_tipo_documento ampliado a 60 caracteres");
            }

        } catch (SQLException e) {
            System.out.println("ATENCIÓN: no se pudieron revisar o ajustar las tablas (" + e.getMessage() + "). " +
                "Si el usuario de MySQL no tiene permiso para ALTER/INSERT, aplique los cambios con un usuario administrador.");
        }
    }

    private static boolean existeRol(Connection conn, String nombre) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM roles WHERE t_rol = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean existeColumna(Connection conn, String tabla, String columna) throws SQLException {
        return largoColumna(conn, tabla, columna) >= 0;
    }

    // Largo máximo de una columna de texto; -1 si la columna no existe
    private static long largoColumna(Connection conn, String tabla, String columna) throws SQLException {
        String sql = "SELECT COALESCE(CHARACTER_MAXIMUM_LENGTH, 0) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tabla);
            ps.setString(2, columna);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private static void ejecutar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
