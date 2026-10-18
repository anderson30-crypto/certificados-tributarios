package servicio;

import org.apache.poi.ss.usermodel.*;
import com.github.pjfanning.xlsx.StreamingReader;

import conexion.ConexionBD;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CargueMasivo {

    private static final DataFormatter formatter = new DataFormatter();

    // Año gravable que se certifica: el año anterior al actual
    private static final int ANIO_CERTIFICADO = LocalDate.now().getYear() - 1;

    // Para probar sin el WebServer: carga la sábana de ejemplo de resources
    public static void main(String[] args) {
        try {
            System.out.println(cargar(Path.of("src/main/resources/SABANA_CERTIFICADO_MINI.xlsx")));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Carga una sábana (xlsx) en dos pasadas:
     *  1. Validación: si el mismo documento aparece dos veces, o el mismo correo es de dos
     *     contratistas distintos (en el archivo o contra lo que ya está en la base de datos),
     *     la sábana se rechaza completa y no se guarda nada ("rechazada" = true + la lista de errores).
     *  2. Cargue: guarda contratistas y contratos. Si el contratista ya existe (mismo documento)
     *     se actualiza en vez de duplicarse, así la misma sábana se puede volver a subir.
     */
    public static Map<String, Object> cargar(Path archivo) throws Exception {
        List<String> duplicados;
        try (InputStream excel = Files.newInputStream(archivo)) {
            duplicados = buscarDuplicados(excel);
        }
        if (!duplicados.isEmpty()) {
            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("rechazada", true);
            resultado.put("insertados", 0);
            resultado.put("actualizados", 0);
            resultado.put("errores", duplicados);
            return resultado;
        }
        try (InputStream excel = Files.newInputStream(archivo)) {
            Map<String, Object> resultado = guardar(excel);
            resultado.put("rechazada", false);
            return resultado;
        }
    }

    /** Pasada 1: documentos y correos repetidos. Devuelve un error por cada repetición (vacía = sin duplicados). */
    static List<String> buscarDuplicados(InputStream excel) throws Exception {
        List<String> errores = new ArrayList<>();
        Map<String, Integer> filaPorDocumento = new HashMap<>();
        Map<String, String[]> duenoCorreo = new HashMap<>();   // correo -> {documento, fila}

        // Correos que ya están en la base de datos, con el documento de su dueño
        Map<String, String> correosBD = new HashMap<>();
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT t_correo_contratista, n_numero_documento FROM contratistas WHERE t_correo_contratista IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                correosBD.put(normalizarCorreo(rs.getString(1)), normalizarDocumento(rs.getString(2)));
            }
        }

        try (Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(excel)) {
            for (Row fila : workbook.getSheetAt(0)) {
                if (fila.getRowNum() == 0) continue;
                int numeroFila = fila.getRowNum() + 1;
                String documento = normalizarDocumento(obtenerTexto(fila.getCell(4)));
                String correo = normalizarCorreo(obtenerTexto(fila.getCell(5)));
                if (documento.isEmpty()) continue;   // la pasada 2 lo informa como fila sin documento

                Integer filaAnterior = filaPorDocumento.putIfAbsent(documento, numeroFila);
                if (filaAnterior != null) {
                    errores.add("Fila " + numeroFila + ": el documento " + documento + " está repetido (ya aparece en la fila " + filaAnterior + ")");
                }

                if (!correo.isEmpty()) {
                    String[] dueno = duenoCorreo.get(correo);
                    if (dueno == null) {
                        duenoCorreo.put(correo, new String[] { documento, String.valueOf(numeroFila) });
                    } else if (!dueno[0].equals(documento)) {
                        errores.add("Fila " + numeroFila + ": el correo " + correo + " ya es del documento " + dueno[0] +
                            " (fila " + dueno[1] + ")");
                    }
                    String duenoBD = correosBD.get(correo);
                    if (duenoBD != null && !duenoBD.equals(documento)) {
                        errores.add("Fila " + numeroFila + ": el correo " + correo + " ya está registrado para el documento " +
                            duenoBD + " en el sistema");
                    }
                }
            }
        }
        return errores;
    }

    private static String normalizarDocumento(String documento) {
        return documento == null ? "" : documento.replace(".", "").replace(",", "").replace(" ", "").trim();
    }

    private static String normalizarCorreo(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    /** Pasada 2: guarda cada fila en su propia transacción. */
    private static Map<String, Object> guardar(InputStream excel) throws Exception {

        int filasInsertadas = 0;
        int filasActualizadas = 0;
        List<String> errores = new ArrayList<>();

        // Lectura por partes (streaming): solo unas 100 filas en memoria a la vez.
        // Con XSSFWorkbook el archivo completo iba a memoria (una sábana de 50.000 filas usaba ~1,8 GB).
        try (Workbook workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(excel);
             Connection conn = ConexionBD.obtenerConexion()) {

            Sheet hoja = workbook.getSheetAt(0);

            for (Row fila : hoja) {
                if (fila.getRowNum() == 0) continue; // saltar encabezado

                String primerApellido = obtenerTexto(fila.getCell(0));
                String numeroDocumento = obtenerTexto(fila.getCell(4));

                // Si toda la fila está vacía, se ignora sin contar como error
                if ((primerApellido == null || primerApellido.isBlank()) &&
                    (numeroDocumento == null || numeroDocumento.isBlank())) {
                    continue;
                }

                int numeroFila = fila.getRowNum() + 1; // como se ve en Excel

                if (numeroDocumento == null || numeroDocumento.isBlank()) {
                    errores.add("Fila " + numeroFila + ": sin número de documento");
                    continue;
                }

                // Cada fila en su propia transacción: o se guarda completa o no se guarda
                conn.setAutoCommit(false);

                try {
                    boolean existia = guardarFila(conn, fila, numeroDocumento);
                    conn.commit();

                    if (existia) {
                        filasActualizadas++;
                    } else {
                        filasInsertadas++;
                    }

                } catch (Exception e) {
                    conn.rollback();
                    errores.add("Fila " + numeroFila + ": " + e.getMessage());
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("insertados", filasInsertadas);
        resultado.put("actualizados", filasActualizadas);
        resultado.put("errores", errores);
        return resultado;
    }

    // Devuelve true si el contratista ya existía
    private static boolean guardarFila(Connection conn, Row fila, String numeroDocumento) throws SQLException {

        String primerApellido = obtenerTexto(fila.getCell(0));
        String segundoApellido = obtenerTexto(fila.getCell(1));
        String nombres = obtenerTexto(fila.getCell(2));
        String tipoDocumento = obtenerTexto(fila.getCell(3));
        String correo = obtenerTexto(fila.getCell(5));

        // 1) Contratista: actualizar si existe, si no insertar
        Integer idContratista = buscarId(conn,
            "SELECT id_contratista FROM contratistas WHERE n_numero_documento = ?", numeroDocumento);
        boolean existia = idContratista != null;

        if (existia) {
            String sql = "UPDATE contratistas SET t_primer_apellido = ?, t_segundo_apellido = ?, t_nombres = ?, " +
                "t_tipo_documento = ?, t_correo_contratista = ? WHERE id_contratista = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, primerApellido);
                ps.setString(2, segundoApellido);
                ps.setString(3, nombres);
                ps.setString(4, tipoDocumento);
                ps.setString(5, correo);
                ps.setInt(6, idContratista);
                ps.executeUpdate();
            }

        } else {
            String sql = "INSERT INTO contratistas " +
                "(t_primer_apellido, t_segundo_apellido, t_nombres, t_tipo_documento, n_numero_documento, t_correo_contratista) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, primerApellido);
                ps.setString(2, segundoApellido);
                ps.setString(3, nombres);
                ps.setString(4, tipoDocumento);
                ps.setString(5, numeroDocumento);
                ps.setString(6, correo);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    idContratista = rs.getInt(1);
                }
            }
        }

        // 2) Contrato (datos financieros) del año: actualizar si existe, si no insertar
        double[] valores = new double[13];
        for (int i = 0; i < valores.length; i++) {
            valores[i] = obtenerNumero(fila.getCell(6 + i)); // columnas G..S
        }

        Integer idContrato = buscarId(conn,
            "SELECT id_contrato FROM contratos WHERE id_contratista = ? AND n_anio = ?",
            idContratista, ANIO_CERTIFICADO);

        String columnas = "n_salario = ?, n_honorarios = ?, n_servicios = ?, n_comisiones = ?, n_prestaciones = ?, " +
            "n_pago_viaticos = ?, n_gastos = ?, n_otros_ingresos = ?, n_cesantias_empleado = ?, " +
            "n_cesantias_fondo = ?, n_pensiones = ?, n_total_ing_brutos = ?, n_ret_ica = ?";

        String sql = (idContrato != null)
            ? "UPDATE contratos SET " + columnas + " WHERE id_contratista = ? AND n_anio = ?"
            : "INSERT INTO contratos SET " + columnas + ", id_contratista = ?, n_anio = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) {
                ps.setDouble(i + 1, valores[i]);
            }
            ps.setInt(14, idContratista);
            ps.setInt(15, ANIO_CERTIFICADO);
            ps.executeUpdate();
        }

        return existia;
    }

    private static Integer buscarId(Connection conn, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static String obtenerTexto(Cell celda) {
        if (celda == null) return null;
        return formatter.formatCellValue(celda).trim();
    }

    private static double obtenerNumero(Cell celda) {
        if (celda == null) return 0.0;
        try {
            return celda.getNumericCellValue();
        } catch (Exception e) {
            String texto = formatter.formatCellValue(celda).replace(",", "").trim();
            return texto.isBlank() ? 0.0 : Double.parseDouble(texto);
        }
    }
}
