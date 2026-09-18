package dao;


import conexion.conexionBD;
import modelo.Certificado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;



public class CertificadoDAO {


    // BUSCAR CERTIFICADO POR ID

    public Certificado buscarPorId(int id) {


        Certificado certificado = null;


        String sql =

        "SELECT " +
        "c.id_certificado, " +
        "c.d_fecha_generacion, " +
        "c.t_ruta_pdf, " +
        "c.t_ruta_local, " +
        "c.t_hash_certificado, " +
        "c.t_ruta_firma, " +
        "c.b_firmado, " +
        "c.t_ruta_cifrado, " +
        "c.b_cifrado, " +
        "ct.t_nombres, " +
        "ct.n_numero_documento, " +
        "co.id_contrato, " +
        "co.n_salario, " +
        "e.t_nombre_estado " +

        "FROM certificados c " +

        "INNER JOIN contratos co " +
        "ON c.id_contrato = co.id_contrato " +

        "INNER JOIN contratistas ct " +
        "ON co.id_contratista = ct.id_contratista " +

        "INNER JOIN estados e " +
        "ON c.id_estado = e.id_estado " +

        "WHERE c.id_certificado=?";



        try {


            Connection cn =
                    conexionBD.conectar();



            PreparedStatement ps =
                    cn.prepareStatement(sql);



            ps.setInt(1, id);



            ResultSet rs =
                    ps.executeQuery();



            if(rs.next()){


                certificado =
                        new Certificado();



                certificado.setIdCertificado(
                        rs.getInt("id_certificado")
                );



                certificado.setNombreContratista(
                        rs.getString("t_nombres")
                );



                certificado.setDocumento(
                        rs.getString("n_numero_documento")
                );



                certificado.setContrato(
                        String.valueOf(
                        rs.getInt("id_contrato")
                        )
                );



                certificado.setSalario(
                        rs.getDouble("n_salario")
                );



                certificado.setFechaGeneracion(
                        rs.getDate("d_fecha_generacion")
                );



                certificado.setEstado(
                        rs.getString("t_nombre_estado")
                );



                certificado.setRutaPdf(
                        rs.getString("t_ruta_pdf")
                );



                certificado.setHashCertificado(
                        rs.getString("t_hash_certificado")
                );



                certificado.setRutaFirma(
                        rs.getString("t_ruta_firma")
                );



                certificado.setFirmado(
                        rs.getBoolean("b_firmado")
                );



                certificado.setRutaCifrado(
                        rs.getString("t_ruta_cifrado")
                );



                certificado.setCifrado(
                        rs.getBoolean("b_cifrado")
                );


            }



        }catch(Exception e){


            System.out.println(
            "Error buscar certificado: "
            + e.getMessage()
            );


        }



        return certificado;

    }





    // ACTUALIZAR RUTA DEL PDF GENERADO


    public boolean actualizarRutaPDF(

            int idCertificado,

            String rutaPdf,

            String rutaLocal){



        String sql =

        "UPDATE certificados SET " +

        "t_ruta_pdf=?, " +

        "t_ruta_local=? " +

        "WHERE id_certificado=?";




        try {


            Connection cn =
                    conexionBD.conectar();



            PreparedStatement ps =
                    cn.prepareStatement(sql);



            ps.setString(1, rutaPdf);


            ps.setString(2, rutaLocal);


            ps.setInt(3, idCertificado);



            ps.executeUpdate();



            return true;



        }catch(Exception e){


            System.out.println(

            "Error actualizando ruta PDF: "

            + e.getMessage()

            );


            return false;

        }


    }





    // ACTUALIZAR INFORMACION DE SEGURIDAD
    // FIRMA Y CIFRADO


    public boolean actualizarSeguridadCertificado(

            int idCertificado,

            String hash,

            String rutaFirma,

            boolean firmado,

            String rutaCifrado,

            boolean cifrado){



        String sql =

        "UPDATE certificados SET "

        + "t_hash_certificado=?, "

        + "t_ruta_firma=?, "

        + "b_firmado=?, "

        + "t_ruta_cifrado=?, "

        + "b_cifrado=? "

        + "WHERE id_certificado=?";



        try {


            Connection cn =
                    conexionBD.conectar();



            PreparedStatement ps =
                    cn.prepareStatement(sql);



            ps.setString(1, hash);


            ps.setString(2, rutaFirma);


            ps.setBoolean(3, firmado);


            ps.setString(4, rutaCifrado);


            ps.setBoolean(5, cifrado);


            ps.setInt(6, idCertificado);



            ps.executeUpdate();



            return true;



        }catch(Exception e){


            System.out.println(

            "Error actualizando seguridad certificado: "

            + e.getMessage()

            );



            return false;

        }

    }



}