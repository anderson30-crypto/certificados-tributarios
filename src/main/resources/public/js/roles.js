/* =====================================================================
   Capa compartida de las vistas por rol (Administrador / Jefe de Nómina / Gestión Humana)
   - La sesión, el usuario y su rol vienen del servidor (/api/sesion); el servidor además
     impide entrar a pantallas o acciones de otro rol (Seguridad.Roles)
   - Layout (topbar + sidebar) según el rol
   ===================================================================== */
(function (w) {
  'use strict';

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
  function fmt(iso){
    return new Date(iso).toLocaleString('es-CO', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' });
  }
  // Avisos del aplicativo (js/avisos.js); tipo por defecto: éxito
  function toast(msg, tipo, titulo){ Avisos.mostrar(msg, tipo || 'exito', titulo); }

  var CT = {
    ROLES: ROLES,
    esc: esc, fmt: fmt, toast: toast,

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
