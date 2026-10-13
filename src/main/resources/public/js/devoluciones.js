/* Certificados devueltos por los clientes (datos de demostración).
   Un certificado devuelto NO se modifica: se vuelve a generar.
   Mientras no se regenere, aparece en Solicitudes como "Pendiente". */
(function (w) {
  'use strict';
  var KEY = 'ct_dev_regeneradas';
  var LISTA = [
    { id:'DEV-001', cliente:'Luis Pardo',   documento:'109.582',   fecha:'01 sep. 2026',
      motivo:'El nombre en el certificado no coincide con el registrado en el sistema.' },
    { id:'DEV-002', cliente:'Laura Muñoz',  documento:'1.020.581', fecha:'30 ago. 2026',
      motivo:'Solicita corrección del valor certificado del período gravable.' }
  ];
  function leer(){ try { return JSON.parse(sessionStorage.getItem(KEY) || '[]'); } catch(e){ return []; } }
  function guardar(v){ try { sessionStorage.setItem(KEY, JSON.stringify(v)); } catch(e){} }

  w.Devoluciones = {
    todas: function(){ return LISTA.slice(); },
    regeneradas: function(){ return leer(); },
    estaRegenerada: function(id){ return leer().indexOf(id) > -1; },
    pendientes: function(){ var r = leer(); return LISTA.filter(function(d){ return r.indexOf(d.id) === -1; }); },
    buscar: function(id){ return LISTA.filter(function(d){ return d.id === id; })[0]; },
    marcarRegenerada: function(id){ var r = leer(); if (r.indexOf(id) === -1){ r.push(id); guardar(r); } }
  };
})(window);
