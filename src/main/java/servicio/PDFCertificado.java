package servicio;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import modelo.Certificado;

public class PDFCertificado {


	public void generarPDF(Certificado certificado) {


        try {

        	String ruta =
        			"C:/Certificados/certificado_"
        			+ certificado.getIdCertificado()
        			+ ".pdf";


            Document documento = new Document();


            PdfWriter.getInstance(
                    documento,
                    new FileOutputStream(ruta)
            );


            documento.open();


            Font titulo = new Font(
                    Font.FontFamily.HELVETICA,
                    16,
                    Font.BOLD
            );


            Paragraph encabezado =
                    new Paragraph(
                            "CERTIFICADO TRIBUTARIO",
                            titulo
                    );


            encabezado.setAlignment(
                    Paragraph.ALIGN_CENTER
            );


            documento.add(encabezado);


            documento.add(
                    new Paragraph("\n")
            );


            documento.add(
            	    new Paragraph(
            	    "Sistema de Certificados Tributarios"
            	    )
            	);


            	documento.add(
            	    new Paragraph(
            	    "Nombre del contratista: "
            	    + certificado.getNombreContratista()
            	    )
            	);


            	documento.add(
            	    new Paragraph(
            	    "Documento: "
            	    + certificado.getDocumento()
            	    )
            	);


            	documento.add(
            	    new Paragraph(
            	    "Contrato: "
            	    + certificado.getContrato()
            	    )
            	);


            	documento.add(
            	    new Paragraph(
            	    "Salario: $"
            	    + certificado.getSalario()
            	    )
            	);


            	documento.add(
            	    new Paragraph(
            	    "Estado del certificado: "
            	    + certificado.getEstado()
            	    )
            	);


            	documento.add(
            	    new Paragraph(
            	    "Fecha de generación: "
            	    + certificado.getFechaGeneracion()
            	    )
            	);


            documento.close();


            System.out.println(
                    "PDF creado correctamente"
            );


        } catch(Exception e) {

            System.out.println(
                    "Error: " + e.getMessage()
            );

        }

    }
}
    

