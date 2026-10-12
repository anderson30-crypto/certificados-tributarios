package Seguridad;

import conexion.ConexionBD;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/**
 * Crea el primer administrador, o le asigna una contraseña nueva a uno existente.
 * Se ejecuta a mano una sola vez (Run en VS Code, o java -cp ... Seguridad.CrearAdministrador).
 * No existe ninguna contraseña por defecto: la escribe quien instala el sistema.
 */
public class CrearAdministrador {

    private static final int LONGITUD_MINIMA = 10;

    public static void main(String[] args) {

        try {
            System.out.println("=== Crear administrador del Sistema de Certificados Tributarios ===");
            System.out.println("Conectando a " + ConexionBD.describirConexion());

            String username = leer("Nombre de usuario para iniciar sesión: ");
            if (username.isBlank()) {
                System.out.println("Cancelado: el nombre de usuario no puede estar vacío.");
                return;
            }

            try (Connection conn = ConexionBD.obtenerConexion()) {

                int idRol = idRolAdministrador(conn);
                Integer idExistente = idUsuario(conn, username);

                String nombre;
                String correo;

                if (idExistente != null) {
                    String respuesta = leer("El usuario '" + username + "' ya existe. ¿Asignarle una contraseña nueva " +
                        "y el rol Administrador? (s/n): ");
                    if (!respuesta.equalsIgnoreCase("s")) {
                        System.out.println("Cancelado, no se cambió nada.");
                        return;
                    }
                    nombre = null;
                    correo = null;
                } else {
                    nombre = leer("Nombre completo: ");
                    correo = leer("Correo: ");
                    if (nombre.isBlank() || correo.isBlank()) {
                        System.out.println("Cancelado: el nombre y el correo son obligatorios.");
                        return;
                    }
                }

                char[] password = leerPassword("Contraseña (mínimo " + LONGITUD_MINIMA + " caracteres): ");
                char[] confirmacion = leerPassword("Repita la contraseña: ");

                if (password.length < LONGITUD_MINIMA) {
                    System.out.println("Cancelado: la contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
                    return;
                }
                if (!Arrays.equals(password, confirmacion)) {
                    System.out.println("Cancelado: las contraseñas no coinciden.");
                    return;
                }
                // La consola de Windows puede cambiar tildes y ñ, y entonces la contraseña guardada
                // no coincidiría con la que se escribe en el navegador
                for (char c : password) {
                    if (c < 33 || c > 126) {
                        System.out.println("Cancelado: use solo letras sin tildes ni ñ, números y símbolos " +
                            "(sin espacios) en la contraseña.");
                        return;
                    }
                }

                String hash = Autenticacion.hash(new String(password));

                if (idExistente != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE usuarios SET t_password_hash = ?, id_rol = ?, b_estado = 1 WHERE id_usuario = ?")) {
                        ps.setString(1, hash);
                        ps.setInt(2, idRol);
                        ps.setInt(3, idExistente);
                        ps.executeUpdate();
                    }
                    System.out.println("Listo: '" + username + "' tiene contraseña nueva y rol Administrador.");
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO usuarios (id_rol, t_nombre_usuario, t_correo, t_username, t_password_hash, b_estado) " +
                            "VALUES (?, ?, ?, ?, ?, 1)")) {
                        ps.setInt(1, idRol);
                        ps.setString(2, nombre);
                        ps.setString(3, correo);
                        ps.setString(4, username);
                        ps.setString(5, hash);
                        ps.executeUpdate();
                    }
                    System.out.println("Listo: administrador '" + username + "' creado.");
                }

                // Comprobación final: el mismo inicio de sesión que usa la página
                boolean puedeEntrar = Autenticacion.validar(username, new String(password)) != null;
                Arrays.fill(password, ' ');
                Arrays.fill(confirmacion, ' ');
                System.out.println(puedeEntrar
                    ? "Comprobado: '" + username + "' puede iniciar sesión en http://localhost:7000"
                    : "ATENCIÓN: el usuario se guardó pero la comprobación de inicio de sesión falló (ver mensaje arriba).");
            }

        } catch (Exception e) {
            System.out.println("No se pudo crear el administrador: " + e.getMessage());
        }
    }

    private static int idRolAdministrador(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id_rol FROM roles WHERE t_rol = 'Administrador'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO roles (t_rol, b_estado_rol) VALUES ('Administrador', 1)", Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Integer idUsuario(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id_usuario FROM usuarios WHERE t_username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    // --- Lectura por consola. En una terminal real la contraseña no se ve al escribirla ---

    private static BufferedReader entrada;

    private static String leer(String mensaje) throws IOException {
        Console consola = System.console();
        if (consola != null) {
            String linea = consola.readLine(mensaje);
            return linea == null ? "" : linea.trim();
        }
        System.out.print(mensaje);
        System.out.flush();
        if (entrada == null) {
            entrada = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        String linea = entrada.readLine();
        return linea == null ? "" : linea.trim();
    }

    private static char[] leerPassword(String mensaje) throws IOException {
        Console consola = System.console();
        if (consola != null) {
            char[] password = consola.readPassword(mensaje);
            return password == null ? new char[0] : password;
        }
        System.out.println("(Aviso: esta consola no puede ocultar lo que escribe)");
        System.out.print(mensaje);
        System.out.flush();
        if (entrada == null) {
            entrada = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        String linea = entrada.readLine();
        return linea == null ? new char[0] : linea.toCharArray();
    }
}
