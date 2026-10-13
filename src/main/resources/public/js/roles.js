/* =====================================================================
   Capa compartida de las vistas por rol (Jefe de Nómina / Administrador)
   - Datos de demostración persistidos en localStorage (reemplazar por API)
   - Sesión simulada en sessionStorage
   - Layout (topbar + sidebar) según el rol
   ===================================================================== */
(function (w) {
  'use strict';

  /* ---------- Almacenamiento seguro ---------- */
  var mem = {};
  function lsGet(k){ try { return localStorage.getItem(k); } catch(e){ return mem[k] || null; } }
  function lsSet(k,v){ try { localStorage.setItem(k,v); } catch(e){ mem[k] = v; } }
  function ssGet(k){ try { return sessionStorage.getItem(k); } catch(e){ return mem['s_'+k] || null; } }
  function ssSet(k,v){ try { sessionStorage.setItem(k,v); } catch(e){ mem['s_'+k] = v; } }
  function ssDel(k){ try { sessionStorage.removeItem(k); } catch(e){ delete mem['s_'+k]; } }
  function read(key, seed){
    var raw = lsGet(key);
    if (raw) { try { return JSON.parse(raw); } catch(e){} }
    var v = seed(); lsSet(key, JSON.stringify(v)); return v;
  }
  function write(key, v){ lsSet(key, JSON.stringify(v)); }

  /* ---------- Catálogos ---------- */
  var ROLES = {
    nomina: 'Usuario Nómina',
    jefe:   'Jefe de Nómina',
    admin:  'Administrador de permisos y auditorías'
  };
  var PERMISOS = [
    { key:'subir_documentos',    label:'Subir documentos',        grupo:'Operación de certificados' },
    { key:'cargar_excel',        label:'Cargar Excel',            grupo:'Operación de certificados' },
    { key:'generar_certificados',label:'Generar certificados',    grupo:'Operación de certificados' },
    { key:'gestionar_firma',     label:'Gestionar firma digital', grupo:'Operación de certificados' },
    { key:'enviar_certificados', label:'Enviar certificados',     grupo:'Operación de certificados' },
    { key:'ver_historial',       label:'Ver historial de Nómina', grupo:'Supervisión' },
    { key:'firmar_reemplazo',    label:'Firmar en reemplazo',     grupo:'Supervisión' },
    { key:'gestionar_permisos',  label:'Gestionar permisos',      grupo:'Administración' },
    { key:'ver_auditorias',      label:'Ver auditorías',          grupo:'Administración' }
  ];
  var PERMISOS_DEFECTO = {
    nomina: ['subir_documentos','cargar_excel','generar_certificados','gestionar_firma','enviar_certificados'],
    jefe:   ['ver_historial','firmar_reemplazo','gestionar_firma'],
    admin:  ['gestionar_permisos','ver_auditorias']
  };
  var HOME = { nomina:'inicio.html', jefe:'jefe-inicio.html', admin:'admin-inicio.html' };
  var DEMO_USER_ID = { nomina:1, jefe:2, admin:3 };

  /* ---------- Utilidades ---------- */
  function esc(s){ return String(s == null ? '' : s).replace(/[&<>"']/g, function(c){ return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]; }); }
  function hoursAgo(h){ return new Date(Date.now() - h * 3600000).toISOString(); }
  function fmt(iso){
    return new Date(iso).toLocaleString('es-CO', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' });
  }
  function permLabel(k){ for (var i=0;i<PERMISOS.length;i++) if (PERMISOS[i].key===k) return PERMISOS[i].label; return k; }
  function nextId(arr){ return arr.reduce(function(m,x){ return Math.max(m, x.id||0); }, 0) + 1; }
  function toast(msg){
    var t = document.getElementById('ctToast');
    if (!t){ t = document.createElement('div'); t.id = 'ctToast'; t.className = 'toast'; document.body.appendChild(t); }
    t.textContent = msg; t.classList.add('show');
    clearTimeout(t._h); t._h = setTimeout(function(){ t.classList.remove('show'); }, 2600);
  }

  /* ---------- Datos semilla ---------- */
  function seedUsuarios(){
    return [
      { id:1, nombre:'Juan Pérez',    usuario:'jperez',   correo:'juan.perez@upb.edu.co',    telefono:'300 000 0000', rol:'nomina', activo:true,  permisos: PERMISOS_DEFECTO.nomina.slice() },
      { id:2, nombre:'María Ramírez', usuario:'mramirez', correo:'maria.ramirez@upb.edu.co', telefono:'301 111 1111', rol:'jefe',   activo:true,  permisos: PERMISOS_DEFECTO.jefe.slice() },
      { id:3, nombre:'Carlos Gómez',  usuario:'cgomez',   correo:'carlos.gomez@upb.edu.co',  telefono:'302 222 2222', rol:'admin',  activo:true,  permisos: PERMISOS_DEFECTO.admin.slice() },
      { id:4, nombre:'Laura Torres',  usuario:'ltorres',  correo:'laura.torres@upb.edu.co',  telefono:'303 333 3333', rol:'nomina', activo:true,  permisos: ['subir_documentos','cargar_excel','generar_certificados'] },
      { id:5, nombre:'Andrés Molina', usuario:'amolina',  correo:'andres.molina@upb.edu.co', telefono:'304 444 4444', rol:'nomina', activo:false, permisos: [] }
    ];
  }
  function seedActividad(){
    var J='Juan Pérez', L='Laura Torres';
    var rows = [
      [2,   J,'Enviar certificado',  'CT-548213 → 128 destinatarios',                     'Completado'],
      [3,   J,'Generar certificado', 'Certificado Tributario 2025 · Folio CT-548213',     'Completado'],
      [3.5, J,'Cargar Excel',        'retenciones_docentes_2025.xlsx · 128 registros',    'Completado'],
      [5,   J,'Subir documentos',    'soporte_retenciones.pdf (2 archivos)',              'Completado'],
      [26,  L,'Cargar Excel',        'retenciones_administrativos.xlsx · Fila 14: NIT inválido', 'Con error'],
      [27,  L,'Subir documentos',    'listado_administrativos.pdf',                       'Completado'],
      [30,  J,'Firma digital',       'Actualizó la firma digital registrada',             'Completado'],
      [52,  J,'Enviar certificado',  'CT-547990 → 3 destinatarios · correo rebotado',     'Con error'],
      [53,  J,'Generar certificado', 'Certificado Tributario · Folio CT-547990',          'Completado'],
      [76,  L,'Generar certificado', 'Certificado Retención · Folio CT-547812',           'Completado'],
      [100, J,'Cargar Excel',        'retenciones_proveedores.xlsx · 54 registros',       'Completado'],
      [124, J,'Generar certificado', 'Certificado · Folio CT-546712',                     'Completado'],
      [150, L,'Enviar certificado',  'CT-546500 → 41 destinatarios',                      'Completado'],
      [170, J,'Subir documentos',    'soportes_agosto.pdf (4 archivos)',                  'Completado']
    ];
    return rows.map(function(r,i){
      return { id:i+1, fecha:hoursAgo(r[0]), usuario:r[1], rol:'nomina', accion:r[2], detalle:r[3], estado:r[4] };
    });
  }
  function seedPendientes(){
    var rows = [
      ['CT-548214','Certificado Tributario 2025 · Docentes',        'Juan Pérez',  1.5, 'pendiente'],
      ['CT-548215','Certificado Tributario 2025 · Administrativos', 'Laura Torres',4,   'pendiente'],
      ['CT-548216','Certificado Tributario 2025 · Proveedores',     'Juan Pérez',  20,  'pendiente'],
      ['CT-548217','Certificado de Retención · Honorarios',         'Laura Torres',28,  'pendiente'],
      ['CT-548213','Certificado Tributario 2025 · Docentes (lote 1)','Juan Pérez', 3,   'firmado']
    ];
    return rows.map(function(r){
      var o = { folio:r[0], nombre:r[1], generadoPor:r[2], fecha:hoursAgo(r[3]), estado:r[4] };
      if (r[4]==='firmado'){ o.firmadoPor='Juan Pérez'; o.fechaFirma=hoursAgo(2.5); o.enReemplazo=false; }
      return o;
    });
  }
  function seedAuditoria(){
    var rows = [
      [0.5,'Carlos Gómez','admin','Autenticación','Inicio de sesión','Acceso exitoso'],
      [1,  'María Ramírez','jefe','Historial','Consulta de historial','Revisó la actividad del Usuario Nómina'],
      [2,  'Juan Pérez','nomina','Certificados','Envío de certificado','CT-548213 → 128 destinatarios'],
      [3,  'Juan Pérez','nomina','Certificados','Generación de certificado','Folio CT-548213'],
      [3.5,'Juan Pérez','nomina','Certificados','Carga de Excel','retenciones_docentes_2025.xlsx'],
      [4,  'Juan Pérez','nomina','Autenticación','Inicio de sesión','Acceso exitoso'],
      [8,  'Carlos Gómez','admin','Usuarios y permisos','Modificación de permisos','Laura Torres · se retiró: Enviar certificados'],
      [9,  'Carlos Gómez','admin','Autenticación','Inicio de sesión','Acceso exitoso'],
      [25, 'Laura Torres','nomina','Autenticación','Intento de sesión fallido','Contraseña incorrecta'],
      [25.2,'Laura Torres','nomina','Autenticación','Inicio de sesión','Acceso exitoso'],
      [26, 'Laura Torres','nomina','Certificados','Carga de Excel','retenciones_administrativos.xlsx · error de validación'],
      [30, 'Juan Pérez','nomina','Firma digital','Actualización de firma','Firma digital reemplazada'],
      [48, 'Carlos Gómez','admin','Usuarios y permisos','Cambio de estado','Andrés Molina · desactivado'],
      [52, 'Juan Pérez','nomina','Certificados','Envío de certificado','CT-547990 · correo rebotado'],
      [72, 'María Ramírez','jefe','Autenticación','Inicio de sesión','Acceso exitoso'],
      [76, 'Laura Torres','nomina','Certificados','Generación de certificado','Folio CT-547812'],
      [96, 'Carlos Gómez','admin','Usuarios y permisos','Modificación de permisos','María Ramírez · se agregó: Firmar en reemplazo'],
      [100,'Juan Pérez','nomina','Certificados','Carga de Excel','retenciones_proveedores.xlsx'],
      [124,'Juan Pérez','nomina','Certificados','Generación de certificado','Folio CT-546712'],
      [150,'Laura Torres','nomina','Certificados','Envío de certificado','CT-546500 → 41 destinatarios'],
      [170,'Juan Pérez','nomina','Certificados','Subida de documentos','soportes_agosto.pdf']
    ];
    return rows.map(function(r,i){
      return { id:i+1, fecha:hoursAgo(r[0]), usuario:r[1], rol:r[2], modulo:r[3], accion:r[4], detalle:r[5] };
    });
  }

  /* ---------- Acceso a datos ---------- */
  var K = { u:'ct2_usuarios', a:'ct2_actividad', p:'ct2_pendientes', au:'ct2_auditoria', n:'ct2_nomina_estado' };
  var CT = {
    ROLES: ROLES, PERMISOS: PERMISOS, PERMISOS_DEFECTO: PERMISOS_DEFECTO,
    esc: esc, fmt: fmt, toast: toast, permLabel: permLabel,

    usuarios:   function(){ return read(K.u,  seedUsuarios); },
    saveUsuarios: function(v){ write(K.u, v); },
    actividad:  function(){ return read(K.a,  seedActividad); },
    pendientes: function(){ return read(K.p,  seedPendientes); },
    savePendientes: function(v){ write(K.p, v); },
    auditoria:  function(){ return read(K.au, seedAuditoria); },
    nominaEstado: function(){ return read(K.n, function(){ return { disponible:true }; }); },
    saveNominaEstado: function(v){ write(K.n, v); },

    currentUser: function(){
      var rol = ssGet('ct_rol');
      var users = CT.usuarios();
      var id = parseInt(ssGet('ct_uid'), 10) || DEMO_USER_ID[rol];
      for (var i=0;i<users.length;i++) if (users[i].id === id) return users[i];
      return { id:0, nombre:'Usuario', rol:rol, permisos:[] };
    },

    audit: function(modulo, accion, detalle){
      var u = CT.currentUser(), list = CT.auditoria();
      list.push({ id: nextId(list), fecha: new Date().toISOString(), usuario:u.nombre, rol:u.rol, modulo:modulo, accion:accion, detalle:detalle || '' });
      write(K.au, list);
    },
    logActividad: function(accion, detalle, estado){
      var u = CT.currentUser(), list = CT.actividad();
      list.push({ id: nextId(list), fecha: new Date().toISOString(), usuario:u.nombre, rol:u.rol, accion:accion, detalle:detalle, estado: estado || 'Completado' });
      write(K.a, list);
    },

    /* ---------- Sesión ---------- */
    roleFromUsername: function(name){
      var u = String(name || '').toLowerCase();
      if (/^jefe/.test(u)) return 'jefe';
      if (/^admin/.test(u)) return 'admin';
      return 'nomina';
    },
    login: function(username){
      var rol = CT.roleFromUsername(username);
      ssSet('ct_rol', rol); ssSet('ct_uid', String(DEMO_USER_ID[rol]));
      CT.audit('Autenticación', 'Inicio de sesión', 'Acceso exitoso');
      return HOME[rol];
    },
    logout: function(){
      if (ssGet('ct_rol')) CT.audit('Autenticación', 'Cierre de sesión', '');
      ssDel('ct_rol'); ssDel('ct_uid');
      location.href = 'login.html';
    },
    /* Atajo para el índice de demostración */
    enterAs: function(rol){
      ssSet('ct_rol', rol); ssSet('ct_uid', String(DEMO_USER_ID[rol]));
      location.href = HOME[rol];
    },
    /* Protege la página: role = 'jefe' | 'admin' | ['jefe','admin'] */
    guard: function(role){
      var allowed = [].concat(role), actual = ssGet('ct_rol');
      if (allowed.indexOf(actual) === -1){ location.replace('login.html'); return false; }
      return true;
    },

    /* ---------- Layout ---------- */
    shell: function(opts){
      var rol = ssGet('ct_rol'), user = CT.currentUser();
      var ICON = {
        home:'<path d="M3 11l9-8 9 8"/><path d="M5 10v10a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V10"/>',
        clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/>',
        pen:'<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
        users:'<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
        shield:'<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/>',
        user:'<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
        out:'<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>'
      };
      function svg(p){ return '<svg viewBox="0 0 24 24" width="1em" height="1em" style="vertical-align:-0.125em;flex-shrink:0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + p + '</svg>'; }
      CT.svg = svg; CT.ICON = ICON;
      var NAV = {
        jefe: [
          ['inicio',   'Inicio',              'jefe-inicio.html',    ICON.home],
          ['historial','Historial de Nómina', 'jefe-historial.html', ICON.clock],
          ['firmar',   'Firmar certificados', 'jefe-firmar.html',    ICON.pen],
          ['perfil',   'Perfil',              'perfil.html',         ICON.user]
        ],
        admin: [
          ['inicio',     'Inicio',              'admin-inicio.html',     ICON.home],
          ['usuarios',   'Usuarios y permisos', 'admin-usuarios.html',   ICON.users],
          ['auditorias', 'Auditorías',          'admin-auditorias.html', ICON.shield],
          ['perfil',     'Perfil',              'perfil-rol.html',       ICON.user]
        ]
      };
      var items = (NAV[rol] || []).map(function(n){
        return '<a class="nav-item' + (n[0]===opts.active ? ' active' : '') + '" href="' + n[2] + '">' +
               '<span class="nav-icon">' + svg(n[3]) + '</span> ' + n[1] + '</a>';
      }).join('');
      var html =
        '<header class="topbar">' +
          '<div class="brand"><div class="logo-mark"><img src="images/upb-logo.png" alt="UPB"></div></div>' +
          '<div class="page-title">' + esc(opts.title) + '</div>' +
          '<div class="topbar-actions"><a class="user-chip" href="' + (rol === 'jefe' ? 'perfil.html' : 'perfil-rol.html') + '">' + svg(ICON.user) + ' ' + esc(user.nombre) + '</a></div>' +
        '</header>' +
        '<nav class="sidebar">' + items +
          '<a class="nav-item logout" href="#" id="ctLogout"><span class="nav-icon">' + svg(ICON.out) + '</span> Cerrar Sesión</a>' +
        '</nav>';
      document.body.insertAdjacentHTML('beforeend', html);
      document.getElementById('ctLogout').addEventListener('click', function(e){ e.preventDefault(); CT.logout(); });
    }
  };

  w.CT = CT;
})(window);
