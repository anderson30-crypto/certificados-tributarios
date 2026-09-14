package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import conexion.conexionBD;
import modelo.Permiso;


public class RolPermisoDAO {


    // ASIGNAR PERMISO A UN ROL

    public boolean asignarPermisoRol(int idRol, int idPermiso) {


    	String sql =
    			"INSERT IGNORE INTO rol_permiso "
    			+"(id_rol,id_permiso) "
    			+"VALUES (?,?)";

        try {


            Connection con =
            conexionBD.conectar();


            PreparedStatement ps =
            con.prepareStatement(sql);


            ps.setInt(1, idRol);

            ps.setInt(2, idPermiso);


            ps.executeUpdate();


            return true;


        } catch(Exception e) {


            System.out.println(
            "Error asignando permiso: "
            + e.getMessage());


            return false;

        }

    }





    // CONSULTAR PERMISOS DE UN ROL


    public ArrayList<Permiso> listarPermisosPorRol(int idRol) {


        ArrayList<Permiso> lista =
        new ArrayList<>();



        String sql =

        "SELECT p.id_permiso, "
        +"p.t_nombre_permiso, "
        +"p.t_descripcion "
        +"FROM permisos p "
        +"INNER JOIN rol_permiso rp "
        +"ON p.id_permiso = rp.id_permiso "
        +"WHERE rp.id_rol=?";



        try {


            Connection con =
            conexionBD.conectar();



            PreparedStatement ps =
            con.prepareStatement(sql);



            ps.setInt(1, idRol);



            ResultSet rs =
            ps.executeQuery();



            while(rs.next()){


                Permiso p =
                new Permiso();


                p.setIdPermiso(
                rs.getInt("id_permiso"));


                p.setNombrePermiso(
                rs.getString("t_nombre_permiso"));


                p.setDescripcion(
                rs.getString("t_descripcion"));



                lista.add(p);

            }



        } catch(Exception e){


            System.out.println(
            e.getMessage());

        }



        return lista;

    }
    
 // VALIDAR PERMISO DE UN ROL

    public boolean tienePermiso(int idRol, String nombrePermiso){

        boolean resultado = false;


        String sql =
                "SELECT COUNT(*) " +
                "FROM permisos p " +
                "INNER JOIN rol_permiso rp " +
                "ON p.id_permiso = rp.id_permiso " +
                "WHERE rp.id_rol=? " +
                "AND p.t_nombre_permiso=?";


        try {

            Connection con =
                    conexionBD.conectar();


            PreparedStatement ps =
                    con.prepareStatement(sql);


            ps.setInt(1, idRol);

            ps.setString(2, nombrePermiso);


            ResultSet rs =
                    ps.executeQuery();


            if(rs.next()){

                resultado =
                        rs.getInt(1) > 0;

            }


        } catch(Exception e){

            System.out.println(
                    "Error validando permiso: "
                    + e.getMessage()
            );

        }


        return resultado;

    }

}
