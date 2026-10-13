-- =====================================================================
-- Reinicia los datos de la demo: deja la base de datos lista para
-- volver a hacer el cargue de la sábana, la generación y el envío.
--
-- BORRA: contratistas, contratos, certificados, solicitudes,
--        historial de certificados y envíos de correo.
-- CONSERVA: usuarios, roles, permisos, estados y auditorías.
--
-- En MySQL Workbench: abrir este archivo y ejecutar todo (rayo ⚡).
-- =====================================================================

USE certificados_tributarios;

-- Workbench trae activo "Safe Updates", que no deja borrar tablas completas
SET SQL_SAFE_UPDATES = 0;

-- 1) Borrar en orden: primero las tablas que dependen de otras (llaves foráneas)
DELETE FROM envio_certificado;       -- depende de certificados
DELETE FROM historial_certificados;  -- depende de certificados
DELETE FROM certificados;            -- depende de contratos y solicitudes
DELETE FROM solicitud_certificados;  -- depende de contratistas
DELETE FROM contratos;               -- depende de contratistas
DELETE FROM contratistas;

-- 2) Reiniciar los consecutivos (los id vuelven a empezar en 1)
ALTER TABLE envio_certificado      AUTO_INCREMENT = 1;
ALTER TABLE historial_certificados AUTO_INCREMENT = 1;
ALTER TABLE certificados           AUTO_INCREMENT = 1;
ALTER TABLE solicitud_certificados AUTO_INCREMENT = 1;
ALTER TABLE contratos              AUTO_INCREMENT = 1;
ALTER TABLE contratistas           AUTO_INCREMENT = 1;

-- 3) Ajuste necesario para la sábana: "permiso por protección temporal" tiene 31 caracteres
ALTER TABLE contratistas MODIFY t_tipo_documento VARCHAR(60) NOT NULL;

SET SQL_SAFE_UPDATES = 1;

-- 4) Verificación: las 6 primeras deben quedar en 0
SELECT 'contratistas' AS tabla, COUNT(*) AS registros FROM contratistas
UNION ALL SELECT 'contratos', COUNT(*) FROM contratos
UNION ALL SELECT 'certificados', COUNT(*) FROM certificados
UNION ALL SELECT 'solicitud_certificados', COUNT(*) FROM solicitud_certificados
UNION ALL SELECT 'historial_certificados', COUNT(*) FROM historial_certificados
UNION ALL SELECT 'envio_certificado', COUNT(*) FROM envio_certificado
UNION ALL SELECT 'usuarios (debe ser >= 1)', COUNT(*) FROM usuarios
UNION ALL SELECT 'estados (debe ser 5)', COUNT(*) FROM estados;

-- 5) El sistema registra los certificados a nombre del usuario con id 1.
--    Esta consulta debe devolver una fila; si no, crear ese usuario antes de la demo.
SELECT id_usuario, t_nombre_usuario FROM usuarios WHERE id_usuario = 1;
