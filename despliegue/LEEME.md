# Despliegue: https://certributario.bucaramanga.upb.edu.co

Servidor: web14 (Red Hat) · Java 17 · MySQL · certificado STAR de bucaramanga.upb.edu.co.
El aplicativo atiende **HTTPS en el puerto 443** con ese certificado; el puerto 80 solo redirige a https.

## Archivos

| Archivo | Para qué |
|---|---|
| `certificados-tributarios.jar` | El aplicativo completo (se genera con `./mvnw clean package`, queda en `target/`). Solo necesita Java 17. |
| `config.produccion.properties` | Configuración de producción: base de datos, correo, certificado y URL. Sin contraseñas reales. |
| `certributario.service` | Servicio systemd: arranca solo, se reinicia si se cae, usa el 443 sin ser root. |
| `certributario.conf` | Solo si el 443 del web14 ya lo usa Apache para otros sitios (ver al final). |

## 1. Usuario y carpetas

```bash
useradd --system --home /opt/certificados-tributarios certributario
mkdir -p /opt/certificados-tributarios /etc/certificados-tributarios
cp certificados-tributarios.jar /opt/certificados-tributarios/
chown -R certributario: /opt/certificados-tributarios
```

## 2. Certificado

El aplicativo necesita el certificado y su cadena en un solo archivo, y poder leer la llave:

```bash
cat /etc/pki/tls/certs/STAR_bucaramanga_upb_edu_co.crt \
    /etc/pki/tls/certs/STAR_bucaramanga_upb_edu_co.ca-bundle \
  > /etc/pki/tls/certs/STAR_bucaramanga_fullchain.crt

# Lectura de la llave solo para el usuario del aplicativo (no cambia los permisos para nadie más)
setfacl -m u:certributario:r /etc/pki/tls/private/web14.key
```

Cuando el certificado se renueve, repetir el `cat` y reiniciar el servicio.

## 3. Configuración

```bash
cp config.produccion.properties /etc/certificados-tributarios/config.properties
# Llenar las contraseñas nuevas (base de datos, correo, cifrado.clave)
chown certributario: /etc/certificados-tributarios/config.properties
chmod 600 /etc/certificados-tributarios/config.properties
```

Base de datos: crear `certificados_tributarios` en utf8mb4 y un usuario solo para el aplicativo con
`SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER` sobre esa base. Las tablas y columnas nuevas las crea
el aplicativo al arrancar.

## 4. Servicio y firewall

```bash
cp certributario.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now certributario
journalctl -u certributario -f
#   -> "Servidor corriendo en https://certributario.bucaramanga.upb.edu.co (https en el puerto 443, ...)"

firewall-cmd --permanent --add-service=https --add-service=http
firewall-cmd --reload
```

## 5. Primer administrador

```bash
systemctl stop certributario
sudo -u certributario env CT_CONFIG=/etc/certificados-tributarios/config.properties \
  java -cp /opt/certificados-tributarios/certificados-tributarios.jar Seguridad.CrearAdministrador
systemctl start certributario
```

## Actualizar a una versión nueva

```bash
systemctl stop certributario
cp certificados-tributarios.jar /opt/certificados-tributarios/
systemctl start certributario
```

## Respaldo

Base de datos `certificados_tributarios` y las carpetas `/opt/certificados-tributarios/certificados_generados`
(PDF firmados de todos los certificados) y `/opt/certificados-tributarios/subidas` (reportes cargados).

---

## Alternativa: si el 443 del web14 ya lo usa Apache

Un puerto solo lo puede usar un programa. Si Apache ya atiende el 443 para otros sitios:

1. En `/etc/certificados-tributarios/config.properties` dejar vacíos `ssl.certificado` y `ssl.llave`, y poner
   `servidor.host=127.0.0.1`, `servidor.puerto=7000`, `seguridad.cookie_segura=true`.
2. Copiar `certributario.conf` a `/etc/httpd/conf.d/` (ya trae las rutas del certificado STAR), ejecutar
   `setsebool -P httpd_can_network_connect 1`, `apachectl configtest` y `systemctl reload httpd`.
3. En `certributario.service` se puede quitar la línea `AmbientCapabilities`.
