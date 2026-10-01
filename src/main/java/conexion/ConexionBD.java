package conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConexionBD {

    // Archivo con url, usuario y password (no se sube a git, ver config.properties.example).
    // Maven lo copia de src/main/resources a target/classes, por eso se lee desde el classpath.
    private static final String CONFIG = "/config.properties";


    public static Properties cargarConfiguracion() throws IOException {

        Properties propiedades = new Properties();

        try (InputStream archivo = ConexionBD.class.getResourceAsStream(CONFIG)) {

            if (archivo == null) {
                throw new FileNotFoundException(
                    "No se encontró config.properties en src/main/resources (copiar config.properties.example)"
                );
            }

            propiedades.load(archivo);
        }

        return propiedades;
    }


    // Usado por WebServer, CargueMasivo y GenerarCertificado: lanza la excepción
    public static Connection obtenerConexion() throws SQLException {

        Properties propiedades;

        try {
            propiedades = cargarConfiguracion();
        } catch (IOException e) {
            throw new SQLException("Error leyendo archivo de configuración: " + e.getMessage(), e);
        }

        return DriverManager.getConnection(
                propiedades.getProperty("url"),
                propiedades.getProperty("usuario"),
                propiedades.getProperty("password")
        );
    }


    // Usado por los DAO: devuelve null si no se pudo conectar
    public static Connection conectar() {

        try {

            Connection conexion = obtenerConexion();

            System.out.println("Conexion exitosa");

            return conexion;

        } catch (SQLException e) {

            System.out.println(
                "Error de conexión MySQL: " + e.getMessage()
            );

            return null;
        }
    }


    public static void main(String[] args) {
        try (Connection conn = obtenerConexion()) {
            System.out.println("Conexión exitosa a la base de datos");
        } catch (SQLException e) {
            System.out.println("Error de conexión: " + e.getMessage());
        }
    }
}