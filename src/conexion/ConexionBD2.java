package conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionBD2 {

    private static final String URL = "jdbc:mysql://localhost:3306/certificados_tributarios";
    private static final String USUARIO = "root";
    private static final String PASSWORD = "Artifex13579?"; // la misma que usaste en Workbench

    public static Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, PASSWORD);
    }

    public static void main(String[] args) {
        try (Connection conn = obtenerConexion()) {
            System.out.println("Conexión exitosa a la base de datos");
        } catch (SQLException e) {
            System.out.println("Error de conexión: " + e.getMessage());
        }
    }
}
