package prueba;

import conexion.conexionBD;
import java.sql.Connection;

public class PruebaConexion {

    public static void main(String[] args) {

        Connection conexion = conexionBD.conectar();

        if(conexion != null) {

            System.out.println("Base de datos conectada correctamente");

        } else {

            System.out.println("No se pudo conectar a la base de datos");

        }

    }

}