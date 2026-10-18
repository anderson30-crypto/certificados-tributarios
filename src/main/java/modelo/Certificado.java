package modelo;

import java.util.Date;

public class Certificado {


    private int idCertificado;

    private String nombreContratista;

    private String documento;

    private String contrato;

    private double salario;

    private Date fechaGeneracion;

    private String estado;

    private String rutaPdf;


    // CAMPOS DE SEGURIDAD

    private String hashCertificado;

    private String rutaFirma;

    private boolean firmado;

    private String rutaCifrado;

    private boolean cifrado;



    // GETTER Y SETTER ID

    public int getIdCertificado() {
        return idCertificado;
    }


    public void setIdCertificado(int idCertificado) {
        this.idCertificado = idCertificado;
    }



    // NOMBRE CONTRATISTA

    public String getNombreContratista() {
        return nombreContratista;
    }


    public void setNombreContratista(String nombreContratista) {
        this.nombreContratista = nombreContratista;
    }



    // DOCUMENTO

    public String getDocumento() {
        return documento;
    }


    public void setDocumento(String documento) {
        this.documento = documento;
    }



    // CONTRATO

    public String getContrato() {
        return contrato;
    }


    public void setContrato(String contrato) {
        this.contrato = contrato;
    }



    // SALARIO

    public double getSalario() {
        return salario;
    }


    public void setSalario(double salario) {
        this.salario = salario;
    }



    // FECHA GENERACION

    public Date getFechaGeneracion() {
        return fechaGeneracion;
    }


    public void setFechaGeneracion(Date fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }



    // ESTADO

    public String getEstado() {
        return estado;
    }


    public void setEstado(String estado) {
        this.estado = estado;
    }



    // RUTA PDF

    public String getRutaPdf() {
        return rutaPdf;
    }


    public void setRutaPdf(String rutaPdf) {
        this.rutaPdf = rutaPdf;
    }



    // ============================
    // SEGURIDAD DEL CERTIFICADO
    // ============================


    // HASH

    public String getHashCertificado() {
        return hashCertificado;
    }


    public void setHashCertificado(String hashCertificado) {
        this.hashCertificado = hashCertificado;
    }



    // FIRMA DIGITAL

    public String getRutaFirma() {
        return rutaFirma;
    }


    public void setRutaFirma(String rutaFirma) {
        this.rutaFirma = rutaFirma;
    }



    public boolean isFirmado() {
        return firmado;
    }


    public void setFirmado(boolean firmado) {
        this.firmado = firmado;
    }



    // CIFRADO

    public String getRutaCifrado() {
        return rutaCifrado;
    }


    public void setRutaCifrado(String rutaCifrado) {
        this.rutaCifrado = rutaCifrado;
    }



    public boolean isCifrado() {
        return cifrado;
    }


    public void setCifrado(boolean cifrado) {
        this.cifrado = cifrado;
    }


}