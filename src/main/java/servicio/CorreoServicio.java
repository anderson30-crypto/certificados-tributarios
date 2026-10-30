package servicio;

import conexion.ConexionBD;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Envía correos por SMTP (Office 365 de la universidad) con el certificado PDF adjunto.
 * Toda la configuración se lee de config.properties (claves correo.*), nunca del código.
 */
public class CorreoServicio {

    private final String usuario;
    private final String redirigirA;
    private final long pausaMs;
    private final Session sesion;

    public CorreoServicio() throws IOException {

        Properties config = ConexionBD.cargarConfiguracion();

        usuario = obligatorio(config, "correo.usuario");
        String password = obligatorio(config, "correo.password");
        redirigirA = config.getProperty("correo.redirigir_a", "").trim();
        pausaMs = Long.parseLong(config.getProperty("correo.pausa_ms", "2500").trim());

        Properties smtp = new Properties();
        smtp.put("mail.smtp.host", obligatorio(config, "correo.host"));
        smtp.put("mail.smtp.port", config.getProperty("correo.puerto", "587").trim());
        smtp.put("mail.smtp.auth", "true");

        // Office 365 en el puerto 587 exige cifrar la conexión con STARTTLS
        boolean starttls = Boolean.parseBoolean(config.getProperty("correo.starttls", "true").trim());
        smtp.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        smtp.put("mail.smtp.starttls.required", String.valueOf(starttls));

        // Para que un servidor lento no deje colgado el WebServer
        smtp.put("mail.smtp.connectiontimeout", "15000");
        smtp.put("mail.smtp.timeout", "30000");

        sesion = Session.getInstance(smtp, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(usuario, password);
            }
        });
    }

    /** true si está activo el modo prueba (todo llega al correo de redirigir_a). */
    public boolean modoPrueba() {
        return !redirigirA.isEmpty();
    }

    /** A dónde llega realmente un correo dirigido a "para" (cambia en modo prueba). */
    public String destinoEfectivo(String para) {
        return modoPrueba() ? redirigirA : para;
    }

    /**
     * Envía un correo. Si correo.redirigir_a tiene un correo, el envío llega ahí (sin cambiar el
     * texto del correo); el destinatario real queda en la consola del WebServer.
     * Si Office 365 rechaza temporalmente el envío (códigos 4xx, como "421 4.3.2 Service not active"),
     * se reintenta hasta 3 veces con espera creciente.
     */
    public void enviar(String para, String cc, String asunto, String mensaje, Path adjunto) throws MessagingException {
        enviar(para, cc, asunto, mensaje, null, Map.of(), adjunto);
    }

    /**
     * Igual que el anterior pero con cuerpo HTML. "texto" es la versión en texto plano para los
     * lectores de correo que no muestran HTML; "imagenes" son las imágenes que el HTML usa con
     * src="cid:nombre" (nombre -> bytes PNG), que van dentro del correo y no como adjunto.
     */
    public void enviar(String para, String cc, String asunto, String texto, String html,
                       Map<String, byte[]> imagenes, Path adjunto) throws MessagingException {

        if (para == null || para.isBlank()) {
            throw new MessagingException("El destinatario no tiene correo registrado");
        }

        String destino = para;
        String copia = cc;
        String cuerpo = texto;

        if (modoPrueba()) {
            destino = redirigirA;
            copia = null;
            System.out.println("Correo para " + para + " redirigido a " + redirigirA);
        }

        MimeMessage correo = new MimeMessage(sesion);
        // Office 365 solo permite enviar como la misma cuenta con la que se inicia sesión
        correo.setFrom(new InternetAddress(usuario));
        correo.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destino));
        if (copia != null && !copia.isBlank()) {
            correo.setRecipients(Message.RecipientType.CC, InternetAddress.parse(copia));
        }
        correo.setSubject(asunto, StandardCharsets.UTF_8.name());

        Multipart partes = new MimeMultipart();

        if (html == null) {
            MimeBodyPart plano = new MimeBodyPart();
            plano.setText(cuerpo, StandardCharsets.UTF_8.name());
            partes.addBodyPart(plano);
        } else {
            partes.addBodyPart(cuerpoHtml(cuerpo, html, imagenes));
        }

        if (adjunto != null) {
            if (!Files.exists(adjunto)) {
                throw new MessagingException("No existe el PDF a adjuntar: " + adjunto);
            }
            MimeBodyPart pdf = new MimeBodyPart();
            FileDataSource archivo = new FileDataSource(adjunto.toFile());
            if (adjunto.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                archivo.setFileTypeMap(new jakarta.activation.MimetypesFileTypeMap() {
                    @Override public String getContentType(String nombre) { return "application/pdf"; }
                });
            }
            pdf.setDataHandler(new DataHandler(archivo));
            pdf.setFileName(adjunto.getFileName().toString());
            partes.addBodyPart(pdf);
        }

        correo.setContent(partes);

        long[] esperas = { 5000, 15000 };
        for (int intento = 0; ; intento++) {
            try {
                Transport.send(correo);
                return;
            } catch (MessagingException e) {
                    if (!esTemporal(e) || intento >= esperas.length) {
                    System.out.println("No se pudo enviar el correo a " 
    + destino + ": " + (e.getMessage() == null ? e : 
    e.getMessage().trim()));
                    throw new MessagingException(mensajeClaro(e), e);
                }
                System.out.println("Office 365 rechazó temporalmente el envío a " + destino + " (" + e.getMessage().trim() +
                    "); nuevo intento en " + esperas[intento] / 1000 + " s");
                try {
                    Thread.sleep(esperas[intento]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    // multipart/alternative (texto | multipart/related (html + imágenes en línea))
    private static MimeBodyPart cuerpoHtml(String texto, String html, Map<String, byte[]> imagenes) throws MessagingException {
        MimeBodyPart plano = new MimeBodyPart();
        plano.setText(texto, StandardCharsets.UTF_8.name());

        MimeMultipart relacionado = new MimeMultipart("related");
        MimeBodyPart parteHtml = new MimeBodyPart();
        parteHtml.setText(html, StandardCharsets.UTF_8.name(), "html");
        relacionado.addBodyPart(parteHtml);
        for (Map.Entry<String, byte[]> imagen : imagenes.entrySet()) {
            MimeBodyPart parteImagen = new MimeBodyPart();
            parteImagen.setDataHandler(new DataHandler(new ByteArrayDataSource(imagen.getValue(), "image/png")));
            parteImagen.setContentID("<" + imagen.getKey() + ">");
            parteImagen.setDisposition(MimeBodyPart.INLINE);
            parteImagen.setFileName(imagen.getKey() + ".png");
            relacionado.addBodyPart(parteImagen);
        }
        MimeBodyPart conImagenes = new MimeBodyPart();
        conImagenes.setContent(relacionado);

        MimeMultipart alternativas = new MimeMultipart("alternative");
        alternativas.addBodyPart(plano);
        alternativas.addBodyPart(conImagenes);
        MimeBodyPart cuerpo = new MimeBodyPart();
        cuerpo.setContent(alternativas);
        return cuerpo;
    }

    // Respuestas SMTP 4xx = el servidor no pudo en ese momento (se puede reintentar)
    private static boolean esTemporal(MessagingException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().trim();
        return m.matches("(?s)^4\\d\\d\\b.*") || m.contains(" 4.3.") || m.contains(" 4.4.") || m.contains(" 4.7.");
    }

    // Mensaje entendible para la pantalla (el detalle técnico queda en la consola)
    private static String mensajeClaro(MessagingException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().trim();
        if (esTemporal(e)) {
            return "el servidor de correo (Office 365) no aceptó el envío en ese momento; intente de nuevo en unos minutos";
        }
        if (m.startsWith("535") || m.toLowerCase().contains("authentication")) {
            return "la cuenta de correo del aplicativo no pudo iniciar sesión en Office 365 (revise usuario y contraseña de aplicación)";
        }
        if (m.startsWith("550") || m.startsWith("553")) {
            return "el servidor de correo rechazó la dirección del destinatario";
        }
        return m;
    }

    /** Espera entre correos para no superar los límites de envío de Exchange Online. */
    public void pausa() {
        try {
            Thread.sleep(pausaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String obligatorio(Properties config, String clave) throws IOException {
        String valor = config.getProperty(clave);
        if (valor == null || valor.isBlank()) {
            throw new IOException("Falta la clave " + clave + " en config.properties (ver config.properties.example)");
        }
        return valor.trim();
    }
}
