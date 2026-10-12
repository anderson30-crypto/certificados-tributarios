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
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (usuario) {
      if (!usuario) {
        window.location.href = 'login.html';
        return;
      }
      document.querySelectorAll('.user-chip').forEach(function (chip) {
        const textos = Array.from(chip.childNodes).filter(function (n) { return n.nodeType === Node.TEXT_NODE; });
        if (textos.length > 0) {
          textos[textos.length - 1].textContent = ' ' + usuario.nombre;
        }
        chip.title = usuario.nombre + ' (' + usuario.rol + ')';
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
