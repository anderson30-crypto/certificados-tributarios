/* =====================================================================
   Capa compartida de las vistas por rol (Administrador / Jefe de Nómina / Gestión Humana)
   - La sesión, el usuario y su rol vienen del servidor (/api/sesion); el servidor además
     impide entrar a pantallas o acciones de otro rol (Seguridad.Roles)
   - Las pantallas del Jefe todavía usan datos de demostración (pendientes por firmar,
     historial) hasta conectar la firma de certificados
   - Layout (topbar + sidebar) según el rol
   ===================================================================== */
(function (w) {
  'use strict';

  /* ---------- Almacenamiento local (solo datos de demostración del Jefe) ---------- */
  var mem = {};
  function lsGet(k){ try { return localStorage.getItem(k); } catch(e){ return mem[k] || null; } }
  function lsSet(k,v){ try { localStorage.setItem(k,v); } catch(e){ mem[k] = v; } }
  function read(key, seed){
    var raw = lsGet(key);
    if (raw) { try { return JSON.parse(raw); } catch(e){} }
    var v = seed(); lsSet(key, JSON.stringify(v)); return v;
  }
  function write(key, v){ lsSet(key, JSON.stringify(v)); }

  /* ---------- Catálogos ---------- */
  var ROLES = {
    nomina: 'Nómina',
    jefe:   'Jefe de Nómina',
    gh:     'Gestión Humana',
    admin:  'Administrador'
  };
  // Jefe de Nómina y Gestión Humana comparten pantallas: los dos firman
  var FIRMANTES = ['jefe', 'gh'];

  /* ---------- Si la sesión vence en cualquier llamada al servidor, volver al login ---------- */
  var fetchOriginal = w.fetch.bind(w);
  w.fetch = function(){
    return fetchOriginal.apply(w, arguments).then(function(r){
      if (r.status === 401) { location.replace('login.html'); }
      return r;
    });
  };

  /* ---------- Usuario de la sesión (una sola consulta por página) ---------- */
  var sesion = null;
  function cargarSesion(){
    if (sesion) return sesion;
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/api/sesion', false);   // síncrono: las páginas lo necesitan antes de dibujarse
    try { xhr.send(); } catch(e){}
    if (xhr.status === 401){ location.replace('login.html'); throw new Error('Sin sesión'); }
    if (xhr.status !== 200){
      // Error del servidor: se muestra en vez de volver al login (eso causaba un ciclo de recargas)
      var detalle = '';
      try { detalle = JSON.parse(xhr.responseText).error || ''; } catch(e){ detalle = xhr.responseText || ''; }
      document.documentElement.innerHTML =
        '<body style="font-family:Segoe UI,Arial,sans-serif;max-width:640px;margin:60px auto;padding:0 16px;color:#23262B">' +
        '<h2 style="color:#C40510">No se pudo cargar su sesión</h2>' +
        '<p>El servidor respondió con el código ' + xhr.status + (detalle ? ':' : '.') + '</p>' +
        (detalle ? '<pre style="white-space:pre-wrap;background:#F4F5F7;padding:12px;border-radius:6px">' + esc(detalle) + '</pre>' : '') +
        '<p>Revise la consola donde corre el WebServer. Después puede <a href="#" onclick="fetch(\'/api/logout\',{method:\'POST\'}).then(function(){location.href=\'login.html\'});return false">cerrar sesión</a>.</p></body>';
      throw new Error('Error ' + xhr.status + ' en /api/sesion');
    }
    var d = JSON.parse(xhr.responseText);
    sesion = { id:d.id, nombre:d.nombre, usuario:d.username, correo:d.correo, telefono:d.telefono || '',
               rol:d.rol, rolNombre:d.rolNombre, funciones:d.funciones || [], inicio:d.inicio };
    return sesion;
  }

  /* ---------- Utilidades ---------- */
  function esc(s){ return String(s == null ? '' : s).replace(/[&<>"']/g, function(c){ return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]; }); }
  function hoursAgo(h){ return new Date(Date.now() - h * 3600000).toISOString(); }
  function fmt(iso){
    return new Date(iso).toLocaleString('es-CO', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' });
  }
  function nextId(arr){ return arr.reduce(function(m,x){ return Math.max(m, x.id||0); }, 0) + 1; }
  function toast(msg){
    var t = document.getElementById('ctToast');
    if (!t){ t = document.createElement('div'); t.id = 'ctToast'; t.className = 'toast'; document.body.appendChild(t); }
    t.textContent = msg; t.classList.add('show');
    clearTimeout(t._h); t._h = setTimeout(function(){ t.classList.remove('show'); }, 2600);
  }

  /* ---------- Datos de demostración del Jefe (se reemplazan al conectar la firma) ---------- */
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

  var K = { a:'ct2_actividad', p:'ct2_pendientes', n:'ct2_nomina_estado' };
  var CT = {
    ROLES: ROLES,
    esc: esc, fmt: fmt, toast: toast,

    actividad:  function(){ return read(K.a,  seedActividad); },
    pendientes: function(){ return read(K.p,  seedPendientes); },
    savePendientes: function(v){ write(K.p, v); },
    nominaEstado: function(){ return read(K.n, function(){ return { disponible:true }; }); },
    saveNominaEstado: function(v){ write(K.n, v); },
    logActividad: function(accion, detalle, estado){
      var u = CT.currentUser(), list = CT.actividad();
      list.push({ id: nextId(list), fecha: new Date().toISOString(), usuario:u.nombre, rol:u.rol, accion:accion, detalle:detalle, estado: estado || 'Completado' });
      write(K.a, list);
    },

    /** Usuario con sesión: { id, nombre, usuario, correo, telefono, rol, rolNombre, funciones, inicio } */
    currentUser: function(){ return cargarSesion(); },
    esFirmante: function(rol){ return FIRMANTES.indexOf(rol) > -1; },

    /** Guarda un evento en la auditoría del servidor */
    audit: function(modulo, accion, detalle){
      fetch('/api/auditoria/evento', { method:'POST', headers:{ 'Content-Type':'application/json' },
        body: JSON.stringify({ modulo:modulo, accion:accion, detalle:detalle || '' }) });
    },

    /* ---------- Sesión ---------- */
    logout: function(){
      fetchOriginal('/api/logout', { method:'POST' }).then(function(){ location.href = 'login.html'; },
                                                          function(){ location.href = 'login.html'; });
    },
    /* Protege la página: role = 'jefe' | 'admin' | ['jefe','admin'] ('jefe' incluye a Gestión Humana).
       El servidor ya bloquea el acceso; esto solo evita dibujar una pantalla ajena. */
    guard: function(role){
      var allowed = [].concat(role), u = cargarSesion();
      if (allowed.indexOf('jefe') > -1) allowed = allowed.concat(['gh']);
      if (allowed.indexOf(u.rol) === -1){ location.replace(u.inicio || 'login.html'); return false; }
      return true;
    },

    /* ---------- Layout ---------- */
    shell: function(opts){
      var user = CT.currentUser(), rol = CT.esFirmante(user.rol) ? 'jefe' : user.rol;
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
          ['perfil',   'Perfil',              'perfil-rol.html',     ICON.user]
        ],
        admin: [
          ['inicio',     'Inicio',              'admin-inicio.html',     ICON.home],
          ['usuarios',   'Usuarios'          , 'admin-usuarios.html',   ICON.users],
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
          '<div class="topbar-actions"><a class="user-chip" href="' + 'perfil-rol.html' + '">' + svg(ICON.user) + ' ' + esc(user.nombre) + '</a></div>' +
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
