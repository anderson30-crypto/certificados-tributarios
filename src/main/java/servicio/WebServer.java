package com.upb.integrador;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
        }).start(7000);

        // Endpoint: lista de contratistas desde la base de datos
        app.get("/api/contratistas", ctx -> {
            List<Map<String, Object>> lista = new ArrayList<>();

            try (Connection conn = ConexionBD.obtenerConexion();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT id_contratista, t_nombres, t_primer_apellido, n_numero_documento FROM contratistas");
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    Map<String, Object> c = new HashMap<>();
                    c.put("id", rs.getInt("id_contratista"));
                    c.put("nombre", rs.getString("t_nombres") + " " + rs.getString("t_primer_apellido"));
                    c.put("documento", rs.getString("n_numero_documento"));
                    lista.add(c);
                }

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("error", e.getMessage()));
                return;
            }

            ctx.json(lista);
        });

        // Endpoint: generar certificado para un contratista específico
        app.post("/api/certificados/{id}", ctx -> {
            int id = Integer.parseInt(ctx.pathParam("id"));

            try {
                String rutaPdf = GenerarCertificado.generarParaContratista(id);
                ctx.json(Map.of("exito", true, "archivo", rutaPdf));

            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).json(Map.of("exito", false, "error", e.getMessage()));
            }
        });

        System.out.println("Servidor corriendo en http://localhost:7000");
    }
}