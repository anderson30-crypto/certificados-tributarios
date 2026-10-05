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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Envía un correo. Si el modo prueba está activo, el correo llega a correo.redirigir_a
     * y en el cuerpo se indica a quién iba dirigido realmente.
     */
    public void enviar(String para, String cc, String asunto, String mensaje, Path adjunto) throws MessagingException {

        if (para == null || para.isBlank()) {
            throw new MessagingException("El destinatario no tiene correo registrado");
        }

        String destino = para;
        String copia = cc;
        String cuerpo = mensaje;

        if (modoPrueba()) {
            destino = redirigirA;
            copia = null;
            cuerpo = "[MODO PRUEBA] Este correo iba dirigido a: " + para +
                (cc == null || cc.isBlank() ? "" : " (CC: " + cc + ")") + "\n\n" + mensaje;
        }

        MimeMessage correo = new MimeMessage(sesion);
        // Office 365 solo permite enviar como la misma cuenta con la que se inicia sesión
        correo.setFrom(new InternetAddress(usuario));
        correo.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destino));
        if (copia != null && !copia.isBlank()) {
            correo.setRecipients(Message.RecipientType.CC, InternetAddress.parse(copia));
        }
        correo.setSubject(asunto, StandardCharsets.UTF_8.name());

        MimeBodyPart texto = new MimeBodyPart();
        texto.setText(cuerpo, StandardCharsets.UTF_8.name());

        Multipart partes = new MimeMultipart();
        partes.addBodyPart(texto);

        if (adjunto != null) {
            if (!Files.exists(adjunto)) {
                throw new MessagingException("No existe el PDF a adjuntar: " + adjunto);
            }
            MimeBodyPart pdf = new MimeBodyPart();
            pdf.setDataHandler(new DataHandler(new FileDataSource(adjunto.toFile())));
            pdf.setFileName(adjunto.getFileName().toString());
            partes.addBodyPart(pdf);
        }

        correo.setContent(partes);
        Transport.send(correo);
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
