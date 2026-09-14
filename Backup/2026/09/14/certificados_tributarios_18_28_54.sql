-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: localhost    Database: certificados_tributarios
-- ------------------------------------------------------
-- Server version	8.0.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `auditorias`
--

DROP TABLE IF EXISTS `auditorias`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auditorias` (
  `id_log` int NOT NULL AUTO_INCREMENT,
  `id_usuario` int DEFAULT NULL,
  `t_accion` varchar(100) DEFAULT NULL,
  `d_fecha_registro` datetime DEFAULT CURRENT_TIMESTAMP,
  `t_tabla_afectada` varchar(50) DEFAULT NULL,
  `t_registro_id` int DEFAULT NULL,
  `t_datos_anteriores` text,
  `t_datos_nuevos` text,
  PRIMARY KEY (`id_log`),
  KEY `id_usuario` (`id_usuario`),
  CONSTRAINT `auditorias_ibfk_1` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auditorias`
--

LOCK TABLES `auditorias` WRITE;
/*!40000 ALTER TABLE `auditorias` DISABLE KEYS */;
INSERT INTO `auditorias` VALUES (1,1,'GENERACION CERTIFICADO','2026-09-14 15:58:38','certificados',2,NULL,'Certificado generado formato: PDF'),(2,1,'ACTUALIZACION CERTIFICADO','2026-09-14 16:42:30','certificados',2,'Estado anterior 2','Estado nuevo 2');
/*!40000 ALTER TABLE `auditorias` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `certificados`
--

DROP TABLE IF EXISTS `certificados`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `certificados` (
  `id_certificado` int NOT NULL AUTO_INCREMENT,
  `id_solicitud` int DEFAULT NULL,
  `id_contrato` int NOT NULL,
  `id_usuario` int NOT NULL,
  `id_estado` int NOT NULL,
  `d_fecha_generacion` date DEFAULT NULL,
  `t_ruta_pdf` varchar(255) DEFAULT NULL,
  `t_ruta_local` varchar(255) DEFAULT NULL,
  `t_formato_certificado` varchar(50) DEFAULT NULL,
  `b_descargar_certificado` tinyint(1) DEFAULT '0',
  `b_generar_correo` tinyint(1) DEFAULT '0',
  `t_correo_copia` varchar(255) DEFAULT NULL,
  `b_certificado_corregido` tinyint(1) DEFAULT '0',
  `id_certificado_anterior` int DEFAULT NULL,
  `d_fecha_correccion` datetime DEFAULT NULL,
  PRIMARY KEY (`id_certificado`),
  KEY `id_solicitud` (`id_solicitud`),
  KEY `id_contrato` (`id_contrato`),
  KEY `id_usuario` (`id_usuario`),
  KEY `id_estado` (`id_estado`),
  CONSTRAINT `certificados_ibfk_1` FOREIGN KEY (`id_solicitud`) REFERENCES `solicitud_certificados` (`id_solicitud`),
  CONSTRAINT `certificados_ibfk_2` FOREIGN KEY (`id_contrato`) REFERENCES `contratos` (`id_contrato`),
  CONSTRAINT `certificados_ibfk_3` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `certificados_ibfk_4` FOREIGN KEY (`id_estado`) REFERENCES `estados` (`id_estado`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `certificados`
--

LOCK TABLES `certificados` WRITE;
/*!40000 ALTER TABLE `certificados` DISABLE KEYS */;
INSERT INTO `certificados` VALUES (2,1,1,1,2,'2026-09-14','C:/Certificados/certificado_2.pdf','C:/Certificados/certificado_2.pdf','PDF',1,1,'contabilidad@empresa.com',0,NULL,NULL);
/*!40000 ALTER TABLE `certificados` ENABLE KEYS */;
UNLOCK TABLES;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_certificado_insert` AFTER INSERT ON `certificados` FOR EACH ROW BEGIN


INSERT INTO auditorias

(

id_usuario,

t_accion,

t_tabla_afectada,

t_registro_id,

t_datos_nuevos

)

VALUES

(

NEW.id_usuario,

'GENERACION CERTIFICADO',

'certificados',

NEW.id_certificado,

CONCAT(
'Certificado generado formato: ',
NEW.t_formato_certificado
)

);


END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_certificado_update` AFTER UPDATE ON `certificados` FOR EACH ROW BEGIN


INSERT INTO auditorias

(

id_usuario,

t_accion,

t_tabla_afectada,

t_registro_id,

t_datos_anteriores,

t_datos_nuevos

)

VALUES

(

NEW.id_usuario,

'ACTUALIZACION CERTIFICADO',

'certificados',

NEW.id_certificado,


CONCAT(
'Estado anterior ',
OLD.id_estado
),


CONCAT(
'Estado nuevo ',
NEW.id_estado
)

);


END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `contratistas`
--

DROP TABLE IF EXISTS `contratistas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contratistas` (
  `id_contratista` int NOT NULL AUTO_INCREMENT,
  `t_primer_apellido` varchar(50) NOT NULL,
  `t_segundo_apellido` varchar(50) DEFAULT NULL,
  `t_nombres` varchar(100) NOT NULL,
  `t_tipo_documento` varchar(30) NOT NULL,
  `n_numero_documento` varchar(30) NOT NULL,
  `t_correo_contratista` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id_contratista`),
  UNIQUE KEY `n_numero_documento` (`n_numero_documento`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contratistas`
--

LOCK TABLES `contratistas` WRITE;
/*!40000 ALTER TABLE `contratistas` DISABLE KEYS */;
INSERT INTO `contratistas` VALUES (1,'Rodriguez','Perez','Juan Carlos','CC','1020304050','juan.rodriguez@gmail.com');
/*!40000 ALTER TABLE `contratistas` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contratos`
--

DROP TABLE IF EXISTS `contratos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contratos` (
  `id_contrato` int NOT NULL AUTO_INCREMENT,
  `id_contratista` int NOT NULL,
  `n_anio` int NOT NULL,
  `n_salario` decimal(15,2) DEFAULT '0.00',
  `n_honorarios` decimal(15,2) DEFAULT '0.00',
  `n_servicios` decimal(15,2) DEFAULT '0.00',
  `n_comisiones` decimal(15,2) DEFAULT '0.00',
  `n_prestaciones` decimal(15,2) DEFAULT '0.00',
  `n_pago_viaticos` decimal(15,2) DEFAULT '0.00',
  `n_gastos` decimal(15,2) DEFAULT '0.00',
  `n_otros_ingresos` decimal(15,2) DEFAULT '0.00',
  `n_cesantias_empleado` decimal(15,2) DEFAULT '0.00',
  `n_cesantias_fondo` decimal(15,2) DEFAULT '0.00',
  `n_pensiones` decimal(15,2) DEFAULT '0.00',
  `n_total_ing_brutos` decimal(15,2) DEFAULT '0.00',
  `n_ret_ica` decimal(15,2) DEFAULT '0.00',
  PRIMARY KEY (`id_contrato`),
  KEY `id_contratista` (`id_contratista`),
  CONSTRAINT `contratos_ibfk_1` FOREIGN KEY (`id_contratista`) REFERENCES `contratistas` (`id_contratista`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contratos`
--

LOCK TABLES `contratos` WRITE;
/*!40000 ALTER TABLE `contratos` DISABLE KEYS */;
INSERT INTO `contratos` VALUES (1,1,2026,3500000.00,42000000.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,0.00,42000000.00,500000.00);
/*!40000 ALTER TABLE `contratos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `envio_certificado`
--

DROP TABLE IF EXISTS `envio_certificado`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `envio_certificado` (
  `id_envio` int NOT NULL AUTO_INCREMENT,
  `id_certificado` int NOT NULL,
  `id_usuario` int NOT NULL,
  `t_correo_destino` varchar(100) DEFAULT NULL,
  `d_fecha_envio` date DEFAULT NULL,
  `b_descargado` tinyint(1) DEFAULT '0',
  `b_envio_correo` tinyint(1) DEFAULT '0',
  `id_estado` int NOT NULL,
  `t_mensaje_error` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_envio`),
  KEY `id_certificado` (`id_certificado`),
  KEY `id_usuario` (`id_usuario`),
  KEY `id_estado` (`id_estado`),
  CONSTRAINT `envio_certificado_ibfk_1` FOREIGN KEY (`id_certificado`) REFERENCES `certificados` (`id_certificado`),
  CONSTRAINT `envio_certificado_ibfk_2` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `envio_certificado_ibfk_3` FOREIGN KEY (`id_estado`) REFERENCES `estados` (`id_estado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `envio_certificado`
--

LOCK TABLES `envio_certificado` WRITE;
/*!40000 ALTER TABLE `envio_certificado` DISABLE KEYS */;
/*!40000 ALTER TABLE `envio_certificado` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `estados`
--

DROP TABLE IF EXISTS `estados`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `estados` (
  `id_estado` int NOT NULL AUTO_INCREMENT,
  `t_nombre_estado` varchar(50) NOT NULL,
  `t_descripcion` varchar(150) DEFAULT NULL,
  PRIMARY KEY (`id_estado`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `estados`
--

LOCK TABLES `estados` WRITE;
/*!40000 ALTER TABLE `estados` DISABLE KEYS */;
INSERT INTO `estados` VALUES (1,'Activo','Registro activo'),(2,'Generado','Certificado generado'),(3,'Enviado','Certificado enviado'),(4,'Corregido','Certificado corregido'),(5,'Error','Error en proceso');
/*!40000 ALTER TABLE `estados` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `historial_certificados`
--

DROP TABLE IF EXISTS `historial_certificados`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `historial_certificados` (
  `id_historial` int NOT NULL AUTO_INCREMENT,
  `id_certificado` int NOT NULL,
  `id_usuario` int NOT NULL,
  `id_estado_anterior` int DEFAULT NULL,
  `id_estado_nuevo` int DEFAULT NULL,
  `t_accion` varchar(100) DEFAULT NULL,
  `t_observacion` text,
  `d_fecha_historial` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id_historial`),
  KEY `id_certificado` (`id_certificado`),
  KEY `id_usuario` (`id_usuario`),
  KEY `id_estado_anterior` (`id_estado_anterior`),
  KEY `id_estado_nuevo` (`id_estado_nuevo`),
  CONSTRAINT `historial_certificados_ibfk_1` FOREIGN KEY (`id_certificado`) REFERENCES `certificados` (`id_certificado`),
  CONSTRAINT `historial_certificados_ibfk_2` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`),
  CONSTRAINT `historial_certificados_ibfk_3` FOREIGN KEY (`id_estado_anterior`) REFERENCES `estados` (`id_estado`),
  CONSTRAINT `historial_certificados_ibfk_4` FOREIGN KEY (`id_estado_nuevo`) REFERENCES `estados` (`id_estado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `historial_certificados`
--

LOCK TABLES `historial_certificados` WRITE;
/*!40000 ALTER TABLE `historial_certificados` DISABLE KEYS */;
/*!40000 ALTER TABLE `historial_certificados` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permisos`
--

DROP TABLE IF EXISTS `permisos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permisos` (
  `id_permiso` int NOT NULL AUTO_INCREMENT,
  `t_nombre_permiso` varchar(100) NOT NULL,
  `t_descripcion` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id_permiso`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `permisos`
--

LOCK TABLES `permisos` WRITE;
/*!40000 ALTER TABLE `permisos` DISABLE KEYS */;
/*!40000 ALTER TABLE `permisos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rol_permiso`
--

DROP TABLE IF EXISTS `rol_permiso`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rol_permiso` (
  `id_rol` int NOT NULL,
  `id_permiso` int NOT NULL,
  PRIMARY KEY (`id_rol`,`id_permiso`),
  KEY `id_permiso` (`id_permiso`),
  CONSTRAINT `rol_permiso_ibfk_1` FOREIGN KEY (`id_rol`) REFERENCES `roles` (`id_rol`),
  CONSTRAINT `rol_permiso_ibfk_2` FOREIGN KEY (`id_permiso`) REFERENCES `permisos` (`id_permiso`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rol_permiso`
--

LOCK TABLES `rol_permiso` WRITE;
/*!40000 ALTER TABLE `rol_permiso` DISABLE KEYS */;
/*!40000 ALTER TABLE `rol_permiso` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id_rol` int NOT NULL AUTO_INCREMENT,
  `t_rol` varchar(50) NOT NULL,
  `b_estado_rol` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id_rol`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (1,'Administrador',1),(2,'Usuario',1);
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `solicitud_certificados`
--

DROP TABLE IF EXISTS `solicitud_certificados`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `solicitud_certificados` (
  `id_solicitud` int NOT NULL AUTO_INCREMENT,
  `id_contratista` int NOT NULL,
  `id_usuario` int NOT NULL,
  `d_fecha_solicitud` datetime DEFAULT CURRENT_TIMESTAMP,
  `t_tipo_solicitud` varchar(50) DEFAULT NULL,
  `t_observacion` text,
  PRIMARY KEY (`id_solicitud`),
  KEY `id_contratista` (`id_contratista`),
  KEY `id_usuario` (`id_usuario`),
  CONSTRAINT `solicitud_certificados_ibfk_1` FOREIGN KEY (`id_contratista`) REFERENCES `contratistas` (`id_contratista`),
  CONSTRAINT `solicitud_certificados_ibfk_2` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id_usuario`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `solicitud_certificados`
--

LOCK TABLES `solicitud_certificados` WRITE;
/*!40000 ALTER TABLE `solicitud_certificados` DISABLE KEYS */;
INSERT INTO `solicitud_certificados` VALUES (1,1,1,'2026-09-14 15:57:24','Certificado tributario','Solicitud de certificado correspondiente al año 2026');
/*!40000 ALTER TABLE `solicitud_certificados` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `usuarios`
--

DROP TABLE IF EXISTS `usuarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `usuarios` (
  `id_usuario` int NOT NULL AUTO_INCREMENT,
  `id_rol` int NOT NULL,
  `t_nombre_usuario` varchar(100) NOT NULL,
  `t_correo` varchar(100) NOT NULL,
  `t_username` varchar(50) NOT NULL,
  `t_password_hash` varchar(255) NOT NULL,
  `b_estado` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`id_usuario`),
  UNIQUE KEY `t_correo` (`t_correo`),
  UNIQUE KEY `t_username` (`t_username`),
  KEY `id_rol` (`id_rol`),
  CONSTRAINT `usuarios_ibfk_1` FOREIGN KEY (`id_rol`) REFERENCES `roles` (`id_rol`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `usuarios`
--

LOCK TABLES `usuarios` WRITE;
/*!40000 ALTER TABLE `usuarios` DISABLE KEYS */;
INSERT INTO `usuarios` VALUES (1,1,'Administrador Sistema','admin@certificados.com','admin','123456',1);
/*!40000 ALTER TABLE `usuarios` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-14 18:28:54
