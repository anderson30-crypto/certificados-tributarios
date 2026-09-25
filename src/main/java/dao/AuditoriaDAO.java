package dao;

import conexion.conexionBD;
import modelo.Auditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;


public class AuditoriaDAO {


    public boolean registrar(Auditoria auditoria) {


        String sql =
                "INSERT INTO auditorias " +
                "(id_usuario, t_accion, t_tabla_afectada, " +
                "t_registro_id, t_datos_anteriores, t_datos_nuevos) " +
                "VALUES (?,?,?,?,?,?)";


        try {


            Connection cn = conexionBD.conectar();


            PreparedStatement ps =
                    cn.prepareStatement(sql);



            ps.setInt(1, auditoria.getIdUsuario());

            ps.setString(2, auditoria.getAccion());

            ps.setString(3, auditoria.getTablaAfectada());

            ps.setInt(4, auditoria.getRegistroId());

            ps.setString(5, auditoria.getDatosAnteriores());

            ps.setString(6, auditoria.getDatosNuevos());



            ps.executeUpdate();


            return true;



        } catch(Exception e){


            System.out.println(
                    "Error registrando auditoria: "
                    + e.getMessage()
            );


            return false;
        }

    }

    public List<Auditoria> listarTodas(){


        List<Auditoria> lista = new ArrayList<>();


        String sql =
                "SELECT * FROM auditorias " +
                "ORDER BY d_fecha_registro DESC";



        try {


            Connection cn = conexionBD.conectar();


            PreparedStatement ps =
                    cn.prepareStatement(sql);



            ResultSet rs =
                    ps.executeQuery();



            while(rs.next()){


                Auditoria a =
                        new Auditoria();



                a.setIdLog(
                        rs.getInt("id_log")
                );


                a.setIdUsuario(
                        rs.getInt("id_usuario")
                );


                a.setAccion(
                        rs.getString("t_accion")
                );


                a.setFechaRegistro(
                        rs.getTimestamp("d_fecha_registro")
                );


                a.setTablaAfectada(
                        rs.getString("t_tabla_afectada")
                );


                a.setRegistroId(
                        rs.getInt("t_registro_id")
                );


                a.setDatosAnteriores(
                        rs.getString("t_datos_anteriores")
                );


                a.setDatosNuevos(
                        rs.getString("t_datos_nuevos")
                );



                lista.add(a);

            }



        } catch(Exception e){


            System.out.println(
                    "Error consultando auditorias: "
                    + e.getMessage()
            );

        }



        return lista;

    }

}