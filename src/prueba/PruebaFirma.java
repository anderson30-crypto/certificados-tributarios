package prueba;


import servicio.FirmaDigitalServicio;


public class PruebaFirma {


    public static void main(String[] args) {


        FirmaDigitalServicio firma =
                new FirmaDigitalServicio();



        firma.firmarYActualizarCertificado(

        		"C:/Certificados/certificado_2.pdf",

        		"C:/Certificados/certificado_2_firmado.pdf",

        		2

        		);

    }

}