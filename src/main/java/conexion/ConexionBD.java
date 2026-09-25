package conexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class conexionBD {


    public static Connection conectar() {

        Connection conexion = null;

        try {

            Properties propiedades = new Properties();

            FileInputStream archivo = new FileInputStream(
                    "src/conexion/config.properties"
            );

            propiedades.load(archivo);


            String url = propiedades.getProperty("url");
            String usuario = propiedades.getProperty("usuario");
            String password = propiedades.getProperty("password");


            conexion = DriverManager.getConnection(
                    url,
                    usuario,
                    password
            );


            System.out.println("Conexion exitosa");


        } catch (SQLException e) {

            System.out.println(
                "Error de conexión MySQL: " + e.getMessage()
            );


        } catch (IOException e) {

            System.out.println(
                "Error leyendo archivo de configuración: " + e.getMessage()
            );

        }


        return conexion;

    }


}