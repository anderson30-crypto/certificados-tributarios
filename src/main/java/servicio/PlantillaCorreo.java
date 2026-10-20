package servicio;

import conexion.ConexionBD;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Correo oficial que reciben los contratistas, con la estructura que entregó Nómina
 * ("Estructura de Correo electrónico Certificado ICA Floridablanca").
 *
 * La estructura fija está en src/main/resources/correo/plantilla_correo.html. Lo que cambia cada año
 * (la resolución, el artículo transcrito, la imagen del calendario y el párrafo de recordatorio) lo edita
 * Nómina desde Enviar correo › Editar correo y queda en la tabla plantilla_correo; mientras no se edite,
 * se usa el texto original del documento de Nómina. En los textos, [AÑO] se cambia por el año gravable.
 */
public final class PlantillaCorreo {

    /** Content-ID de la imagen del calendario dentro del correo (src="cid:calendario"). */
    public static final String CID_CALENDARIO = "calendario";

    /** Marca que Nómina puede escribir en los textos; se reemplaza por el año gravable del certificado. */
    public static final String MARCA_ANIO = "[AÑO]";

    public static final String INTRODUCCION = "introduccion", ARTICULO_TITULO = "articulo_titulo",
        ARTICULO_TEXTO = "articulo_texto", RECORDATORIO = "recordatorio";
    private static final String CALENDARIO = "calendario";

    private static final int TAMANO_MAXIMO_IMAGEN = 2 * 1024 * 1024;   // 2 MB
    private static final int ANCHO_MAXIMO = 600;                        // px en el correo

    // Textos originales del documento de Nómina (se usan mientras no se editen)
    private static final Map<String, String> ORIGINAL = new LinkedHashMap<>();
    static {
        ORIGINAL.put(INTRODUCCION, "Se transcribe artículo 1 de la Resolución 6059 del 31/10/2025 de la Secretaría de Hacienda del municipio.");
        ORIGINAL.put(ARTICULO_TITULO, "ARTICULO 1.");
        ORIGINAL.put(ARTICULO_TEXTO, "Fíjese como último día de presentación oportuna de las declaraciones privadas de Industria " +
            "y Comercio avisos y tableros y sus complementarios del periodo gravable " + MARCA_ANIO + ". Estos vencimientos " +
            "corresponden a los dos últimos dígitos del número de identificación ciudadana o número de identificación " +
            "tributaria del contribuyente, sin tener en cuenta el dígito de verificación.");
        ORIGINAL.put(RECORDATORIO, "Señor contratista, recuerde que con el acuerdo municipal 045 de 2016 el Municipio de " +
            "Floridablanca gravó los contratos de prestación de servicios que se celebren en la jurisdicción del municipio " +
            "de Floridablanca a partir del 1 de enero de 2017, por tanto, debe presentar la declaración de Industria y " +
            "Comercio del año inmediatamente anterior en las fechas establecidas por la alcaldía.");
    }

    // Largo máximo de cada texto (el recordatorio puede quedar vacío para quitarlo del correo)
    private static final Map<String, Integer> LARGO_MAXIMO = Map.of(
        INTRODUCCION, 500, ARTICULO_TITULO, 60, ARTICULO_TEXTO, 4000, RECORDATORIO, 4000);

    public static class ReglaException extends Exception {
        public ReglaException(String mensaje) { super(mensaje); }
    }

    private PlantillaCorreo() { }

    // ---------- Armar el correo ----------

    /** HTML del correo para un certificado del año gravable indicado; la imagen va como cid:. */
    public static String html(int anio) throws IOException, SQLException {
        return html(anio, "cid:" + CID_CALENDARIO);
    }

    /** Igual, pero con la imagen en otra dirección (para la vista previa en el navegador). */
    public static String html(int anio, String srcCalendario) throws IOException, SQLException {
        Map<String, String> textos = textos();
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(calendario()));
        int ancho = Math.min(imagen.getWidth(), ANCHO_MAXIMO);
        int alto = Math.round(imagen.getHeight() * (ancho / (float) imagen.getWidth()));

        String recordatorio = textos.get(RECORDATORIO).isBlank() ? ""
            : "<p style=\"margin:0; text-align:justify;\">" + aHtml(textos.get(RECORDATORIO), anio) + "</p>\n" +
              "<p style=\"margin:0;\">&nbsp;</p>\n";

        String plantilla = leer("/correo/plantilla_correo.html");
        // El comentario de ayuda del inicio no viaja en el correo
        plantilla = plantilla.replaceFirst("(?s)^\\s*<!--.*?-->\\s*", "");
        return plantilla
            .replace("{{ANIO}}", String.valueOf(anio))
            .replace("{{INTRODUCCION}}", aHtml(textos.get(INTRODUCCION), anio))
            .replace("{{ARTICULO_TITULO}}", aHtml(textos.get(ARTICULO_TITULO), anio))
            .replace("{{ARTICULO_TEXTO}}", aHtml(textos.get(ARTICULO_TEXTO), anio))
            .replace("{{RECORDATORIO}}", recordatorio)
            .replace("{{CALENDARIO_ANCHO}}", String.valueOf(ancho))
            .replace("{{CALENDARIO_ALTO}}", String.valueOf(alto))
            .replace("{{CALENDARIO}}", srcCalendario);
    }

    /** Versión en texto plano del mismo correo (para lectores que no muestran HTML). */
    public static String texto(int anio) throws IOException, SQLException {
        String t = html(anio)
            .replaceAll(">\\s*\\n\\s*<", "><")
            .replaceAll("(?s)<img[^>]*>", "[Calendario tributario: fechas de vencimiento según los dos últimos dígitos del documento]")
            .replaceAll("<br>", "\n")
            .replaceAll("</p>", "\n")
            .replaceAll("(?s)<[^>]+>", "")
            .replace("&nbsp;", "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
        return t.replaceAll("(?m)^[ \\t]+|[ \\t]+$", "").trim() + "\n";
    }

    /** Imágenes en línea del correo: Content-ID -> PNG. */
    public static Map<String, byte[]> imagenes() throws IOException, SQLException {
        return Map.of(CID_CALENDARIO, calendario());
    }

    /** La imagen del calendario: la que subió Nómina o, si no ha subido ninguna, la del documento original. */
    public static byte[] calendario() throws IOException, SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement("SELECT b_archivo FROM plantilla_correo WHERE t_clave = ?")) {
            ps.setString(1, CALENDARIO);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBytes(1) != null) return rs.getBytes(1);
            }
        }
        try (InputStream in = PlantillaCorreo.class.getResourceAsStream("/correo/calendario-ica.png")) {
            if (in == null) throw new IOException("No se encontró correo/calendario-ica.png");
            return in.readAllBytes();
        }
    }

    // ---------- Editar (Enviar correo › Editar correo) ----------

    /** Lo editable del correo, con quién lo cambió por última vez (para la pantalla de edición). */
    public static Map<String, Object> contenido() throws SQLException {
        Map<String, Object> r = new LinkedHashMap<>(textos());
        r.put("imagenPersonalizada", false);
        r.put("original", ORIGINAL);
        r.put("marcaAnio", MARCA_ANIO);
        String sql = "SELECT p.t_clave, p.d_actualizacion, u.t_nombre_usuario FROM plantilla_correo p " +
            "LEFT JOIN usuarios u ON u.id_usuario = p.id_usuario ORDER BY p.d_actualizacion DESC";
        try (Connection conn = ConexionBD.obtenerConexion(); PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boolean primero = true;
            while (rs.next()) {
                if (primero) {
                    Timestamp fecha = rs.getTimestamp("d_actualizacion");
                    r.put("actualizado", fecha == null ? null : fecha.toLocalDateTime().toString());
                    r.put("actualizadoPor", rs.getString("t_nombre_usuario"));
                    primero = false;
                }
                if (CALENDARIO.equals(rs.getString("t_clave"))) r.put("imagenPersonalizada", true);
            }
        }
        return r;
    }

    /**
     * Guarda los textos (y la imagen, si llega una nueva). Devuelve qué cambió, para la auditoría.
     * imagen: bytes de un PNG o JPG, o null para dejar la actual.
     */
    public static String guardar(Map<String, String> nuevos, byte[] imagen, int idUsuario)
            throws ReglaException, SQLException, IOException {
        Map<String, String> limpios = new LinkedHashMap<>();
        for (String clave : ORIGINAL.keySet()) {
            String valor = nuevos.get(clave) == null ? "" : nuevos.get(clave).replace("\r\n", "\n").trim();
            if (valor.isEmpty() && !RECORDATORIO.equals(clave)) {
                throw new ReglaException("Falta " + nombre(clave) + ".");
            }
            if (valor.length() > LARGO_MAXIMO.get(clave)) {
                throw new ReglaException(primeraMayuscula(nombre(clave)) + " admite máximo " + LARGO_MAXIMO.get(clave) + " caracteres.");
            }
            limpios.put(clave, valor);
        }
        byte[] png = imagen == null ? null : convertirAPng(imagen);

        Map<String, String> antes = textos();
        StringBuilder cambios = new StringBuilder();
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            try {
                for (Map.Entry<String, String> t : limpios.entrySet()) {
                    if (t.getValue().equals(antes.get(t.getKey()))) continue;
                    guardarFila(conn, t.getKey(), t.getValue(), null, idUsuario);
                    cambios.append(cambios.length() > 0 ? " · " : "").append(nombre(t.getKey()));
                }
                if (png != null) {
                    guardarFila(conn, CALENDARIO, null, png, idUsuario);
                    cambios.append(cambios.length() > 0 ? " · " : "").append("imagen del calendario");
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return cambios.length() == 0 ? "sin cambios" : cambios.toString();
    }

    /** Vuelve al correo original del documento de Nómina (textos e imagen). */
    public static void restaurarOriginal() throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM plantilla_correo")) {
            ps.executeUpdate();
        }
    }

    // ---------- Internos ----------

    private static Map<String, String> textos() throws SQLException {
        Map<String, String> r = new LinkedHashMap<>(ORIGINAL);
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement("SELECT t_clave, t_valor FROM plantilla_correo WHERE t_valor IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (r.containsKey(rs.getString(1))) r.put(rs.getString(1), rs.getString(2));
            }
        }
        return r;
    }

    private static void guardarFila(Connection conn, String clave, String valor, byte[] archivo, int idUsuario) throws SQLException {
        String sql = "INSERT INTO plantilla_correo (t_clave, t_valor, b_archivo, id_usuario, d_actualizacion) " +
            "VALUES (?, ?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE t_valor = VALUES(t_valor), b_archivo = VALUES(b_archivo), " +
            "id_usuario = VALUES(id_usuario), d_actualizacion = NOW()";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clave);
            ps.setString(2, valor);
            ps.setBytes(3, archivo);
            ps.setInt(4, idUsuario);
            ps.executeUpdate();
        }
    }

    // Solo PNG o JPG que se puedan leer; se guarda siempre como PNG (así el correo la envía igual)
    private static byte[] convertirAPng(byte[] imagen) throws ReglaException, IOException {
        if (imagen.length == 0) throw new ReglaException("No se recibió ninguna imagen.");
        if (imagen.length > TAMANO_MAXIMO_IMAGEN) throw new ReglaException("La imagen supera 2 MB.");
        boolean png = imagen.length > 4 && (imagen[0] & 0xFF) == 0x89 && imagen[1] == 'P' && imagen[2] == 'N' && imagen[3] == 'G';
        boolean jpg = imagen.length > 3 && (imagen[0] & 0xFF) == 0xFF && (imagen[1] & 0xFF) == 0xD8;
        if (!png && !jpg) throw new ReglaException("La imagen debe ser PNG o JPG.");
        BufferedImage leida = ImageIO.read(new ByteArrayInputStream(imagen));
        if (leida == null) throw new ReglaException("No se pudo leer la imagen.");
        if (leida.getWidth() > 4000 || leida.getHeight() > 4000) {
            throw new ReglaException("La imagen es demasiado grande (máximo 4000 × 4000 píxeles).");
        }
        if (png) return imagen;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(leida, "png", salida);
        return salida.toByteArray();
    }

    // Texto escrito por Nómina -> HTML seguro: se escapan los caracteres especiales, [AÑO] pasa a ser el
    // año gravable y los saltos de línea se conservan
    private static String aHtml(String texto, int anio) {
        return escapar(texto).replace(MARCA_ANIO, String.valueOf(anio)).replace("\n", "<br>");
    }

    private static String nombre(String clave) {
        switch (clave) {
            case INTRODUCCION: return "el texto de la resolución";
            case ARTICULO_TITULO: return "el título del artículo";
            case ARTICULO_TEXTO: return "el texto del artículo";
            case RECORDATORIO: return "el párrafo de recordatorio";
            default: return clave;
        }
    }

    private static String primeraMayuscula(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String leer(String recurso) throws IOException {
        try (InputStream in = PlantillaCorreo.class.getResourceAsStream(recurso)) {
            if (in == null) throw new IOException("No se encontró la plantilla " + recurso);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
