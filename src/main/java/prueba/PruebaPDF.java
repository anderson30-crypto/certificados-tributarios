package prueba;

import dao.CertificadoDAO;
import modelo.Certificado;
import servicio.PDFCertificado;


public class PruebaPDF {


    public static void main(String[] args) {


        CertificadoDAO dao =
                new CertificadoDAO();


        Certificado certificado =
                dao.buscarPorId(2);



        if(certificado != null) {


            PDFCertificado pdf =
                    new PDFCertificado();


            pdf.generarPDF(certificado);


            dao.actualizarRutaPDF(
                    certificado.getIdCertificado(),
                    "C:/Certificados/certificado_"
                    + certificado.getIdCertificado()
                    + ".pdf",
                    "C:/Certificados/certificado_"
                    + certificado.getIdCertificado()
                    + ".pdf"
            );


            System.out.println(
            "PDF generado con datos reales"
            );


        } else {


            System.out.println(
            "No existe el certificado"
            );

        }


    }

}