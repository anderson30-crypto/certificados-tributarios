package servicio;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import conexion.ConexionBD;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CargueMasivo {

    private static final DataFormatter formatter = new DataFormatter();

    // Año gravable que se certifica: el año anterior al actual
    private static final int ANIO_CERTIFICADO = LocalDate.now().getYear() - 1;

    // Para probar sin el WebServer: carga la sábana de ejemplo de resources
    public static void main(String[] args) {
        String rutaArchivo = "src/main/resources/SABANA_CERTIFICADO_MINI.xlsx";

        try (FileInputStream fis = new FileInputStream(rutaArchivo)) {
            System.out.println(cargar(fis));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Lee la sábana (xlsx) y guarda contratistas y contratos en la base de datos.
     * Si el contratista ya existe (mismo documento) se actualiza en vez de duplicarse,
     * así la misma sábana se puede volver a subir sin errores.
     */
    public static Map<String, Object> cargar(InputStream excel) throws Exception {

        int filasInsertadas = 0;
        int filasActualizadas = 0;
        List<String> errores = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(excel);
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
