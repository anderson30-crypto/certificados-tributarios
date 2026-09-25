package servicio;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.MessageDigest;


import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.security.BouncyCastleDigest;
import com.itextpdf.text.pdf.security.ExternalDigest;
import com.itextpdf.text.pdf.security.ExternalSignature;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PrivateKeySignature;


import org.bouncycastle.jce.provider.BouncyCastleProvider;


import dao.CertificadoDAO;



public class FirmaDigitalServicio {



    // FIRMA DIGITAL DEL PDF

    public boolean firmarPDF(
            String rutaPDF,
            String rutaSalida){


        try {


            Security.addProvider(
                    new BouncyCastleProvider()
            );



            String rutaCertificado =
                    "Seguridad/certificado.p12";



            String password =
                    "123456";



            KeyStore keyStore =
                    KeyStore.getInstance("PKCS12");



            keyStore.load(

                    new FileInputStream(rutaCertificado),

                    password.toCharArray()

            );



            String alias =
                    keyStore.aliases()
                    .nextElement();



            PrivateKey privateKey =
                    (PrivateKey)
                    keyStore.getKey(

                            alias,

                            password.toCharArray()

                    );



            Certificate[] cadena =
                    keyStore.getCertificateChain(alias);




            PdfReader reader =
                    new PdfReader(rutaPDF);



            FileOutputStream salida =
                    new FileOutputStream(rutaSalida);



            PdfStamper stamper =
                    PdfStamper.createSignature(

                            reader,

                            salida,

                            '\0'

                    );




            ExternalSignature firma =

                    new PrivateKeySignature(

                            privateKey,

                            "SHA256",

                            "BC"

                    );



            ExternalDigest digest =

                    new BouncyCastleDigest();




            Rectangle rectangulo =

                    new Rectangle(

                            36,

                            748,

                            200,

                            780

                    );



            com.itextpdf.text.pdf.PdfSignatureAppearance apariencia =
                    stamper.getSignatureAppearance();



            apariencia.setVisibleSignature(

                    rectangulo,

                    1,

                    "firma"

            );





            MakeSignature.signDetached(

                    apariencia,

                    digest,

                    firma,

                    cadena,

                    null,

                    null,

                    null,

                    0,

                    MakeSignature.CryptoStandard.CMS

            );



            System.out.println(
                    "PDF firmado correctamente"
            );



            return true;



        }catch(Exception e){


            System.out.println(

                    "Error firmando PDF: "

                    + e.getMessage()

            );


            return false;

        }


    }





    // FIRMA + HASH + ACTUALIZAR BASE DE DATOS


    public boolean firmarYActualizarCertificado(

            String rutaPDF,

            String rutaSalida,

            int idCertificado){



        try {



            boolean firmado =

                    firmarPDF(

                            rutaPDF,

                            rutaSalida

                    );



            if(!firmado){

                return false;

            }




            // GENERAR HASH SHA-256


            MessageDigest md =

                    MessageDigest.getInstance(

                            "SHA-256"

                    );



            FileInputStream archivo =

                    new FileInputStream(

                            rutaSalida

                    );



            byte[] buffer =

                    new byte[1024];



            int lectura;



            while(

                    (lectura = archivo.read(buffer))

                    != -1

            ){

                md.update(
                        buffer,
                        0,
                        lectura
                );

            }



            archivo.close();



            byte[] hashBytes =

                    md.digest();




            StringBuilder hash =

                    new StringBuilder();



            for(byte b : hashBytes){


                hash.append(

                        String.format(

                                "%02x",

                                b

                        )

                );


            }




            // ACTUALIZAR BD


            CertificadoDAO dao =

                    new CertificadoDAO();



            boolean actualizado =

                    dao.actualizarSeguridadCertificado(

                            idCertificado,

                            hash.toString(),

                            rutaSalida,

                            true,

                            null,

                            false

                    );




            if(actualizado){


                System.out.println(

                "Firma registrada correctamente en BD"

                );


                return true;

            }



            return false;



        }catch(Exception e){


            System.out.println(

            "Error actualizando firma: "

            + e.getMessage()

            );


            return false;

        }


    }


}