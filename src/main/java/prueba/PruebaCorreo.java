package prueba;

import servicio.CorreoServicio;

/**
 * FASE 0: comprobar que las credenciales de correo funcionan antes de usar el resto del sistema.
 * Envía UN correo, sin adjunto, a la dirección de correo.redirigir_a de config.properties.
 */
public class PruebaCorreo {

    public static void main(String[] args) {

        try {
            CorreoServicio correo = new CorreoServicio();

            if (!correo.modoPrueba()) {
                System.out.println("Pon tu correo en correo.redirigir_a (config.properties) antes de probar.");
                return;
            }

            correo.enviar(
                "prueba@destinatario.com",
                null,
                "Prueba de envío - Certificados Tributarios",
                "Si recibes este correo, la configuración SMTP funciona correctamente.",
                null
            );

            System.out.println("Correo enviado. Revisa la bandeja de entrada (y la de spam) de tu correo.");

        } catch (Exception e) {
            System.out.println("NO se pudo enviar el correo: " + e);
            if (e.getCause() != null) {
                System.out.println("Causa: " + e.getCause());
            }
            System.out.println();
            System.out.println("Guía rápida:");
            System.out.println(" - 535 5.7.139: SMTP AUTH / autenticación básica deshabilitada -> pedir a TI que la habilite o usar OAuth2");
            System.out.println(" - 535 5.7.3: usuario o contraseña de aplicación incorrectos");
            System.out.println(" - Timeout / Could not connect: la red bloquea el puerto 587 (probar con otra red)");
            e.printStackTrace();
        }
    }
}
