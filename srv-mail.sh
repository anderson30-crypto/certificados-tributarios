#!/usr/bin/env bash
###############################################################################
# srv-mail  -  POSTFIX + DOVECOT  -  instalacion automatica  -  FLORIDABLANCA
# IPv4 172.23.1.5/24   IPv6 2801:23:1::5/64   VLAN 10 (DMZ)
# Dominio: neuroimp.com
# Ubuntu Server 24.04 - adaptador en PUENTE
#
# Uso:   sudo bash srv-mail-floridablanca.sh
#
# Deja dos buzones de prueba: jperez y mlopez, clave Floridablanca2026
###############################################################################

set -euo pipefail

#-----------------------------------------------------------------------------
# Variables
#-----------------------------------------------------------------------------
HOSTNAME_SRV="srv-mail"
DOMINIO="neuroimp.com"
IPV4="172.23.1.5"
IPV6="2801:23:1::5"
GW4="172.23.1.1"
GW6="2801:23:1::1"
DNS4="172.23.1.3"

USUARIOS=(jperez mlopez)
PASS_USUARIOS="Floridablanca2026"

VERDE='\e[32m'; ROJO='\e[31m'; AMARILLO='\e[33m'; NC='\e[0m'
paso()  { echo -e "\n${VERDE}==> $*${NC}"; }
aviso() { echo -e "${AMARILLO}[!] $*${NC}"; }
error() { echo -e "${ROJO}[X] $*${NC}"; exit 1; }

#-----------------------------------------------------------------------------
# Comprobaciones previas
#-----------------------------------------------------------------------------
[[ $EUID -eq 0 ]] || error "Ejecuta el script con sudo:  sudo bash $0"

IFACE=$(ip route show default 2>/dev/null | awk '{print $5; exit}')
[[ -n "${IFACE:-}" ]] || IFACE=$(ls /sys/class/net | grep -v '^lo$' | head -1)
[[ -n "${IFACE:-}" ]] || error "No se encontro ninguna interfaz de red"
echo "Interfaz detectada: $IFACE"

paso "Comprobando acceso a internet (necesario para apt)"
if ! getent hosts archive.ubuntu.com >/dev/null 2>&1 || \
   ! timeout 15 bash -c 'exec 3<>/dev/tcp/archive.ubuntu.com/80' 2>/dev/null; then
    error "Sin internet. Deja la VM con DHCP (como viene por defecto) y vuelve a ejecutar."
fi

#=============================================================================
# 1 - Hostname, hosts y zona horaria
#=============================================================================
paso "1/7 Hostname, /etc/hosts y zona horaria"

cat > /etc/hosts <<EOF
127.0.0.1       localhost
::1             localhost ip6-localhost ip6-loopback
${IPV4}    email.${DOMINIO}    ${HOSTNAME_SRV}
EOF

hostnamectl set-hostname "$HOSTNAME_SRV"
timedatectl set-timezone America/Bogota

#=============================================================================
# 2 - Instalar Postfix sin asistente
#=============================================================================
paso "2/7 Instalando Postfix y Dovecot"

export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a
APT_OPTS=(-o DPkg::Lock::Timeout=600 -o Acquire::ForceIPv4=true -o Acquire::Retries=3)

# El espejo de pais (co.archive.ubuntu.com) viene fallando, asi que pasamos al
# repositorio general ANTES de la primera descarga, no como plan B.
usar_espejo_general() {
    local f cambios=0
    for f in /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list; do
        [[ -f $f ]] || continue
        [[ -f "${f}.bak" ]] || cp "$f" "${f}.bak"
        if grep -qE 'https?://[a-z]{2}\.archive\.ubuntu\.com' "$f"; then
            sed -i -E 's#https?://[a-z]{2}\.archive\.ubuntu\.com#http://archive.ubuntu.com#g' "$f"
            cambios=1
        fi
    done
    if (( cambios )); then
        aviso "Espejo de pais reemplazado por archive.ubuntu.com (copia en *.bak)"
    else
        echo "El espejo ya era archive.ubuntu.com, no se toca"
    fi
}

intentar() {
    local n
    for n in {1..10}; do
        "$@" && return 0
        aviso "apt ocupado o fallo de red, reintento $n/10 en 30 s..."
        [[ $n -eq 2 ]] && { apt-get "${APT_OPTS[@]}" update || true; }
        sleep 30
    done
    error "No se pudo ejecutar: $*"
}

# Respuestas del asistente de Postfix, para que no pregunte nada
debconf-set-selections <<EOF
postfix postfix/main_mailer_type string Internet Site
postfix postfix/mailname string ${DOMINIO}
EOF

usar_espejo_general
intentar apt-get "${APT_OPTS[@]}" update
intentar apt-get "${APT_OPTS[@]}" -y install postfix mailutils dovecot-imapd dovecot-pop3d

command -v postconf >/dev/null || error "Postfix no quedo instalado; no se aplica netplan"

#=============================================================================
# 3 - Configurar Postfix
#=============================================================================
paso "3/7 Configurando Postfix"

postconf -e "myhostname = email.${DOMINIO}"
postconf -e "mydomain = ${DOMINIO}"
postconf -e "myorigin = \$mydomain"
postconf -e "mydestination = \$myhostname, ${DOMINIO}, localhost.\$mydomain, localhost"
postconf -e "inet_interfaces = all"
postconf -e "inet_protocols = all"
postconf -e "mynetworks = 127.0.0.0/8 [::1]/128 172.23.0.0/16 [2801:23::]/32"
postconf -e "home_mailbox = Maildir/"
postconf -e "smtpd_banner = \$myhostname ESMTP RASBI Floridablanca"
postconf -e "smtpd_recipient_restrictions = permit_mynetworks, reject_unauth_destination"

echo "--- parametros aplicados ---"
postconf -n | grep -E "myhostname|mydomain|mydestination|mynetworks|home_mailbox"

#=============================================================================
# 4 - Configurar Dovecot
#=============================================================================
paso "4/7 Configurando Dovecot"

# La sintaxis cambio por completo en Dovecot 2.4: "mail_location" ya no existe
# (se partio en mail_driver + mail_path) y "disable_plaintext_auth" paso a
# llamarse "auth_allow_cleartext". Detectamos la version y escribimos la que toca.
DV=$(dovecot --version 2>/dev/null | awk '{print $1}')
DV_MAJ=${DV%%.*}
DV_RESTO=${DV#*.}
DV_MIN=${DV_RESTO%%.*}
echo "Dovecot detectado: ${DV}"

escribir_conf_23() {
cat > /etc/dovecot/conf.d/99-rasbi.conf <<'EOF'
# Configuracion RASBI Floridablanca - sintaxis Dovecot 2.3
mail_location = maildir:~/Maildir
disable_plaintext_auth = no
auth_mechanisms = plain login
protocols = imap pop3
EOF
}

escribir_conf_24() {
cat > /etc/dovecot/conf.d/99-rasbi.conf <<'EOF'
# Configuracion RASBI Floridablanca - sintaxis Dovecot 2.4
mail_driver = maildir
mail_path = ~/Maildir
auth_allow_cleartext = yes
auth_mechanisms = plain login
protocols {
  imap = yes
  pop3 = yes
}
EOF
}

escribir_conf_24_simple() {
cat > /etc/dovecot/conf.d/99-rasbi.conf <<'EOF'
# Configuracion RASBI Floridablanca - sintaxis Dovecot 2.4 (sin bloque protocols)
mail_driver = maildir
mail_path = ~/Maildir
auth_allow_cleartext = yes
auth_mechanisms = plain login
EOF
}

if (( DV_MAJ > 2 || ( DV_MAJ == 2 && DV_MIN >= 4 ) )); then
    escribir_conf_24
    if ! doveconf -n > /dev/null 2>&1; then
        aviso "El bloque 'protocols' no fue aceptado, probando sin el"
        escribir_conf_24_simple
    fi
else
    escribir_conf_23
fi

# Si sigue fallando, mostrar el error completo en vez de ocultarlo
if ! doveconf -n > /dev/null 2>&1; then
    echo "--- salida de doveconf ---"
    doveconf -n || true
    error "Error de sintaxis en Dovecot (ver arriba)"
fi
echo "Configuracion de Dovecot valida"

#=============================================================================
# 5 - Buzones de prueba
#=============================================================================
paso "5/7 Creando buzones"

for u in "${USUARIOS[@]}"; do
    if ! id "$u" >/dev/null 2>&1; then
        adduser --disabled-password --gecos "" "$u"
    fi
    echo "${u}:${PASS_USUARIOS}" | chpasswd
    mkdir -p "/home/${u}/Maildir"
    chown -R "${u}:${u}" "/home/${u}/Maildir"
    echo "  buzon ${u}@${DOMINIO} listo"
done

systemctl enable postfix dovecot
systemctl restart postfix dovecot
systemctl is-active --quiet postfix || error "Postfix no arranco: journalctl -u postfix"
systemctl is-active --quiet dovecot || error "Dovecot no arranco: journalctl -u dovecot"

#=============================================================================
# 6 - Prueba de envio local
#=============================================================================
paso "6/7 Prueba de envio"

echo "Correo de prueba generado por el script de instalacion." \
    | mail -s "Prueba RASBI Floridablanca" "${USUARIOS[0]}@${DOMINIO}" || aviso "El envio fallo"
sleep 4
if ls "/home/${USUARIOS[0]}/Maildir/new/" >/dev/null 2>&1 && \
   [[ -n "$(ls -A "/home/${USUARIOS[0]}/Maildir/new/" 2>/dev/null)" ]]; then
    echo "Correo entregado en /home/${USUARIOS[0]}/Maildir/new/"
else
    aviso "El buzon esta vacio. Revisa: tail -30 /var/log/mail.log"
fi

ss -tlnp | grep -E ':25|:110|:143' || aviso "No se ven los puertos escuchando"

#=============================================================================
# 7 - Firewall
#=============================================================================
ufw allow 22/tcp
ufw allow 25/tcp
ufw allow 110/tcp
ufw allow 143/tcp
ufw --force enable

#=============================================================================
# 8 - Red fija (AL FINAL)
#=============================================================================
paso "7/7 Aplicando IP fija (netplan)"

mkdir -p /root/netplan-backup
find /etc/netplan -maxdepth 1 -name '*.yaml' ! -name '01-netcfg.yaml' \
     -exec mv {} /root/netplan-backup/ \;

mkdir -p /etc/cloud/cloud.cfg.d
echo "network: {config: disabled}" > /etc/cloud/cloud.cfg.d/99-disable-network-config.cfg

cat > /etc/netplan/01-netcfg.yaml <<EOF
network:
  version: 2
  renderer: networkd
  ethernets:
    ${IFACE}:
      dhcp4: no
      dhcp6: no
      accept-ra: no
      addresses:
        - ${IPV4}/24
        - ${IPV6}/64
      routes:
        - to: default
          via: ${GW4}
        - to: "::/0"
          via: ${GW6}
      nameservers:
        search: [${DOMINIO}]
        addresses: [${DNS4}]
EOF
chmod 600 /etc/netplan/01-netcfg.yaml

aviso "Si estas conectado por SSH a la IP DHCP, la sesion se va a cortar aqui."
aviso "Reconecta a ${IPV4}."
netplan apply
sleep 3

systemctl restart postfix dovecot

#=============================================================================
# Pruebas finales
#=============================================================================
paso "Pruebas"

ip -br addr
ping -c2 -W2 "$GW4" || aviso "No responde el gateway ${GW4}"
ss -tln | grep -E ':25|:110|:143'

echo -e "\n${VERDE}Listo. srv-mail (Floridablanca) configurado en ${IPV4} / ${IPV6}${NC}"
cat <<EOF

 Clientes de correo (Thunderbird, Outlook):
   IMAP  email.${DOMINIO}  puerto 143  sin cifrado, contrasena normal
   POP3  email.${DOMINIO}  puerto 110  sin cifrado
   SMTP  email.${DOMINIO}  puerto 25   sin cifrado, sin autenticacion

 Buzones:  ${USUARIOS[0]}@${DOMINIO}  y  ${USUARIOS[1]}@${DOMINIO}
 Clave:    ${PASS_USUARIOS}

 Sin cifrado a proposito, es laboratorio. El registro MX de la zona ya apunta
 a email.${DOMINIO}; si no resuelve, revisa que srv-dns este arriba.
EOF
