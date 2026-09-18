package servicio;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CargueMasivo {

    private static final DataFormatter formatter = new DataFormatter();

    public static void main(String[] args) {
        String rutaArchivo = "src/main/resources/SABANA_CERTIFICADO_MINI.xlsx";

        try (FileInputStream fis = new FileInputStream(rutaArchivo);
             Workbook workbook = new XSSFWorkbook(fis);
             Connection conn = ConexionBD2.obtenerConexion()) {

            Sheet hoja = workbook.getSheetAt(0);
            int filasInsertadas = 0;
            int filasConError = 0;

            for (Row fila : hoja) {
                if (fila.getRowNum() == 0) continue; // saltar encabezado

                String primerApellido = obtenerTexto(fila.getCell(0));
                String numeroDocumento = obtenerTexto(fila.getCell(4));

                // Si toda la fila está vacía, se ignora sin contar como error
                if ((primerApellido == null || primerApellido.isBlank()) &&
                    (numeroDocumento == null || numeroDocumento.isBlank())) {
                    continue;
                }

                try {
                    String segundoApellido = obtenerTexto(fila.getCell(1));
                    String nombres = obtenerTexto(fila.getCell(2));
                    String tipoDocumento = obtenerTexto(fila.getCell(3));
                    String correo = obtenerTexto(fila.getCell(5));

                    if (numeroDocumento == null || numeroDocumento.isBlank()) {
                        System.out.println("Fila " + fila.getRowNum() + " sin número de documento, se omite.");
                        filasConError++;
                        continue;
                    }

                    // 1) Insertar contratista
                    String sqlContratista = "INSERT INTO contratistas " +
                        "(t_primer_apellido, t_segundo_apellido, t_nombres, t_tipo_documento, n_numero_documento, t_correo_contratista) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";

                    int idContratista;

                    try (PreparedStatement ps = conn.prepareStatement(sqlContratista, PreparedStatement.RETURN_GENERATED_KEYS)) {
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

                    // 2) Insertar contrato (datos financieros)
                    double salario = obtenerNumero(fila.getCell(6));
                    double honorarios = obtenerNumero(fila.getCell(7));
                    double servicios = obtenerNumero(fila.getCell(8));
                    double comisiones = obtenerNumero(fila.getCell(9));
                    double prestaciones = obtenerNumero(fila.getCell(10));
                    double viaticos = obtenerNumero(fila.getCell(11));
                    double gastosRepresentacion = obtenerNumero(fila.getCell(12));
                    double otrosIngresos = obtenerNumero(fila.getCell(13));
                    double cesantiasEmpleado = obtenerNumero(fila.getCell(14));
                    double cesantiasFondo = obtenerNumero(fila.getCell(15));
                    double pensiones = obtenerNumero(fila.getCell(16));
                    double totalIngresos = obtenerNumero(fila.getCell(17));
                    double retIca = obtenerNumero(fila.getCell(18));

                    String sqlContrato = "INSERT INTO contratos " +
                        "(id_contratista, n_anio, n_salario, n_honorarios, n_servicios, n_comisiones, n_prestaciones, " +
                        "n_pago_viaticos, n_gastos, n_otros_ingresos, n_cesantias_empleado, n_cesantias_fondo, n_pensiones, " +
                        "n_total_ing_brutos, n_ret_ica) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                    try (PreparedStatement ps = conn.prepareStatement(sqlContrato)) {
                        ps.setInt(1, idContratista);
                        ps.setInt(2, 2025); // año del certificado
                        ps.setDouble(3, salario);
                        ps.setDouble(4, honorarios);
                        ps.setDouble(5, servicios);
                        ps.setDouble(6, comisiones);
                        ps.setDouble(7, prestaciones);
                        ps.setDouble(8, viaticos);
                        ps.setDouble(9, gastosRepresentacion);
                        ps.setDouble(10, otrosIngresos);
                        ps.setDouble(11, cesantiasEmpleado);
                        ps.setDouble(12, cesantiasFondo);
                        ps.setDouble(13, pensiones);
                        ps.setDouble(14, totalIngresos);
                        ps.setDouble(15, retIca);
                        ps.executeUpdate();
                    }

                    filasInsertadas++;

                } catch (Exception e) {
                    System.out.println("Error en fila " + fila.getRowNum() + ": " + e.getMessage());
                    filasConError++;
                }
            }

            System.out.println("Cargue finalizado. Insertados: " + filasInsertadas + " | Con error: " + filasConError);

        } catch (Exception e) {
            e.printStackTrace();
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