/* =====================================================================
   Avisos del aplicativo (reemplazan las ventanas alert/confirm del navegador)

   Avisos.mostrar(mensaje, tipo, titulo)   tipo: 'exito' | 'error' | 'aviso' | 'info'
   Avisos.alSiguiente(mensaje, tipo, titulo)  igual, pero se muestra en la página a la que se va
   Avisos.confirmar(mensaje, opciones) -> Promise<boolean>
       opciones: { titulo, aceptar, cancelar, peligro }

   - Los avisos cortos de éxito o información se cierran solos; los errores y los mensajes
     largos (por ejemplo el resultado de un cargue) quedan hasta que el usuario los cierra
   - Avisos.alSiguiente sirve cuando justo después se cambia de página (se muestra una sola vez allá)
   ===================================================================== */
(function (w) {
  'use strict';

  var CLAVE = 'ct_avisos_pendientes';
  var TIPOS = {
    exito: { titulo: 'Listo',       icono: '<path d="M20 6L9 17l-5-5"/>' },
    error: { titulo: 'Error',       icono: '<path d="M18 6L6 18"/><path d="M6 6l12 12"/>' },
    aviso: { titulo: 'Atención',    icono: '<path d="M12 8v5"/><path d="M12 16.5v.5"/>' },
    info:  { titulo: 'Información', icono: '<path d="M12 11v6"/><path d="M12 7.5v.5"/>' }
  };

  var CSS =
    '.av-pila{position:fixed;top:72px;right:20px;z-index:10000;display:flex;flex-direction:column;gap:10px;width:380px;max-width:calc(100vw - 32px);pointer-events:none}' +
    '.av{pointer-events:auto;display:flex;gap:12px;align-items:flex-start;background:#fff;border:1px solid #E1E3E8;border-left:4px solid var(--av-c);' +
      'border-radius:8px;box-shadow:0 8px 24px rgba(20,20,30,.14);padding:12px 12px 12px 14px;font-family:"Segoe UI","Helvetica Neue",Arial,sans-serif;' +
      'color:#23262B;animation:av-entra .22s ease-out}' +
    '.av.sale{animation:av-sale .18s ease-in forwards}' +
    '.av-ico{flex:0 0 26px;height:26px;border-radius:50%;background:var(--av-f);color:var(--av-c);display:flex;align-items:center;justify-content:center}' +
    '.av-ico svg{width:15px;height:15px}' +
    '.av-cuerpo{flex:1;min-width:0}' +
    '.av-titulo{font-size:13px;font-weight:700;margin:2px 0 3px}' +
    '.av-texto{font-size:12.5px;line-height:1.45;color:#3A3F47;white-space:pre-line;word-break:break-word;max-height:260px;overflow:auto}' +
    '.av-cerrar{flex:0 0 auto;border:none;background:none;color:#9AA0A8;cursor:pointer;font-size:18px;line-height:1;padding:2px 4px;border-radius:4px}' +
    '.av-cerrar:hover{color:#23262B;background:#F4F5F7}' +
    '.av-exito{--av-c:#1E7B3A;--av-f:#E6F4EA}.av-error{--av-c:#C40510;--av-f:#FDECEC}' +
    '.av-aviso{--av-c:#9A6700;--av-f:#FBF3DC}.av-info{--av-c:#1F4FA8;--av-f:#EAF1FD}' +
    '.av-fondo{position:fixed;inset:0;z-index:10001;background:rgba(20,20,30,.45);display:flex;align-items:center;justify-content:center;padding:16px;animation:av-aparece .15s ease-out}' +
    '.av-caja{background:#fff;border-radius:8px;width:400px;max-width:100%;padding:22px 24px 18px;box-shadow:0 12px 32px rgba(0,0,0,.2);' +
      'font-family:"Segoe UI","Helvetica Neue",Arial,sans-serif;color:#23262B}' +
    '.av-caja h3{margin:0 0 8px;font-size:16px}' +
    '.av-caja p{margin:0 0 20px;font-size:13px;line-height:1.5;color:#3A3F47;white-space:pre-line}' +
    '.av-botones{display:flex;justify-content:flex-end;gap:10px}' +
    '.av-btn{padding:9px 20px;border-radius:20px;font-size:12.5px;font-weight:700;cursor:pointer;font-family:inherit;border:1px solid #E30613}' +
    '.av-btn.si{background:#E30613;color:#fff}.av-btn.si:hover{background:#C40510}' +
    '.av-btn.no{background:#fff;color:#E30613}.av-btn.no:hover{background:#FDECEC}' +
    '@keyframes av-entra{from{opacity:0;transform:translateX(24px)}to{opacity:1;transform:none}}' +
    '@keyframes av-sale{to{opacity:0;transform:translateX(24px)}}' +
    '@keyframes av-aparece{from{opacity:0}to{opacity:1}}' +
    '@media (max-width:560px){.av-pila{left:16px;right:16px;width:auto;top:64px}}';

  function esc(s){ return String(s == null ? '' : s).replace(/[&<>"']/g, function(c){ return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]; }); }
  function svg(p){ return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">' + p + '</svg>'; }

  function listo(fn){
    if (document.body) fn(); else document.addEventListener('DOMContentLoaded', fn);
  }
  function estilos(){
    if (document.getElementById('av-estilos')) return;
    var st = document.createElement('style'); st.id = 'av-estilos'; st.textContent = CSS;
    document.head.appendChild(st);
  }
  function pila(){
    var p = document.querySelector('.av-pila');
    if (!p){ p = document.createElement('div'); p.className = 'av-pila'; p.setAttribute('role', 'status'); p.setAttribute('aria-live', 'polite'); document.body.appendChild(p); }
    return p;
  }

  /* ---------- Avisos pendientes (sobreviven a un cambio de página) ---------- */
  function leerPendientes(){ try { return JSON.parse(sessionStorage.getItem(CLAVE) || '[]'); } catch(e){ return []; } }
  function guardarPendientes(l){ try { sessionStorage.setItem(CLAVE, JSON.stringify(l)); } catch(e){} }
  function quitarPendiente(id){ guardarPendientes(leerPendientes().filter(function(a){ return a.id !== id; })); }

  // Tipo según el texto, para los alert() que no lo indican
  function adivinarTipo(m){
    m = String(m).toLowerCase();
    if (/error|no se pudo|falló|fallo|no es correcta|incorrect/.test(m)) return 'error';
    if (/correctamente|guardad|exitos|listo/.test(m)) return 'exito';
    if (/debes|debe |selecciona|escribe|primero|no cumple|no coincide|solo se aceptan|no hay/.test(m)) return 'aviso';
    return 'info';
  }

  function pintar(a){
    listo(function(){
      estilos();
      var t = TIPOS[a.tipo] || TIPOS.info;
      var el = document.createElement('div');
      el.className = 'av av-' + (TIPOS[a.tipo] ? a.tipo : 'info');
      el.setAttribute('role', a.tipo === 'error' ? 'alert' : 'status');
      el.innerHTML = '<div class="av-ico">' + svg(t.icono) + '</div>' +
        '<div class="av-cuerpo"><div class="av-titulo">' + esc(a.titulo || t.titulo) + '</div>' +
        '<div class="av-texto">' + esc(a.mensaje) + '</div></div>' +
        '<button class="av-cerrar" aria-label="Cerrar aviso">&times;</button>';
      var cerrado = false;
      function cerrar(){
        if (cerrado) return; cerrado = true;
        quitarPendiente(a.id);
        el.classList.add('sale');
        setTimeout(function(){ el.remove(); }, 200);
      }
      el.querySelector('.av-cerrar').addEventListener('click', cerrar);
      pila().appendChild(el);
      var corto = String(a.mensaje).length <= 140 && String(a.mensaje).indexOf('\n') === -1;
      if ((a.tipo === 'exito' || a.tipo === 'info') && corto) setTimeout(cerrar, 5000);
    });
  }

  function crear(mensaje, tipo, titulo){
    return { id: Date.now() + '-' + Math.random().toString(36).slice(2, 7), mensaje: String(mensaje == null ? '' : mensaje),
             tipo: tipo || adivinarTipo(mensaje), titulo: titulo || '' };
  }

  var Avisos = {
    mostrar: function(mensaje, tipo, titulo){
      pintar(crear(mensaje, tipo, titulo));
    },
    alSiguiente: function(mensaje, tipo, titulo){
      var l = leerPendientes(); l.push(crear(mensaje, tipo, titulo)); guardarPendientes(l);
    },
    confirmar: function(mensaje, op){
      op = op || {};
      return new Promise(function(resolver){
        listo(function(){
          estilos();
          var previo = document.activeElement;
          var f = document.createElement('div');
          f.className = 'av-fondo';
          f.innerHTML = '<div class="av-caja" role="dialog" aria-modal="true" aria-labelledby="av-tt">' +
            '<h3 id="av-tt">' + esc(op.titulo || 'Confirmar') + '</h3><p>' + esc(mensaje) + '</p>' +
            '<div class="av-botones"><button class="av-btn no">' + esc(op.cancelar || 'Cancelar') + '</button>' +
            '<button class="av-btn si">' + esc(op.aceptar || 'Aceptar') + '</button></div></div>';
          function fin(v){
            document.removeEventListener('keydown', teclas);
            f.remove(); if (previo && previo.focus) previo.focus();
            resolver(v);
          }
          function teclas(e){ if (e.key === 'Escape') fin(false); }
          f.querySelector('.si').addEventListener('click', function(){ fin(true); });
          f.querySelector('.no').addEventListener('click', function(){ fin(false); });
          f.addEventListener('click', function(e){ if (e.target === f) fin(false); });
          document.addEventListener('keydown', teclas);
          document.body.appendChild(f);
          f.querySelector(op.peligro ? '.no' : '.si').focus();
        });
      });
    }
  };

  // Cualquier alert() que quede en el código también se muestra con el estilo del aplicativo.
  // Como alert() solía ir justo antes de cambiar de página, se guarda por si la página cambia.
  w.alert = function(m){
    var a = crear(m);
    var l = leerPendientes(); l.push(a); guardarPendientes(l);
    pintar(a);
  };

  // Al abrir la página: mostrar (una sola vez) los avisos que venían de la página anterior
  var pendientes = leerPendientes();
  guardarPendientes([]);
  pendientes.forEach(pintar);

  w.Avisos = Avisos;
})(window);
