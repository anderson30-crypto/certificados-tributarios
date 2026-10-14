// Sesión del usuario en todas las pantallas (menos el login):
//  - muestra el nombre de quien inició sesión en la barra superior
//  - "Cerrar Sesión" cierra la sesión en el servidor
//  - si la sesión vence (respuesta 401), vuelve al login
(function () {
  const fetchOriginal = window.fetch.bind(window);

  window.fetch = async function (...args) {
    const respuesta = await fetchOriginal(...args);
    if (respuesta.status === 401) {
      window.location.href = 'login.html';
    }
    return respuesta;
  };

  fetchOriginal('/api/sesion')
    .then(function (r) {
      if (r.status === 401) { window.location.href = 'login.html'; return null; }
      // Otro error: se avisa en vez de volver al login (eso causaba un ciclo de recargas)
      if (!r.ok) {
        return r.text().then(function (t) {
          let msg = t; try { msg = JSON.parse(t).error || t; } catch (e) { }
          (window.Avisos ? Avisos.mostrar : alert)(msg + '\n\nRevise la consola del WebServer.', 'error', 'No se pudo cargar su sesión (error ' + r.status + ')');
          return null;
        });
      }
      return r.json();
    })
    .then(function (usuario) {
      if (!usuario) {
        return;
      }
      document.querySelectorAll('.user-chip').forEach(function (chip) {
        const textos = Array.from(chip.childNodes).filter(function (n) { return n.nodeType === Node.TEXT_NODE; });
        if (textos.length > 0) {
          textos[textos.length - 1].textContent = ' ' + usuario.nombre;
        }
        chip.title = usuario.nombre + ' (' + usuario.rolNombre + ')';
      });
    });

  document.querySelectorAll('.logout').forEach(function (enlace) {
    enlace.addEventListener('click', async function (e) {
      e.preventDefault();
      await fetchOriginal('/api/logout', { method: 'POST' });
      window.location.href = 'login.html';
    });
  });
})();
