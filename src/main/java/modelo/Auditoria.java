package modelo;

import java.sql.Timestamp;

public class Auditoria {

    private int idLog;
    private int idUsuario;
    private String accion;
    private Timestamp fechaRegistro;
    private String tablaAfectada;
    private int registroId;
    private String datosAnteriores;
    private String datosNuevos;


    public Auditoria() {

    }


    public Auditoria(int idUsuario, String accion, 
                     String tablaAfectada, int registroId,
                     String datosAnteriores,
                     String datosNuevos) {

        this.idUsuario = idUsuario;
        this.accion = accion;
        this.tablaAfectada = tablaAfectada;
        this.registroId = registroId;
        this.datosAnteriores = datosAnteriores;
        this.datosNuevos = datosNuevos;
    }


    public int getIdLog() {
        return idLog;
    }


    public void setIdLog(int idLog) {
        this.idLog = idLog;
    }


    public int getIdUsuario() {
        return idUsuario;
    }


    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }


    public String getAccion() {
        return accion;
    }


    public void setAccion(String accion) {
        this.accion = accion;
    }


    public Timestamp getFechaRegistro() {
        return fechaRegistro;
    }


    public void setFechaRegistro(Timestamp fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }


    public String getTablaAfectada() {
        return tablaAfectada;
    }


    public void setTablaAfectada(String tablaAfectada) {
        this.tablaAfectada = tablaAfectada;
    }


    public int getRegistroId() {
        return registroId;
    }

    public void setRegistroId(int registroId) {
        this.registroId = registroId;
    }


    public String getDatosAnteriores() {
        return datosAnteriores;
    }


    public void setDatosAnteriores(String datosAnteriores) {
        this.datosAnteriores = datosAnteriores;
    }


    public String getDatosNuevos() {
        return datosNuevos;
    }


    public void setDatosNuevos(String datosNuevos) {
        this.datosNuevos = datosNuevos;
    }
}