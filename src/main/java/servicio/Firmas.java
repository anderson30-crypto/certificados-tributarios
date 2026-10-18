package servicio;

import Seguridad.EsquemaBD;
import conexion.ConexionBD;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Firma de los certificados por el Jefe de Nómina (o Gestión Humana cuando el Jefe no está).
 *
 *  - Cada firmante registra su firma como imagen PNG (tabla firmas_usuario).
 *  - Un certificado recién generado (estado Generado o Corregido) queda PENDIENTE DE FIRMA y no se
 *    puede enviar por correo.
 *  - Al firmar: la imagen se estampa sobre "Atentamente," en el PDF, el PDF se cifra con la cédula
 *    del contratista como contraseña de apertura y el certificado pasa a estado Firmado. Desde ese
 *    momento Nómina lo puede enviar.
 */
public final class Firmas {

    public static final int ESTADO_GENERADO = 2;
    public static final int ESTADO_ENVIADO = 3;
    public static final int ESTADO_CORREGIDO = 4;
    public static final int ESTADO_FIRMADO = EsquemaBD.ESTADO_FIRMADO;

    private static final int TAMANO_MAXIMO_PNG = 2 * 1024 * 1024;   // 2 MB
    private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G' };

    // Espacio de la firma en el certificado (pt): a la derecha de "Atentamente," y encima del nombre
    private static final float FIRMA_X = 117.2f, FIRMA_ANCHO = 150f, FIRMA_ALTO = 55f;

    public static class ReglaException extends Exception {
        public ReglaException(String mensaje) { super(mensaje); }
    }

    private Firmas() { }

    // ---------- Firma de cada firmante (imagen PNG) ----------

    /** Guarda (o reemplaza) la firma del usuario. Solo PNG; se recortan los bordes vacíos. */
    public static void guardarFirma(int idUsuario, byte[] png) throws ReglaException, SQLException, IOException {
        if (png == null || png.length == 0) throw new ReglaException("No se recibió ninguna imagen.");
        if (png.length > TAMANO_MAXIMO_PNG) throw new ReglaException("La imagen supera 2 MB.");
        for (int i = 0; i < FIRMA_PNG.length; i++) {
            if (png[i] != FIRMA_PNG[i]) throw new ReglaException("La firma debe ser una imagen PNG.");
        }
        BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(png));
        if (imagen == null) throw new ReglaException("No se pudo leer la imagen PNG.");
        if (imagen.getWidth() > 4000 || imagen.getHeight() > 4000) {
            throw new ReglaException("La imagen es demasiado grande (máximo 4000 × 4000 píxeles).");
        }
        BufferedImage recortada = recortarBordes(imagen);
        if (recortada == null) throw new ReglaException("La imagen está vacía: no tiene ningún trazo.");

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(recortada, "png", salida);

        String sql = "INSERT INTO firmas_usuario (id_usuario, b_imagen, d_actualizacion) VALUES (?, ?, NOW()) " +
            "ON DUPLICATE KEY UPDATE b_imagen = VALUES(b_imagen), d_actualizacion = NOW()";
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.setBytes(2, salida.toByteArray());
            ps.executeUpdate();
        }
    }

    /** La firma PNG del usuario, o null si no tiene. */
    public static byte[] obtenerFirma(int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement("SELECT b_imagen FROM firmas_usuario WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    public static void eliminarFirma(int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM firmas_usuario WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
    }

    // Quita los bordes transparentes o blancos para que la firma ocupe su espacio en el PDF
    private static BufferedImage recortarBordes(BufferedImage img) {
        int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
                boolean vacio = a < 16 || (r > 240 && g > 240 && b > 240);
                if (!vacio) {
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) return null;
        int margen = 4;
        minX = Math.max(0, minX - margen); minY = Math.max(0, minY - margen);
        maxX = Math.min(img.getWidth() - 1, maxX + margen); maxY = Math.min(img.getHeight() - 1, maxY + margen);
        BufferedImage copia = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
        copia.getGraphics().drawImage(img.getSubimage(minX, minY, copia.getWidth(), copia.getHeight()), 0, 0, null);
        return copia;
    }

    // ---------- Certificados por firmar ----------

    private static final String SELECT_CERTIFICADO =
        "SELECT ce.id_certificado, ce.id_estado, ce.t_ruta_pdf, ce.d_fecha_generacion, ce.b_certificado_corregido, " +
        "ce.d_fecha_firma, ct.t_nombres, ct.t_primer_apellido, ct.t_segundo_apellido, ct.n_numero_documento, " +
        "ug.t_nombre_usuario AS generado_por, uf.t_nombre_usuario AS firmado_por " +
        "FROM certificados ce JOIN contratos co ON co.id_contrato = ce.id_contrato " +
        "JOIN contratistas ct ON ct.id_contratista = co.id_contratista " +
        "LEFT JOIN usuarios ug ON ug.id_usuario = ce.id_usuario " +
        "LEFT JOIN usuarios uf ON uf.id_usuario = ce.id_usuario_firma ";

    /** Certificados generados o corregidos que esperan firma (los más antiguos primero). */
    public static List<Map<String, Object>> pendientes() throws SQLException {
        return consultar(SELECT_CERTIFICADO + "WHERE ce.id_estado IN (?, ?) ORDER BY ce.id_certificado",
            ESTADO_GENERADO, ESTADO_CORREGIDO);
    }

    /** Últimos certificados firmados (estén o no enviados). */
    public static List<Map<String, Object>> firmadosRecientes(int limite) throws SQLException {
        return consultar(SELECT_CERTIFICADO + "WHERE ce.id_usuario_firma IS NOT NULL ORDER BY ce.d_fecha_firma DESC LIMIT " + limite);
    }

    /** Firmados que Nómina todavía no ha enviado (listos para el correo). */
    public static List<Map<String, Object>> listosParaEnviar() throws SQLException {
        return consultar(SELECT_CERTIFICADO + "WHERE ce.id_estado = ? ORDER BY ce.d_fecha_firma DESC", ESTADO_FIRMADO);
    }

    private static List<Map<String, Object>> consultar(String sql, Object... parametros) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) ps.setObject(i + 1, parametros[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    String segundo = rs.getString("t_segundo_apellido");
                    c.put("idCertificado", rs.getInt("id_certificado"));
                    c.put("estado", rs.getInt("id_estado"));
                    c.put("contratista", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido") +
                        (segundo == null ? "" : " " + segundo));
                    c.put("documento", GenerarCertificado.formatearDocumento(rs.getString("n_numero_documento")));
                    c.put("archivo", rs.getString("t_ruta_pdf") == null ? null : Paths.get(rs.getString("t_ruta_pdf")).getFileName().toString());
                    c.put("fechaGeneracion", String.valueOf(rs.getDate("d_fecha_generacion")));
                    c.put("corregido", rs.getBoolean("b_certificado_corregido"));
                    c.put("generadoPor", rs.getString("generado_por"));
                    c.put("firmadoPor", rs.getString("firmado_por"));
                    Timestamp f = rs.getTimestamp("d_fecha_firma");
                    c.put("fechaFirma", f == null ? null : f.toLocalDateTime().toString());
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    // ---------- Firmar ----------

    /**
     * Firma los certificados indicados con la firma registrada del usuario. Cada uno se procesa por
     * separado: si uno falla, los demás siguen. Devuelve el resultado de cada uno.
     */
    public static List<Map<String, Object>> firmar(List<Integer> ids, int idUsuario) throws Exception {
        byte[] firma = obtenerFirma(idUsuario);
        if (firma == null) {
            throw new ReglaException("Primero registre su firma en Perfil › Firma Digital.");
        }
        List<Map<String, Object>> resultados = new ArrayList<>();
        try (Connection conn = ConexionBD.obtenerConexion()) {
            for (int id : ids) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("idCertificado", id);
                try {
                    r.put("contratista", firmarUno(conn, id, idUsuario, firma));
                    r.put("exito", true);
                } catch (Exception e) {
                    r.put("exito", false);
                    r.put("error", e.getMessage());
                }
                resultados.add(r);
            }
        }
        return resultados;
    }

    private static String firmarUno(Connection conn, int idCertificado, int idUsuario, byte[] firma) throws Exception {
        String sql = "SELECT ce.id_estado, ce.t_ruta_pdf, ct.n_numero_documento, ct.t_nombres, ct.t_primer_apellido " +
            "FROM certificados ce JOIN contratos co ON co.id_contrato = ce.id_contrato " +
            "JOIN contratistas ct ON ct.id_contratista = co.id_contratista WHERE ce.id_certificado = ?";
        int estado;
        String ruta, documento, nombre;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCertificado);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ReglaException("El certificado " + idCertificado + " no existe.");
                estado = rs.getInt("id_estado");
                ruta = rs.getString("t_ruta_pdf");
                documento = rs.getString("n_numero_documento").replaceAll("[^0-9A-Za-z]", "");
                nombre = rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido");
            }
        }
        if (estado != ESTADO_GENERADO && estado != ESTADO_CORREGIDO) {
            throw new ReglaException("El certificado de " + nombre + " no está pendiente de firma.");
        }
        if (ruta == null || !new File(ruta).isFile()) {
            throw new ReglaException("No se encontró el PDF del certificado de " + nombre + ".");
        }

        String rutaFirmado = ruta.replaceFirst("\\.pdf$", "") + "_firmado.pdf";
        estamparYCifrar(new File(ruta), new File(rutaFirmado), firma, documento);

        // Comprobación: el PDF firmado abre con la cédula y trae el número de documento
        try (PDDocument pdf = PDDocument.load(new File(rutaFirmado), documento)) {
            String texto = new PDFTextStripper().getText(pdf).replaceAll("[^0-9]", "");
            if (!texto.contains(documento.replaceAll("[^0-9]", ""))) {
                throw new ReglaException("El PDF firmado no pasó la verificación.");
            }
        } catch (IOException e) {
            Files.deleteIfExists(Paths.get(rutaFirmado));
            throw new ReglaException("El PDF firmado no se pudo abrir con la cédula: " + e.getMessage());
        }

        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE certificados SET id_estado = ?, id_usuario_firma = ?, d_fecha_firma = NOW(), t_ruta_pdf = ? " +
                    "WHERE id_certificado = ? AND id_estado = ?")) {
                ps.setInt(1, ESTADO_FIRMADO);
                ps.setInt(2, idUsuario);
                ps.setString(3, rutaFirmado);
                ps.setInt(4, idCertificado);
                ps.setInt(5, estado);
                if (ps.executeUpdate() != 1) throw new ReglaException("El certificado cambió mientras se firmaba; intente de nuevo.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO historial_certificados (id_certificado, id_usuario, id_estado_anterior, id_estado_nuevo, t_accion, t_observacion) " +
                    "VALUES (?, ?, ?, ?, 'Firma', 'Firmado y cifrado con la cédula del contratista')")) {
                ps.setInt(1, idCertificado);
                ps.setInt(2, idUsuario);
                ps.setInt(3, estado);
                ps.setInt(4, ESTADO_FIRMADO);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            Files.deleteIfExists(Paths.get(rutaFirmado));
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
        // El PDF sin firma ni cifrado ya no se necesita: el oficial es el firmado
        Files.deleteIfExists(Paths.get(ruta));
        return nombre;
    }

    /** Pone la imagen de la firma sobre "Atentamente," y cifra el PDF (contraseña de apertura = documento). */
    static void estamparYCifrar(File origen, File destino, byte[] firmaPng, String documento) throws Exception {
        try (PDDocument pdf = PDDocument.load(origen)) {
            PDPage pagina = pdf.getPage(0);
            float altoPagina = pagina.getMediaBox().getHeight();
            float baseAtentamente = buscarAtentamente(pdf);

            PDImageXObject imagen = LosslessFactory.createFromImage(pdf, ImageIO.read(new ByteArrayInputStream(firmaPng)));
            float escala = Math.min(FIRMA_ANCHO / imagen.getWidth(), FIRMA_ALTO / imagen.getHeight());
            float ancho = imagen.getWidth() * escala, alto = imagen.getHeight() * escala;
            // Centro vertical del espacio de la firma: 20 pt bajo la línea de "Atentamente," (como el formato oficial)
            float centroDesdeArriba = baseAtentamente + 20f;
            float y = altoPagina - centroDesdeArriba - alto / 2;

            try (PDPageContentStream cs = new PDPageContentStream(pdf, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.drawImage(imagen, FIRMA_X, y, ancho, alto);
            }

            AccessPermission permisos = new AccessPermission();
            permisos.setCanModify(false);
            permisos.setCanAssembleDocument(false);
            permisos.setCanModifyAnnotations(false);
            permisos.setCanFillInForm(false);
            permisos.setCanPrint(true);
            permisos.setCanExtractContent(true);
            StandardProtectionPolicy politica = new StandardProtectionPolicy(clavePropietario(), documento, permisos);
            politica.setEncryptionKeyLength(256);
            pdf.protect(politica);
            pdf.save(destino);
        }
    }

    // Posición (desde arriba, en pt) de la línea base de "Atentamente,"
    private static float buscarAtentamente(PDDocument pdf) throws IOException {
        final float[] y = { -1 };
        PDFTextStripper lector = new PDFTextStripper() {
            @Override
            protected void writeString(String texto, List<TextPosition> posiciones) {
                if (y[0] < 0 && texto.trim().startsWith("Atentamente")) {
                    y[0] = posiciones.get(0).getYDirAdj();
                }
            }
        };
        lector.setSortByPosition(true);
        lector.getText(pdf);
        if (y[0] < 0) throw new IOException("no se encontró \"Atentamente,\" en el certificado");
        return y[0];
    }

    // Contraseña de propietario (permite cambiar permisos); la de apertura es la cédula
    private static String clavePropietario() {
        String clave = ConexionBD.obtener("cifrado.clave", null);
        if (clave != null) return clave;
        byte[] azar = new byte[24];
        new SecureRandom().nextBytes(azar);
        return HexFormat.of().formatHex(azar);
    }
}
