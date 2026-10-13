#!/usr/bin/env bash
###############################################################################
# srv-dns  -  BIND9  -  instalacion automatica
# SEDE FLORIDABLANCA  -  dominio neuroimp.com
# IPv4 172.23.1.3/24   IPv6 2801:23:1::3/64   VLAN 10 (DMZ)
# Ubuntu Server 24.04
#
# Uso:   sudo bash srv-dns.sh
#
# Orden: primero se instala y configura todo con la red actual (DHCP, con
# internet) y la IP fija se aplica AL FINAL, cuando BIND ya esta funcionando.
###############################################################################

set -euo pipefail

#-----------------------------------------------------------------------------
# Variables  (si tu profesor te asigno otras IP, cambialas SOLO aqui)
#-----------------------------------------------------------------------------
HOSTNAME_SRV="srv-dns"
DOMINIO="neuroimp.com"

# "si" = un solo servidor Ubuntu aloja TODOS los servicios (DNS, web, correo,
#        DHCP, VoIP): se le ponen todas las IP en la misma tarjeta y se abren
#        todos los puertos del firewall.
# "no" = este servidor solo hace DNS (cada servicio esta en otra maquina).
SERVIDOR_UNICO="si"

IPV4="172.23.1.3"
IPV6="2801:23:1::3"
GW4="172.23.1.1"
GW6="2801:23:1::1"

IP_WEB="172.23.1.4";    IP6_WEB="2801:23:1::4"
IP_MAIL="172.23.1.5";   IP6_MAIL="2801:23:1::5"
IP_DHCP="172.23.1.10"
IP_VOIP="172.23.1.50";  IP6_VOIP="2801:23:1::50"

# DNS de otras sedes para resolver sus dominios (confirma estas IP con tus companeros)
DNS_BUCARAMANGA="172.17.1.4"       # vit.com
DNS_GIRON="172.19.1.4"             # nutriser.com

SERIAL="$(date +%Y%m%d)01"

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
${IPV4}     ${HOSTNAME_SRV}.${DOMINIO}    ${HOSTNAME_SRV}
EOF

hostnamectl set-hostname "$HOSTNAME_SRV"
timedatectl set-timezone America/Bogota

#=============================================================================
# 2 - Instalar BIND9 (todavia con la red DHCP)
#=============================================================================
paso "2/7 Instalando BIND9"

export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a
APT_OPTS=(-o DPkg::Lock::Timeout=600 -o Acquire::ForceIPv4=true -o Acquire::Retries=3)

cambiar_espejo() {
    local f
    for f in /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list; do
        [[ -f $f ]] && sed -i -E 's#http://[a-z]{2}\.archive\.ubuntu\.com#http://archive.ubuntu.com#g' "$f"
    done
    aviso "Espejo cambiado a archive.ubuntu.com"
    apt-get "${APT_OPTS[@]}" update || true
}

intentar() {
    local n
    for n in {1..10}; do
        "$@" && return 0
        aviso "apt ocupado o fallo de red, reintento $n/10 en 30 s..."
        [[ $n -eq 2 ]] && cambiar_espejo
        sleep 30
    done
    error "No se pudo ejecutar: $*"
}

intentar apt-get "${APT_OPTS[@]}" update
intentar apt-get "${APT_OPTS[@]}" -y install bind9 bind9utils bind9-dnsutils

command -v named >/dev/null || error "BIND9 no quedo instalado; no se aplica netplan"

#=============================================================================
# 3 - Opciones globales
#=============================================================================
paso "3/7 Configurando BIND9"

# redes-internas: pueden consultar Y usar recursion (internet).
# otras-sedes:    solo pueden consultar nuestra zona (neuroimp.com).
cat > /etc/bind/named.conf.options <<EOF
acl redes-internas {
    127.0.0.0/8;
    ::1;
    172.23.0.0/16;
    172.16.0.0/16;
    2801:23::/32;
};

acl otras-sedes {
    172.17.0.0/16;      // Bucaramanga  vit.com
    172.19.0.0/16;      // Giron        nutriser.com
    172.22.0.0/16;      // Piedecuesta  fortkorp.com
    172.30.0.0/16;      // Barrancabermeja  mye.com
    2801:AAAA::/32;
    2801:BBA::/32;
    2801:F::/32;
    2801:BA::/32;
};

options {
    directory "/var/cache/bind";

    recursion yes;
    allow-query     { redes-internas; otras-sedes; };
    allow-recursion { redes-internas; };

    forwarders { 8.8.8.8; 8.8.4.4; };
    forward only;

    dnssec-validation no;

    listen-on    { 127.0.0.1; ${IPV4}; };
    listen-on-v6 { ::1; ${IPV6}; };
};
EOF

#=============================================================================
# 4 - Zonas
#=============================================================================
cat > /etc/bind/named.conf.local <<EOF
zone "${DOMINIO}" {
    type master;
    file "/etc/bind/db.${DOMINIO}";
};

zone "1.23.172.in-addr.arpa" {
    type master;
    file "/etc/bind/db.172.23.1";
};

// Dominios de otras sedes (se reenvian a su DNS)
zone "vit.com" {
    type forward;
    forward only;
    forwarders { ${DNS_BUCARAMANGA}; };
};

zone "nutriser.com" {
    type forward;
    forward only;
    forwarders { ${DNS_GIRON}; };
};
EOF

# Zona directa
cat > /etc/bind/db.${DOMINIO} <<EOF
\$TTL    604800
@   IN  SOA dns.${DOMINIO}. admin.${DOMINIO}. (
            ${SERIAL}  ; Serial - subir en cada cambio
            604800      ; Refresh
            86400       ; Retry
            2419200     ; Expire
            604800 )    ; Negative TTL
;
@       IN  NS      dns.${DOMINIO}.
@       IN  MX  10  email.${DOMINIO}.
;
dns     IN  A       ${IPV4}
dns     IN  AAAA    ${IPV6}
web     IN  A       ${IP_WEB}
web     IN  AAAA    ${IP6_WEB}
email   IN  A       ${IP_MAIL}
email   IN  AAAA    ${IP6_MAIL}
dhcp    IN  A       ${IP_DHCP}
voip    IN  A       ${IP_VOIP}
voip    IN  AAAA    ${IP6_VOIP}
;
www      IN  CNAME   web.${DOMINIO}.
ftp      IN  CNAME   web.${DOMINIO}.
intranet IN  CNAME   web.${DOMINIO}.
mail     IN  CNAME   email.${DOMINIO}.
EOF

# Zona inversa
cat > /etc/bind/db.172.23.1 <<EOF
\$TTL    604800
@   IN  SOA dns.${DOMINIO}. admin.${DOMINIO}. (
            ${SERIAL} 604800 86400 2419200 604800 )
;
@       IN  NS      dns.${DOMINIO}.
;
3       IN  PTR     dns.${DOMINIO}.
4       IN  PTR     web.${DOMINIO}.
5       IN  PTR     email.${DOMINIO}.
10      IN  PTR     dhcp.${DOMINIO}.
50      IN  PTR     voip.${DOMINIO}.
EOF

#=============================================================================
# 5 - Comprobar sintaxis y arrancar BIND
#=============================================================================
paso "4/7 Comprobando sintaxis y arrancando BIND"

named-checkconf                                             || error "Error en named.conf"
named-checkzone "$DOMINIO" /etc/bind/db.${DOMINIO}          || error "Error en zona directa"
named-checkzone 1.23.172.in-addr.arpa /etc/bind/db.172.23.1 || error "Error en zona inversa"

systemctl enable named
systemctl restart named
systemctl is-active --quiet named || error "named no arranco. Revisa: journalctl -u named"

echo "Prueba local: web.${DOMINIO} -> $(dig @127.0.0.1 web.${DOMINIO} +short)"

#=============================================================================
# 6 - Firewall
#=============================================================================
paso "5/7 Firewall"

ufw allow 22/tcp
ufw allow 53/tcp
ufw allow 53/udp
if [[ "$SERVIDOR_UNICO" == "si" ]]; then
    aviso "Servidor unico: se abren tambien los puertos de web, FTPS, correo, DHCP y VoIP"
    ufw allow 80/tcp
    ufw allow 443/tcp
    ufw allow 21/tcp
    ufw allow 40000:40100/tcp
    ufw allow 25/tcp
    ufw allow 110/tcp
    ufw allow 143/tcp
    ufw allow 993/tcp
    ufw allow 995/tcp
    ufw allow 67/udp
    ufw allow 5060/tcp
    ufw allow 5060/udp
    ufw allow 10000:20000/udp
fi
ufw --force enable

#=============================================================================
# 7 - Red fija (AL FINAL)
#=============================================================================
paso "6/7 Aplicando IP fija (netplan)"

# Lista de direcciones: siempre la del DNS; si es servidor unico, tambien las demas
DIRECCIONES="        - ${IPV4}/24
        - ${IPV6}/64"
if [[ "$SERVIDOR_UNICO" == "si" ]]; then
    for ip in "$IP_WEB" "$IP_MAIL" "$IP_DHCP" "$IP_VOIP"; do
        DIRECCIONES+=$'\n'"        - ${ip}/24"
    done
    for ip6 in "$IP6_WEB" "$IP6_MAIL" "$IP6_VOIP"; do
        DIRECCIONES+=$'\n'"        - ${ip6}/64"
    done
fi

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
${DIRECCIONES}
      routes:
        - to: default
          via: ${GW4}
        - to: "::/0"
          via: ${GW6}
      nameservers:
        search: [${DOMINIO}]
        addresses: [${IPV4}]
EOF
chmod 600 /etc/netplan/01-netcfg.yaml

aviso "Si estas conectado por SSH a la IP DHCP, la sesion se va a cortar aqui."
aviso "Reconecta a ${IPV4}."
netplan apply
sleep 3

systemctl restart named

#=============================================================================
# 8 - Pruebas finales
#=============================================================================
paso "7/7 Pruebas"

ip -br addr
ping -c2 -W2 "$GW4" || aviso "No responde el gateway ${GW4} (normal si el router aun no esta montado)"

echo "dns   A    : $(dig @${IPV4} dns.${DOMINIO} +short)"
echo "web   A    : $(dig @${IPV4} web.${DOMINIO} +short)"
echo "web   AAAA : $(dig @${IPV4} web.${DOMINIO} AAAA +short)"
echo "voip  A    : $(dig @${IPV4} voip.${DOMINIO} +short)"
echo "www   CNAME: $(dig @${IPV4} www.${DOMINIO} CNAME +short)"
echo "PTR .4     : $(dig @${IPV4} -x ${IP_WEB} +short)"
echo "MX         : $(dig @${IPV4} ${DOMINIO} MX +short)"
echo "google     : $(dig @${IPV4} google.com +short | head -1)"

echo -e "\n${VERDE}Listo. srv-dns configurado en ${IPV4} / ${IPV6}${NC}"
echo "Si 'google' sale vacio es que el laboratorio no tiene salida a internet:"
echo "no afecta a la resolucion interna de ${DOMINIO}."
