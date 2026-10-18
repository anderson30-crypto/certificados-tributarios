package Seguridad;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Los cuatro roles del sistema, qué puede hacer cada uno y a qué pantallas entra.
 *
 *  - admin  (Administrador)   : solo administra usuarios y contraseñas, y consulta auditorías
 *  - nomina (Nómina)          : carga la sábana, genera y envía certificados, ve el dashboard
 *  - jefe   (Jefe de Nómina)  : firma los certificados y revisa el historial de Nómina
 *  - gh     (Gestión Humana)  : lo mismo que el jefe; firma cuando el jefe no está
 */
public final class Roles {

    public static final String ADMIN = "admin";
    public static final String NOMINA = "nomina";
    public static final String JEFE = "jefe";
    public static final String GH = "gh";

    /** Nombre del rol tal como se guarda en la tabla roles. */
    public static final Map<String, String> NOMBRES = Map.of(
        ADMIN, "Administrador",
        NOMINA, "Nómina",
        JEFE, "Jefe de Nómina",
        GH, "Gestión Humana"
    );

    /** Funciones de cada rol (se muestran en el perfil y en la administración de usuarios). */
    public static final Map<String, List<String>> FUNCIONES = Map.of(
        ADMIN, List.of("Gestionar usuarios", "Cambiar contraseñas", "Ver auditorías"),
        NOMINA, List.of("Cargar sábana", "Generar certificados", "Enviar certificados", "Ver dashboard"),
        JEFE, List.of("Firmar certificados", "Gestionar firma", "Ver historial de Nómina"),
        GH, List.of("Firmar certificados", "Gestionar firma", "Ver historial de Nómina")
    );

    /** Pantalla de inicio de cada rol. */
    public static final Map<String, String> INICIO = Map.of(
        ADMIN, "/admin-inicio.html",
        NOMINA, "/inicio.html",
        JEFE, "/jefe-inicio.html",
        GH, "/jefe-inicio.html"
    );

    private static final Set<String> FIRMANTES = Set.of(JEFE, GH);

    // Pantallas de Nómina (las demás .html sin prefijo de rol también se consideran de Nómina)
    private static final Set<String> PAGINAS_NOMINA = Set.of(
        "/inicio.html", "/subir-documentos.html", "/generar-certificado.html", "/certificado-generado.html",
        "/ver-certificado.html", "/enviar-correo.html", "/enviar-correo-formulario.html", "/borrador.html",
        "/historial.html", "/solicitudes.html", "/certificados-devueltos.html", "/notificaciones-error.html",
        "/perfil.html", "/dashboard.html"
    );

    private Roles() { }

    /** Clave del rol (admin, nomina, jefe, gh) a partir del nombre guardado en la tabla roles. */
    public static String clave(String nombreRol) {
        String n = sinTildes(nombreRol == null ? "" : nombreRol).toLowerCase().trim();
        if (n.startsWith("admin")) return ADMIN;
        if (n.startsWith("jefe")) return JEFE;
        if (n.startsWith("gestion")) return GH;
        return NOMINA; // "Nómina", "Operador de Nómina" o el antiguo "Usuario"
    }

    public static boolean esFirmante(String clave) {
        return FIRMANTES.contains(clave);
    }

    /**
     * true si el rol puede usar esa ruta (página .html o /api/...).
     * Las rutas públicas (login, imágenes, css, js) se deciden antes, en el WebServer.
     */
    public static boolean puede(String clave, String ruta, String metodo) {

        // Cualquier usuario con sesión: su sesión, su perfil y su contraseña
        if (ruta.equals("/api/sesion") || ruta.equals("/api/logout") || ruta.startsWith("/api/perfil")
                || ruta.equals("/cambiar-contrasena.html") || ruta.equals("/api/auditoria/evento")) {
            return true;
        }

        if (ruta.startsWith("/api/")) {
            if (ruta.startsWith("/api/usuarios") || ruta.startsWith("/api/auditorias")) {
                return clave.equals(ADMIN);
            }
            // Firma de certificados e historial de Nómina: Jefe de Nómina y Gestión Humana
            if (ruta.startsWith("/api/firma/") || ruta.equals("/api/historial-nomina")) {
                return esFirmante(clave);
            }
            // Ver un PDF: Nómina y quienes firman
            if (ruta.startsWith("/api/certificados/pdf/")) {
                return clave.equals(NOMINA) || esFirmante(clave);
            }
            // Cargue, contratistas, generación y correo: solo Nómina
            return clave.equals(NOMINA);
        }

        if (ruta.endsWith(".html")) {
            if (ruta.startsWith("/admin-")) return clave.equals(ADMIN);
            if (ruta.startsWith("/jefe-") || ruta.equals("/firma-digital.html")) return esFirmante(clave);
            if (ruta.equals("/perfil-rol.html")) return !clave.equals(NOMINA);
            if (PAGINAS_NOMINA.contains(ruta)) return clave.equals(NOMINA);
            return clave.equals(NOMINA);
        }

        return true;
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
