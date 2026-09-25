package prueba;


import servicio.CifradoServicio;



public class PruebaCifrado {


    public static void main(String[] args) {



        CifradoServicio cifrado =
                new CifradoServicio();



        cifrado.cifrarPDF(

        "C:/Certificados/certificado_2_firmado.pdf",

        "C:/Certificados/certificado_2_cifrado.enc",

        2

        );


    }


}
