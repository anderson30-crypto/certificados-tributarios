package conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConexionBD {

    // Ningún dato sensible va en el código. La configuración se busca en este orden
    // (lo que se encuentre después reemplaza a lo anterior):
    //   1. src/main/resources/config.properties  -> desarrollo en el PC (no se sube a git)
    //   2. el archivo indicado en la variable de entorno CT_CONFIG  -> servidor, fuera del proyecto
    //   3. variables de entorno CT_...  -> servidor (por ejemplo CT_DB_PASSWORD)
    private static final String CONFIG = "/config.properties";

    // Claves que se pueden definir también como variable de entorno (ver nombreVariable)
    private static final String[] CLAVES = {
        "url", "usuario", "password",
        "correo.host", "correo.puerto", "correo.usuario", "correo.password",
        "correo.starttls", "correo.pausa_ms", "correo.redirigir_a",
        "firma.p12.password", "cifrado.clave"
    };


    public static Properties cargarConfiguracion() throws IOException {

        Properties propiedades = new Properties();

        try (InputStream archivo = ConexionBD.class.getResourceAsStream(CONFIG)) {
            if (archivo != null) {
                propiedades.load(new InputStreamReader(archivo, StandardCharsets.UTF_8));
            }
        }

        String externo = System.getenv("CT_CONFIG");
        if (externo != null && !externo.isBlank()) {
            try (Reader archivo = Files.newBufferedReader(Path.of(externo.trim()), StandardCharsets.UTF_8)) {
                propiedades.load(archivo);
            } catch (IOException e) {
                throw new IOException("No se pudo leer el archivo de CT_CONFIG (" + externo + "): " + e.getMessage(), e);
            }
        }

        for (String clave : CLAVES) {
            String valor = System.getenv(nombreVariable(clave));
            if (valor != null) {
                propiedades.setProperty(clave, valor);
            }
        }

        return propiedades;
    }

    /** Un valor de configuración obligatorio; si falta, el error dice dónde definirlo. */
    public static String obtener(String clave) throws IOException {
        String valor = cargarConfiguracion().getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IOException("Falta la configuración '" + clave + "': defínala en config.properties " +
                "o en la variable de entorno " + nombreVariable(clave));
        }
        return valor.trim();
    }

    /** url -> CT_DB_URL, correo.host -> CT_CORREO_HOST, firma.p12.password -> CT_FIRMA_P12_PASSWORD */
    public static String nombreVariable(String clave) {
        if (clave.equals("url") || clave.equals("usuario") || clave.equals("password")) {
            return "CT_DB_" + clave.toUpperCase();
        }
        return "CT_" + clave.toUpperCase().replace('.', '_');
    }


    // Usado por WebServer, CargueMasivo y GenerarCertificado: lanza la excepción
    public static Connection obtenerConexion() throws SQLException {

        Properties propiedades;

        try {
            propiedades = cargarConfiguracion();
        } catch (IOException e) {
            throw new SQLException("Error leyendo archivo de configuración: " + e.getMessage(), e);
        }

        String url = propiedades.getProperty("url");
        if (url == null || url.isBlank()) {
            throw new SQLException("Falta la conexión a la base de datos: defina url, usuario y password en " +
                "config.properties o las variables de entorno CT_DB_URL, CT_DB_USUARIO y CT_DB_PASSWORD");
        }

        Properties datos = new Properties();
        datos.setProperty("user", propiedades.getProperty("usuario", "").trim());
        datos.setProperty("password", propiedades.getProperty("password", ""));
        // Sin esto, si MySQL no responde el programa se queda esperando para siempre sin mostrar nada
        datos.setProperty("connectTimeout", "10000");

        return DriverManager.getConnection(url.trim(), datos);
    }

    /** Texto para la consola: a qué base y con qué usuario se conecta (nunca la contraseña). */
    public static String describirConexion() {
        try {
            Properties propiedades = cargarConfiguracion();
            return propiedades.getProperty("url", "(sin url)").trim() +
                " con el usuario " + propiedades.getProperty("usuario", "(sin usuario)").trim();
        } catch (IOException e) {
            return "(no se pudo leer config.properties: " + e.getMessage() + ")";
        }
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
