package Seguridad;

import conexion.ConexionBD;
import org.mindrot.jbcrypt.BCrypt;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inicio de sesión: valida usuario y contraseña contra la tabla usuarios.
 * Las contraseñas se guardan solo como hash BCrypt (nunca en texto plano).
 */
public class Autenticacion {

    /** Lo que queda guardado en la sesión del navegador tras iniciar sesión. */
    public record UsuarioSesion(int idUsuario, int idRol, String rol, String nombre, String username)
        implements Serializable { }

    // Costo de BCrypt: 12 tarda ~0,25 s por intento, suficiente para frenar ataques de fuerza bruta
    private static final int COSTO_BCRYPT = 12;

    // Bloqueo por intentos fallidos: 5 errores seguidos bloquean ese usuario 5 minutos
    private static final int MAX_INTENTOS = 5;
    private static final long BLOQUEO_MS = 5 * 60 * 1000;
    private static final Map<String, int[]> intentos = new ConcurrentHashMap<>();
    private static final Map<String, Long> bloqueadosHasta = new ConcurrentHashMap<>();

    // Se compara contra este hash cuando el usuario no existe, para que la respuesta tarde
    // lo mismo y no revele qué nombres de usuario existen
    private static final String HASH_FALSO = BCrypt.hashpw("usuario-inexistente", BCrypt.gensalt(COSTO_BCRYPT));

    public static class UsuarioBloqueadoException extends Exception {
        public UsuarioBloqueadoException(long minutos) {
            super("Demasiados intentos fallidos. Intente de nuevo en " + minutos + " minuto(s).");
        }
    }

    public static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(COSTO_BCRYPT));
    }

    /**
     * Devuelve el usuario si el nombre y la contraseña son correctos y el usuario está activo;
     * null en cualquier otro caso (sin decir cuál dato falló).
     */
    public static UsuarioSesion validar(String username, String password)
            throws SQLException, UsuarioBloqueadoException {

        if (username == null || password == null) {
            return null;
        }
        String clave = username.trim().toLowerCase();

        Long hasta = bloqueadosHasta.get(clave);
        if (hasta != null && hasta > System.currentTimeMillis()) {
            registrarMotivo(username, "usuario bloqueado por intentos fallidos (se desbloquea solo o reiniciando el WebServer)");
            throw new UsuarioBloqueadoException((hasta - System.currentTimeMillis()) / 60000 + 1);
        }

        String sql = "SELECT u.id_usuario, u.id_rol, u.t_nombre_usuario, u.t_username, u.t_password_hash, " +
            "u.b_estado, r.t_rol FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol WHERE u.t_username = ?";

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                boolean existe = rs.next();
                String hashGuardado = existe ? rs.getString("t_password_hash") : null;
                boolean activo = existe && rs.getBoolean("b_estado");

                // Solo se aceptan hashes BCrypt; una contraseña vieja en texto plano no sirve para entrar
                boolean esBcrypt = hashGuardado != null && hashGuardado.startsWith("$2");
                boolean coincide = BCrypt.checkpw(password, esBcrypt ? hashGuardado : HASH_FALSO) && esBcrypt;

                if (!coincide || !activo) {
                    // El motivo exacto solo va a la consola del servidor; la pantalla muestra un mensaje genérico
                    registrarMotivo(username, !existe ? "el usuario no existe en la tabla usuarios"
                        : !esBcrypt ? "la contraseña guardada no está cifrada con BCrypt; asígnele una nueva con Seguridad.CrearAdministrador"
                        : !activo ? "el usuario está desactivado (b_estado = 0)"
                        : "la contraseña no coincide");
                    registrarFallo(clave);
                    return null;
                }

                intentos.remove(clave);
                bloqueadosHasta.remove(clave);

                return new UsuarioSesion(
                    rs.getInt("id_usuario"),
                    rs.getInt("id_rol"),
                    rs.getString("t_rol"),
                    rs.getString("t_nombre_usuario"),
                    rs.getString("t_username")
                );
            }
        }
    }

    private static void registrarMotivo(String username, String motivo) {
        System.out.println("Inicio de sesión rechazado para '" + username + "': " + motivo);
    }

    private static void registrarFallo(String clave) {
        int[] contador = intentos.computeIfAbsent(clave, k -> new int[1]);
        synchronized (contador) {
            contador[0]++;
            if (contador[0] >= MAX_INTENTOS) {
                bloqueadosHasta.put(clave, System.currentTimeMillis() + BLOQUEO_MS);
                contador[0] = 0;
            }
        }
    }
}
