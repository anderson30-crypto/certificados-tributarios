package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import conexion.conexionBD;
import modelo.Permiso;


public class PermisoDAO {


    public boolean crearPermiso(Permiso permiso) {


        String sql =
        "INSERT INTO permisos "
        +"(t_nombre_permiso,t_descripcion) "
        +"VALUES (?,?)";


        try {

            Connection con = conexionBD.conectar();

            PreparedStatement ps =
            con.prepareStatement(sql);


            ps.setString(1,
            permiso.getNombrePermiso());


            ps.setString(2,
            permiso.getDescripcion());


            ps.executeUpdate();


            return true;


        } catch(Exception e) {

            System.out.println(e.getMessage());

            return false;

        }

    }



    public ArrayList<Permiso> listarPermisos(){


        ArrayList<Permiso> lista =
        new ArrayList<>();


        String sql =
        "SELECT * FROM permisos";


        try {

            Connection con =
            conexionBD.conectar();


            PreparedStatement ps =
            con.prepareStatement(sql);


            ResultSet rs =
            ps.executeQuery();


            while(rs.next()){


                Permiso p = new Permiso();


                p.setIdPermiso(
                rs.getInt("id_permiso"));


                p.setNombrePermiso(
                rs.getString("t_nombre_permiso"));


                p.setDescripcion(
                rs.getString("t_descripcion"));


                lista.add(p);

            }


        } catch(Exception e){

            System.out.println(e.getMessage());

        }


        return lista;

    }

}