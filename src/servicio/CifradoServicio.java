package servicio;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import dao.CertificadoDAO;



public class CifradoServicio {


    private String clave =
            "Certificados1234";



    public boolean cifrarPDF(

            String rutaPDF,

            String rutaSalida,

            int idCertificado){


        try {



            // Crear clave AES

            MessageDigest sha =
                    MessageDigest.getInstance("SHA-256");


            byte[] claveBytes =
                    sha.digest(
                    clave.getBytes("UTF-8")
                    );


            SecretKeySpec secretKey =
                    new SecretKeySpec(
                    claveBytes,
                    "AES"
                    );



            Cipher cipher =
                    Cipher.getInstance(
                    "AES"
                    );


            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey
            );



            FileInputStream entrada =
                    new FileInputStream(
                    rutaPDF
                    );



            byte[] contenido =
                    new byte[
                    entrada.available()
                    ];



            entrada.read(contenido);

            entrada.close();



            byte[] cifrado =
                    cipher.doFinal(
                    contenido
                    );



            FileOutputStream salida =
                    new FileOutputStream(
                    rutaSalida
                    );



            salida.write(cifrado);

            salida.close();




            // Actualizar base de datos


            CertificadoDAO dao =
                    new CertificadoDAO();



            boolean actualizado =
                    dao.actualizarSeguridadCertificado(

                    idCertificado,

                    null,

                    null,

                    true,

                    rutaSalida,

                    true

                    );



            if(actualizado){


                System.out.println(
                "Documento cifrado correctamente"
                );


                return true;

            }



            return false;



        }catch(Exception e){


            System.out.println(
            "Error cifrando documento: "
            + e.getMessage()
            );


            return false;

        }


    }



}