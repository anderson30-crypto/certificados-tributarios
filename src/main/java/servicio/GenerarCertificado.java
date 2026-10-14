package servicio;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import conexion.ConexionBD;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Genera el certificado de ingresos y retención de ICA en PDF con el formato oficial de la UPB
 * (plantilla_certificado.html) y lo registra en la tabla certificados.
 */
public class GenerarCertificado {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");

    // Miles con punto, sin decimales, sin importar el idioma del equipo: 4.170.357
    private static final DecimalFormat FORMATO;
    static {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(ES_CO);
        simbolos.setGroupingSeparator('.');
        FORMATO = new DecimalFormat("#,##0", simbolos);
    }

    // Carpeta (en la raíz del proyecto) donde quedan los PDF generados
    public static final String CARPETA_SALIDA = "certificados_generados";

    // Usuario que queda registrado cuando se genera sin sesión (por ejemplo, desde el main de prueba)
    public static final int ID_USUARIO_SISTEMA = 1;

    // Tabla estados: 2 = Generado, 4 = Corregido (generado de nuevo después de una devolución)
    private static final int ESTADO_GENERADO = 2;
    public static final int ESTADO_CORREGIDO = 4;

    // Conceptos del certificado: marcador de la plantilla -> columna de la tabla contratos
    private static final Map<String, String> CONCEPTOS = new LinkedHashMap<>();
    static {
        CONCEPTOS.put("SALARIO", "n_salario");
        CONCEPTOS.put("HONORARIOS", "n_honorarios");
        CONCEPTOS.put("SERVICIOS", "n_servicios");
        CONCEPTOS.put("COMISIONES", "n_comisiones");
        CONCEPTOS.put("PRESTACIONES", "n_prestaciones");
        CONCEPTOS.put("VIATICOS", "n_pago_viaticos");
        CONCEPTOS.put("GASTOS_REP", "n_gastos");
        CONCEPTOS.put("OTROS_INGRESOS", "n_otros_ingresos");
        CONCEPTOS.put("CESANTIAS_EMPLEADO", "n_cesantias_empleado");
        CONCEPTOS.put("CESANTIAS_FONDO", "n_cesantias_fondo");
        CONCEPTOS.put("PENSIONES", "n_pensiones");
        CONCEPTOS.put("TOTAL_INGRESOS", "n_total_ing_brutos");
        CONCEPTOS.put("RET_ICA", "n_ret_ica");
    }

    /** Lo que devuelve la generación: el registro en la tabla certificados y el PDF creado. */
    public record Resultado(int idCertificado, String rutaPdf) { }

    /** Datos que van en el certificado (los mismos que se usan para la huella). */
    public record Datos(int idContrato, String nombreCompleto, String tipoDocumento, String documento, int anio,
                        Map<String, Double> valores) { }

    public static void main(String[] args) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            int idContratista = obtenerUltimoIdContratista(conn);
            Resultado resultado = generarParaContratista(idContratista);
            System.out.println("Certificado generado: " + resultado.rutaPdf() +
                " (id_certificado " + resultado.idCertificado() + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Resultado generarParaContratista(int idContratista) throws Exception {
        return generarParaContratista(idContratista, ID_USUARIO_SISTEMA);
    }

    /** Genera el PDF y lo registra a nombre de quien lo generó (el usuario con sesión iniciada). */
    public static Resultado generarParaContratista(int idContratista, int idUsuario) throws Exception {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            Datos datos = leerDatos(conn, idContratista);
            String ruta = crearPdfVerificado(datos);
            try {
                int id = registrarCertificado(conn, datos, ruta, idUsuario, null);
                return new Resultado(id, ruta);
            } catch (Exception e) {
                Files.deleteIfExists(Paths.get(ruta));
                throw e;
            }
        }
    }

    /**
     * Genera un certificado nuevo que reemplaza a uno devuelto. El devuelto no se modifica:
     * el nuevo queda en estado Corregido y apunta al anterior (id_certificado_anterior).
     */
    public static Resultado generarReemplazo(Connection conn, int idContratista, int idUsuario, int idAnterior)
            throws Exception {
        Datos datos = leerDatos(conn, idContratista);
        String ruta = crearPdfVerificado(datos);
        try {
            int id = registrarCertificado(conn, datos, ruta, idUsuario, idAnterior);
            return new Resultado(id, ruta);
        } catch (Exception e) {
            Files.deleteIfExists(Paths.get(ruta));
            throw e;
        }
    }

    /** Crea el PDF y revisa que quedó bien; si no, lo borra y avisa el problema. */
    private static String crearPdfVerificado(Datos datos) throws Exception {
        String ruta = crearPdf(datos);
        String problema = verificarPdf(ruta, datos);
        if (problema != null) {
            Files.deleteIfExists(Paths.get(ruta));
            throw new Exception("El certificado de " + datos.nombreCompleto() + " no pasó la verificación: " + problema);
        }
        return ruta;
    }

    /** Datos actuales del contratista (contrato del año más reciente). */
    public static Datos leerDatos(Connection conn, int idContratista) throws Exception {
        String sql = "SELECT c.t_primer_apellido, c.t_segundo_apellido, c.t_nombres, c.t_tipo_documento, " +
            "c.n_numero_documento, co.id_contrato, co.n_anio, co." + String.join(", co.", CONCEPTOS.values()) + " " +
            "FROM contratistas c JOIN contratos co ON c.id_contratista = co.id_contratista " +
            "WHERE c.id_contratista = ? ORDER BY co.n_anio DESC LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idContratista);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new Exception("No se encontró el contratista con id " + idContratista + " o no tiene contrato");
                }
                String segundoApellido = rs.getString("t_segundo_apellido");
                String nombre = rs.getString("t_nombres").trim() + " " + rs.getString("t_primer_apellido").trim() +
                    (segundoApellido == null || segundoApellido.isBlank() ? "" : " " + segundoApellido.trim());
                Map<String, Double> valores = new LinkedHashMap<>();
                for (Map.Entry<String, String> c : CONCEPTOS.entrySet()) {
                    valores.put(c.getKey(), rs.getDouble(c.getValue()));
                }
                return new Datos(rs.getInt("id_contrato"), nombre.toUpperCase(ES_CO), rs.getString("t_tipo_documento"),
                    rs.getString("n_numero_documento").trim(), rs.getInt("n_anio"), valores);
            }
        }
    }

    /** Crea el PDF en certificados_generados/ y devuelve su ruta. Nunca sobrescribe uno anterior. */
    private static String crearPdf(Datos datos) throws Exception {
        String html = llenarPlantilla(datos, LocalDate.now(), null);
        Files.createDirectories(Paths.get(CARPETA_SALIDA));
        String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
        String ruta = CARPETA_SALIDA + "/certificado_" + datos.documento() + "_" + marca + ".pdf";
        try (OutputStream os = new FileOutputStream(ruta)) {
            escribirPdf(html, os);
        }
        return ruta;
    }

    /** HTML del certificado listo para convertir a PDF. firmaDataUri: imagen PNG de la firma (o null). */
    public static String llenarPlantilla(Datos d, LocalDate expedicion, String firmaDataUri) throws Exception {
        String html;
        try (InputStream plantilla = GenerarCertificado.class.getResourceAsStream("/plantilla_certificado.html")) {
            if (plantilla == null) {
                throw new Exception("No se encontró plantilla_certificado.html en src/main/resources");
            }
            html = new String(plantilla.readAllBytes(), StandardCharsets.UTF_8);
        }

        html = html.replace("{{PARRAFO_INICIAL}}", parrafoInicial(d))
                   .replace("{{DIA}}", String.valueOf(expedicion.getDayOfMonth()))
                   .replace("{{MES}}", expedicion.getMonth().getDisplayName(TextStyle.FULL, ES_CO))
                   .replace("{{ANIO_EXPEDICION}}", String.valueOf(expedicion.getYear()))
                   .replace("{{FIRMANTE_NOMBRE}}", esc(ConexionBD.obtener("certificado.firmante.nombre", "Jhon Jairo Andrés Aparicio Rueda")))
                   .replace("{{FIRMANTE_CARGO}}", esc(ConexionBD.obtener("certificado.firmante.cargo", "Coordinador de Nómina")))
                   .replace("{{FIRMA}}", firmaDataUri == null ? "" : "<img src=\"" + firmaDataUri + "\" alt=\"Firma\" />")
                   .replace("{{LOGO_UPB}}", imagen("/certificado/upb-logo-certificado.png"))
                   .replace("{{VIGILADA}}", imagen("/certificado/vigilada-mineducacion.png"));

        for (Map.Entry<String, Double> v : d.valores().entrySet()) {
            html = html.replace("{{" + v.getKey() + "}}", formatearValor(v.getValue()));
        }
        return html;
    }

    /** Convierte el HTML a PDF con las fuentes del certificado incrustadas. */
    public static void escribirPdf(String html, OutputStream destino) throws Exception {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFont(() -> recurso("/certificado/fuentes/LiberationSans-Regular.ttf"), "ArialCert",
            400, BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> recurso("/certificado/fuentes/LiberationSans-Bold.ttf"), "ArialCert",
            700, BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> recurso("/certificado/fuentes/LiberationSansNarrow-Regular.ttf"), "ArialNarrowCert",
            400, BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.withHtmlContent(html, null);
        builder.toStream(destino);
        builder.run();
    }

    /**
     * Revisa que el PDF quedó bien: se puede abrir, tiene una página y trae el nombre y el
     * documento del contratista. Devuelve null si está bien, o el problema encontrado.
     */
    public static String verificarPdf(String ruta, Datos datos) {
        File archivo = new File(ruta);
        if (!archivo.isFile() || archivo.length() == 0) {
            return "El archivo PDF no se creó.";
        }
        try (PDDocument pdf = PDDocument.load(archivo)) {
            if (pdf.getNumberOfPages() != 1) {
                return "El PDF tiene " + pdf.getNumberOfPages() + " páginas; el certificado debe tener una sola.";
            }
            // Se lee en el orden en que aparece en la hoja y sin espacios (el nombre puede partirse en dos renglones)
            PDFTextStripper lector = new PDFTextStripper();
            lector.setSortByPosition(true);
            String texto = lector.getText(pdf).replaceAll("[\\s\\u200b]+", "");
            if (!texto.contains(formatearDocumento(datos.documento()))) {
                return "El PDF no contiene el número de documento " + formatearDocumento(datos.documento()) + ".";
            }
            if (!texto.contains(datos.nombreCompleto().replaceAll("\\s+", ""))) {
                return "El PDF no contiene el nombre " + datos.nombreCompleto() + ".";
            }
            return null;
        } catch (Exception e) {
            return "El PDF no se puede abrir: " + e.getMessage();
        }
    }

    /** Huella (SHA-256) de los datos del certificado: si cambia algún dato, cambia la huella. */
    public static String huella(Datos d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.nombreCompleto()).append('|').append(d.tipoDocumento()).append('|')
          .append(d.documento()).append('|').append(d.anio());
        d.valores().forEach((k, v) -> sb.append('|').append(k).append('=').append(Math.round(v)));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Deja constancia del PDF en la tabla certificados (la necesita envio_certificado para el historial)
    private static int registrarCertificado(Connection conn, Datos datos, String rutaPdf, int idUsuario,
                                            Integer idAnterior) throws Exception {
        String sql = "INSERT INTO certificados " +
            "(id_contrato, id_usuario, id_estado, d_fecha_generacion, t_ruta_pdf, t_formato_certificado, t_huella_datos, " +
            " b_certificado_corregido, id_certificado_anterior, d_fecha_correccion) " +
            "VALUES (?, ?, ?, CURDATE(), ?, 'PDF', ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, datos.idContrato());
            ps.setInt(2, idUsuario);
            ps.setInt(3, idAnterior == null ? ESTADO_GENERADO : ESTADO_CORREGIDO);
            ps.setString(4, rutaPdf);
            ps.setString(5, huella(datos));
            ps.setBoolean(6, idAnterior != null);
            if (idAnterior == null) {
                ps.setNull(7, java.sql.Types.INTEGER);
                ps.setNull(8, java.sql.Types.TIMESTAMP);
            } else {
                ps.setInt(7, idAnterior);
                ps.setObject(8, LocalDateTime.now());
            }
            ps.executeUpdate();

            try (ResultSet claves = ps.getGeneratedKeys()) {
                claves.next();
                return claves.getInt(1);
            }
        }
    }

    // ---------- Formatos ----------

    /** $4.170.357; el cero se escribe "0" con dos espacios a la derecha, como en el formato oficial. */
    private static String formatearValor(double valor) {
        long redondeado = Math.round(valor);
        if (redondeado == 0) return "<span class=\"cero\">0</span>";
        return "$" + FORMATO.format(redondeado);
    }

    /** 1095794 -> 1.095.794 (si trae letras, como un pasaporte, se deja igual). */
    public static String formatearDocumento(String documento) {
        String limpio = documento.replace(".", "").replace(",", "").trim();
        if (!limpio.matches("\\d+")) return documento.trim();
        return FORMATO.format(Long.parseLong(limpio));
    }

    // ---------- Renglones estirados (como los saltos de línea justificados de Word) ----------

    private static final float TAMANO_LETRA = 10.674f;   // pt, igual que en la plantilla
    private static final float ANCHO_TEXTO = 423.06f;    // pt, de 89,7 a 512,76
    private static final float REDONDEO_LETRA = 0.016f;

    private record Trozo(String texto, boolean negrita) { }

    /**
     * "Que la Universidad reconoció a NOMBRE con cédula de ciudadanía DOCUMENTO por ingresos ...
     * durante el año dos mil veinticinco (2025), las siguientes sumas:" con los cortes del formato oficial.
     */
    private static String parrafoInicial(Datos d) throws Exception {
        String tipo = d.tipoDocumento() == null || d.tipoDocumento().isBlank()
            ? "cédula de ciudadanía" : d.tipoDocumento().trim().toLowerCase(ES_CO);
        String documento = formatearDocumento(d.documento());
        String anio = numeroEnLetras(d.anio()) + " (" + d.anio() + ")";

        // Renglón 2: en el formato oficial los tres primeros espacios llevan 1,27 pt más
        String r1 = renglonEstirado(List.of(new Trozo("Que la Universidad reconoció a", false),
            new Trozo(d.nombreCompleto(), true)), 0);
        String r2 = renglonEstirado(List.of(new Trozo("con " + tipo, false), new Trozo(documento, true),
            new Trozo("por ingresos laborales y de prestación de", false)), 1.27);

        if (r1 != null && r2 != null) {
            // Renglón 3 en adelante: justificado normal; los tres primeros espacios llevan 2,46 pt más
            return r1 + "\n        " + r2 + "\n        <p class=\"parrafo\">servicios<i class=\"e3\"></i> con" +
                "<i class=\"e3\"></i> esta<i class=\"e3\"></i> Seccional, durante el año " + anio + ", las siguientes sumas:</p>";
        }
        // Nombre muy largo: el párrafo completo justificado de corrido
        return "<p class=\"parrafo\">Que la Universidad reconoció a <b>" + esc(d.nombreCompleto()) + "</b> con " + esc(tipo) +
            " <b>" + esc(documento) + "</b> por ingresos laborales y de prestación de servicios con esta Seccional, " +
            "durante el año " + anio + ", las siguientes sumas:</p>";
    }

    private static PDType0Font fuenteNormal, fuenteNegrita;

    private static synchronized float ancho(String texto, boolean negrita) throws Exception {
        if (fuenteNormal == null) {
            PDDocument medidas = new PDDocument();   // solo para leer las medidas de las fuentes
            fuenteNormal = PDType0Font.load(medidas, recurso("/certificado/fuentes/LiberationSans-Regular.ttf"), false);
            fuenteNegrita = PDType0Font.load(medidas, recurso("/certificado/fuentes/LiberationSans-Bold.ttf"), false);
        }
        return (negrita ? fuenteNegrita : fuenteNormal).getStringWidth(texto) / 1000f * TAMANO_LETRA;
    }

    /**
     * Un renglón que ocupa exactamente el ancho del texto agrandando solo los espacios entre palabras.
     * extraTresPrimeros: espacio adicional en los tres primeros espacios (detalle del formato oficial).
     * Devuelve null si el texto no cabe en un renglón (un nombre muy largo).
     */
    private static String renglonEstirado(List<Trozo> trozos, double extraTresPrimeros) throws Exception {
        List<String> palabras = new ArrayList<>();
        List<Boolean> negritas = new ArrayList<>();
        for (Trozo t : trozos) {
            for (String p : t.texto().trim().split("\\s+")) {
                palabras.add(p);
                negritas.add(t.negrita());
            }
        }
        float natural = 0;
        for (int i = 0; i < palabras.size(); i++) {
            // el motor del PDF redondea cada letra hacia arriba (~0,016 pt por letra)
            natural += ancho(palabras.get(i), negritas.get(i)) + palabras.get(i).length() * REDONDEO_LETRA;
        }
        int espacios = palabras.size() - 1;
        natural += espacios * ancho(" ", false);
        float sobrante = ANCHO_TEXTO - natural;

        if (sobrante < 0 || espacios == 0) {
            return null;   // no cabe en un renglón
        }
        StringBuilder html = new StringBuilder();

        int conExtra = Math.min(3, espacios);
        double base = (sobrante - conExtra * extraTresPrimeros) / espacios;
        if (base < 0) { base = sobrante / (double) espacios; extraTresPrimeros = 0; }

        html.append("<div class=\"parrafo renglon\">");
        for (int i = 0; i < palabras.size(); i++) {
            if (i > 0) {
                double hueco = base + (i <= conExtra ? extraTresPrimeros : 0);
                html.append(String.format(Locale.ROOT, "<i style=\"padding-right:%.3fpt\"></i> ", hueco));
            }
            html.append(negritas.get(i) ? "<b>" + esc(palabras.get(i)) + "</b>" : esc(palabras.get(i)));
        }
        return html.append("</div>").toString();
    }

    private static final String[] UNIDADES = { "", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho",
        "nueve", "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho",
        "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis",
        "veintisiete", "veintiocho", "veintinueve" };
    private static final String[] DECENAS = { "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta",
        "ochenta", "noventa" };
    private static final String[] CENTENAS = { "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
        "seiscientos", "setecientos", "ochocientos", "novecientos" };

    /** 2025 -> "dos mil veinticinco" (de 0 a 999.999). */
    public static String numeroEnLetras(int n) {
        if (n == 0) return "cero";
        if (n < 0 || n > 999_999) return String.valueOf(n);
        int miles = n / 1000, resto = n % 1000;
        String texto = miles == 0 ? "" : (miles == 1 ? "mil" : menorDeMil(miles).replaceAll("uno$", "un") + " mil");
        if (resto > 0) texto = (texto.isEmpty() ? "" : texto + " ") + menorDeMil(resto);
        return texto;
    }

    private static String menorDeMil(int n) {
        if (n == 100) return "cien";
        int c = n / 100, r = n % 100;
        String texto = CENTENAS[c];
        if (r > 0) {
            String dec = r < 30 ? UNIDADES[r] : DECENAS[r / 10] + (r % 10 == 0 ? "" : " y " + UNIDADES[r % 10]);
            texto = texto.isEmpty() ? dec : texto + " " + dec;
        }
        return texto;
    }

    private static String esc(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- Recursos (imágenes y fuentes van dentro del proyecto, funcionan igual en el .jar) ----------

    private static final Map<String, String> IMAGENES = new java.util.concurrent.ConcurrentHashMap<>();

    private static String imagen(String ruta) throws Exception {
        String uri = IMAGENES.get(ruta);
        if (uri == null) {
            try (InputStream in = recurso(ruta)) {
                uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
            }
            IMAGENES.put(ruta, uri);
        }
        return uri;
    }

    private static InputStream recurso(String ruta) {
        InputStream in = GenerarCertificado.class.getResourceAsStream(ruta);
        if (in == null) {
            throw new IllegalStateException("No se encontró " + ruta + " en src/main/resources");
        }
        return in;
    }

    private static int obtenerUltimoIdContratista(Connection conn) throws Exception {
        String sql = "SELECT MAX(id_contratista) AS ultimo FROM contratistas";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt("ultimo");
        }
    }
}
