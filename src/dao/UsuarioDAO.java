package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import conexion.conexionBD;
import modelo.Usuario;


public class UsuarioDAO {


    // CREAR USUARIO

    public boolean crearUsuario(Usuario usuario) {

        String sql = 
        "INSERT INTO usuarios "
        + "(id_rol,t_nombre_usuario,t_correo,t_username,t_password_hash,b_estado)"
        + " VALUES (?,?,?,?,?,?)";


        try {

            Connection con = conexionBD.conectar();

            PreparedStatement ps = con.prepareStatement(sql);


            ps.setInt(1, usuario.getIdRol());
            ps.setString(2, usuario.getNombreUsuario());
            ps.setString(3, usuario.getCorreo());
            ps.setString(4, usuario.getUsername());
            ps.setString(5, usuario.getPassword());
            ps.setBoolean(6, usuario.isEstado());


            ps.executeUpdate();


            return true;


        } catch(Exception e) {

            System.out.println("Error creando usuario: "
                    + e.getMessage());

            return false;
        }

    }




    // CONSULTAR USUARIOS

    public ArrayList<Usuario> listarUsuarios(){


        ArrayList<Usuario> lista = new ArrayList<>();


        String sql =
        "SELECT * FROM usuarios";


        try {


            Connection con = conexionBD.conectar();

            PreparedStatement ps =
            con.prepareStatement(sql);


            ResultSet rs = ps.executeQuery();



            while(rs.next()) {


                Usuario u = new Usuario();


                u.setIdUsuario(
                rs.getInt("id_usuario"));


                u.setIdRol(
                rs.getInt("id_rol"));


                u.setNombreUsuario(
                rs.getString("t_nombre_usuario"));


                u.setCorreo(
                rs.getString("t_correo"));


                u.setUsername(
                rs.getString("t_username"));


                u.setEstado(
                rs.getBoolean("b_estado"));


                lista.add(u);

            }



        } catch(Exception e){

            System.out.println(e.getMessage());

        }


        return lista;

    }




    // EDITAR USUARIO

    public boolean editarUsuario(Usuario usuario){


        String sql =
        "UPDATE usuarios SET "
        +"t_nombre_usuario=?, "
        +"t_correo=?, "
        +"id_rol=? "
        +"WHERE id_usuario=?";



        try{


            Connection con =
            conexionBD.conectar();


            PreparedStatement ps =
            con.prepareStatement(sql);



            ps.setString(1,
            usuario.getNombreUsuario());


            ps.setString(2,
            usuario.getCorreo());


            ps.setInt(3,
            usuario.getIdRol());


            ps.setInt(4,
            usuario.getIdUsuario());



            ps.executeUpdate();


            return true;


        }catch(Exception e){

            System.out.println(e.getMessage());

            return false;

        }

    }





    // DESACTIVAR USUARIO


    public boolean desactivarUsuario(int idUsuario){


        String sql =
        "UPDATE usuarios "
        +"SET b_estado=0 "
        +"WHERE id_usuario=?";



        try{


            Connection con =
            conexionBD.conectar();


            PreparedStatement ps =
            con.prepareStatement(sql);



            ps.setInt(1,idUsuario);


            ps.executeUpdate();


            return true;


        }catch(Exception e){


            System.out.println(e.getMessage());

            return false;

        }


    }


}