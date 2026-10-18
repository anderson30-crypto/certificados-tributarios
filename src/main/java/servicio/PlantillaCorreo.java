package servicio;

import conexion.ConexionBD;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Correo oficial que reciben los contratistas, con la estructura que entregó Nómina
 * ("Estructura de Correo electrónico Certificado ICA Floridablanca").
 * El texto está en src/main/resources/correo/plantilla_correo.html y la imagen del calendario
 * tributario en correo/calendario-ica.png; lo que cambia cada año se explica en la plantilla.
 */
public final class PlantillaCorreo {

    /** Content-ID de la imagen del calendario dentro del correo (src="cid:calendario"). */
    public static final String CID_CALENDARIO = "calendario";

    private static final String RESOLUCION_POR_DEFECTO = "6059 del 31/10/2025";

    private PlantillaCorreo() { }

    /** HTML del correo para un certificado del año gravable indicado; la imagen va como cid:. */
    public static String html(int anio) throws IOException {
        return html(anio, "cid:" + CID_CALENDARIO);
    }

    /** Igual, pero con la imagen en otra dirección (para la vista previa en el navegador). */
    public static String html(int anio, String srcCalendario) throws IOException {
        String plantilla = leer("/correo/plantilla_correo.html");
        // El comentario de ayuda del inicio no viaja en el correo
        plantilla = plantilla.replaceFirst("(?s)^\\s*<!--.*?-->\\s*", "");
        return plantilla
            .replace("{{ANIO}}", String.valueOf(anio))
            .replace("{{RESOLUCION}}", escapar(resolucion()))
            .replace("{{CALENDARIO}}", srcCalendario);
    }

    /** Versión en texto plano del mismo correo (para lectores que no muestran HTML). */
    public static String texto(int anio) throws IOException {
        String t = html(anio)
            .replaceAll(">\\s*\\n\\s*<", "><")
            .replaceAll("(?s)<img[^>]*>", "[Calendario tributario: fechas de vencimiento según los dos últimos dígitos del documento]")
            .replaceAll("</p>", "\n")
            .replaceAll("(?s)<[^>]+>", "")
            .replace("&nbsp;", "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
        return t.replaceAll("(?m)^[ \\t]+|[ \\t]+$", "").trim() + "\n";
    }

    /** Imágenes en línea del correo: Content-ID -> PNG. */
    public static Map<String, byte[]> imagenes() throws IOException {
        return Map.of(CID_CALENDARIO, calendario());
    }

    public static byte[] calendario() throws IOException {
        try (InputStream in = PlantillaCorreo.class.getResourceAsStream("/correo/calendario-ica.png")) {
            if (in == null) throw new IOException("No se encontró correo/calendario-ica.png");
            return in.readAllBytes();
        }
    }

    private static String resolucion() throws IOException {
        String valor = ConexionBD.cargarConfiguracion().getProperty("correo.resolucion", "").trim();
        return valor.isEmpty() ? RESOLUCION_POR_DEFECTO : valor;
    }

    private static String leer(String recurso) throws IOException {
        try (InputStream in = PlantillaCorreo.class.getResourceAsStream(recurso)) {
            if (in == null) throw new IOException("No se encontró la plantilla " + recurso);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
