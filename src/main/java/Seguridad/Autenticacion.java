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
        implements Serializable {

        /** admin, nomina, jefe o gh (ver Roles). */
        public String claveRol() {
            return Roles.clave(rol);
        }
    }

    // Costo de BCrypt: 12 tarda ~0,25 s por intento, suficiente para frenar ataques de fuerza bruta
    private static final int COSTO_BCRYPT = 12;

    // Bloqueo por intentos fallidos: 3 contraseñas malas seguidas bloquean el usuario 15 minutos.
    // El Administrador NO se bloquea (así nunca queda el sistema sin quien desbloquee a los demás);
    // sus intentos fallidos igual quedan en la auditoría y BCrypt hace lento cada intento.
    // (se cambian con seguridad.login.intentos y seguridad.login.bloqueo_minutos en config.properties).
    // El contador y el bloqueo quedan en la tabla usuarios, así que reiniciar el WebServer no los borra;
    // el Administrador puede desbloquear antes de tiempo desde Usuarios.
    public static final int MAX_INTENTOS = entero("seguridad.login.intentos", 3);
    public static final int BLOQUEO_MINUTOS = entero("seguridad.login.bloqueo_minutos", 15);

    // Nombres de usuario que no existen: se cuentan igual (en memoria) para que la respuesta sea la
    // misma que con un usuario real y no se pueda averiguar cuáles existen
    private static final Map<String, int[]> intentosInexistentes = new ConcurrentHashMap<>();
    private static final Map<String, Long> inexistentesBloqueadosHasta = new ConcurrentHashMap<>();

    // Se compara contra este hash cuando el usuario no existe, para que la respuesta tarde
    // lo mismo y no revele qué nombres de usuario existen
    private static final String HASH_FALSO = BCrypt.hashpw("usuario-inexistente", BCrypt.gensalt(COSTO_BCRYPT));

    public static class UsuarioBloqueadoException extends Exception {
        public UsuarioBloqueadoException(long minutos) {
            super("Usuario bloqueado por " + MAX_INTENTOS + " intentos fallidos. Intente de nuevo en " + minutos +
                " minuto(s) o pida al Administrador que lo desbloquee.");
        }
    }

    /** Contraseña incorrecta (o usuario inexistente o inactivo): cuántos intentos le quedan antes del bloqueo. */
    public static class CredencialesInvalidasException extends Exception {
        private final int restantes;
        public CredencialesInvalidasException(int restantes) {
            super("Usuario o contraseña incorrectos." + (restantes < 0 ? "" : restantes == 1 ? " Le queda 1 intento antes de que se bloquee el usuario."
                : " Le quedan " + restantes + " intentos antes de que se bloquee el usuario."));
            this.restantes = restantes;
        }
        public int restantes() { return restantes; }
    }

    public static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(COSTO_BCRYPT));
    }

    /**
     * Devuelve el usuario si el nombre y la contraseña son correctos y el usuario está activo.
     * Si no, lanza CredencialesInvalidasException (sin decir cuál dato falló) o, al llegar a
     * MAX_INTENTOS fallos seguidos, UsuarioBloqueadoException.
     */
    public static UsuarioSesion validar(String username, String password)
            throws SQLException, UsuarioBloqueadoException, CredencialesInvalidasException {

        if (username == null || username.isBlank() || password == null) {
            throw new CredencialesInvalidasException(MAX_INTENTOS);
        }
        String clave = username.trim().toLowerCase();

        String sql = "SELECT u.id_usuario, u.id_rol, u.t_nombre_usuario, u.t_username, u.t_password_hash, " +
            "u.b_estado, r.t_rol, " +
            "CASE WHEN u.d_bloqueado_hasta > NOW() THEN TIMESTAMPDIFF(SECOND, NOW(), u.d_bloqueado_hasta) ELSE 0 END AS segundos_bloqueo " +
            "FROM usuarios u JOIN roles r ON r.id_rol = u.id_rol WHERE u.t_username = ?";

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                boolean existe = rs.next();
                Integer idUsuario = existe ? rs.getInt("id_usuario") : null;

                // Bloqueado: no se revisa la contraseña (ni siquiera la correcta) hasta que pase el tiempo
                boolean esAdministrador = existe && Roles.ADMIN.equals(Roles.clave(rs.getString("t_rol")));
                long segundos = esAdministrador ? 0 : existe ? rs.getLong("segundos_bloqueo") : segundosBloqueoInexistente(clave);
                if (segundos > 0) {
                    registrarMotivo(idUsuario, username, "usuario bloqueado por intentos fallidos");
                    throw new UsuarioBloqueadoException((segundos + 59) / 60);
                }

                String hashGuardado = existe ? rs.getString("t_password_hash") : null;
                boolean activo = existe && rs.getBoolean("b_estado");

                // Solo se aceptan hashes BCrypt; una contraseña vieja en texto plano no sirve para entrar
                boolean esBcrypt = hashGuardado != null && hashGuardado.startsWith("$2");
                boolean coincide = BCrypt.checkpw(password, esBcrypt ? hashGuardado : HASH_FALSO) && esBcrypt;

                if (!coincide || !activo) {
                    // El motivo exacto solo va a la consola del servidor; la pantalla muestra un mensaje genérico
                    registrarMotivo(idUsuario, username, !existe ? "el usuario no existe en la tabla usuarios"
                        : !esBcrypt ? "la contraseña guardada no está cifrada con BCrypt; asígnele una nueva con Seguridad.CrearAdministrador"
                        : !activo ? "el usuario está desactivado (b_estado = 0)"
                        : "la contraseña no coincide");
                    if (esAdministrador) throw new CredencialesInvalidasException(-1);   // sin contador ni bloqueo
                    int restantes = existe ? registrarFallo(conn, idUsuario, username) : registrarFalloInexistente(clave, username);
                    if (restantes == 0) throw new UsuarioBloqueadoException(BLOQUEO_MINUTOS);
                    throw new CredencialesInvalidasException(restantes);
                }

                UsuarioSesion sesion = new UsuarioSesion(
                    rs.getInt("id_usuario"),
                    rs.getInt("id_rol"),
                    rs.getString("t_rol"),
                    rs.getString("t_nombre_usuario"),
                    rs.getString("t_username")
                );
                reiniciarIntentos(conn, idUsuario);
                return sesion;
            }
        }
    }

    /** Quita el bloqueo y el contador de intentos (lo usa el Administrador y al asignar contraseña nueva). */
    public static void desbloquear(int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            reiniciarIntentos(conn, idUsuario);
        }
    }

    private static void reiniciarIntentos(Connection conn, int idUsuario) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE usuarios SET n_intentos_fallidos = 0, d_bloqueado_hasta = NULL WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
    }

    /** true si el usuario existe y sigue activo (se revisa en cada petición de una sesión abierta). */
    public static boolean sigueActivo(int idUsuario) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement("SELECT b_estado FROM usuarios WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    // El motivo exacto va a la consola del servidor y a la auditoría; la pantalla muestra un mensaje genérico
    private static void registrarMotivo(Integer idUsuario, String username, String motivo) {
        System.out.println("Inicio de sesión rechazado para '" + username + "': " + motivo);
        RegistroAuditoria.registrar(idUsuario, "Autenticación", "Intento de sesión fallido",
            "Usuario '" + username + "': " + motivo);
    }

    // Suma un fallo en la base de datos; al llegar a MAX_INTENTOS bloquea el usuario. Devuelve los intentos que quedan.
    private static int registrarFallo(Connection conn, int idUsuario, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE usuarios SET n_intentos_fallidos = n_intentos_fallidos + 1 WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
        int fallos;
        try (PreparedStatement ps = conn.prepareStatement("SELECT n_intentos_fallidos FROM usuarios WHERE id_usuario = ?")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                fallos = rs.getInt(1);
            }
        }
        if (fallos < MAX_INTENTOS) {
            return MAX_INTENTOS - fallos;
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET n_intentos_fallidos = 0, " +
                "d_bloqueado_hasta = DATE_ADD(NOW(), INTERVAL ? MINUTE) WHERE id_usuario = ?")) {
            ps.setInt(1, BLOQUEO_MINUTOS);
            ps.setInt(2, idUsuario);
            ps.executeUpdate();
        }
        System.out.println("Usuario '" + username + "' bloqueado " + BLOQUEO_MINUTOS + " minutos por " + MAX_INTENTOS + " intentos fallidos");
        RegistroAuditoria.registrar(idUsuario, "Autenticación", "Usuario bloqueado",
            MAX_INTENTOS + " contraseñas incorrectas seguidas · bloqueado " + BLOQUEO_MINUTOS + " minutos");
        return 0;
    }

    private static int registrarFalloInexistente(String clave, String username) {
        int[] contador = intentosInexistentes.computeIfAbsent(clave, k -> new int[1]);
        synchronized (contador) {
            contador[0]++;
            if (contador[0] < MAX_INTENTOS) {
                return MAX_INTENTOS - contador[0];
            }
            contador[0] = 0;
        }
        inexistentesBloqueadosHasta.put(clave, System.currentTimeMillis() + BLOQUEO_MINUTOS * 60_000L);
        RegistroAuditoria.registrar(null, "Autenticación", "Usuario bloqueado",
            "Usuario inexistente '" + username + "': " + MAX_INTENTOS + " intentos seguidos");
        return 0;
    }

    private static long segundosBloqueoInexistente(String clave) {
        Long hasta = inexistentesBloqueadosHasta.get(clave);
        if (hasta == null) return 0;
        long ms = hasta - System.currentTimeMillis();
        if (ms <= 0) {
            inexistentesBloqueadosHasta.remove(clave);
            return 0;
        }
        return (ms + 999) / 1000;
    }

    private static int entero(String clave, int porDefecto) {
        try {
            int valor = Integer.parseInt(ConexionBD.obtener(clave, String.valueOf(porDefecto)).trim());
            return valor > 0 ? valor : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
