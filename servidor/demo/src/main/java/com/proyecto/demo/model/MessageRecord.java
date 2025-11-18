package com.proyecto.demo.model;

import java.time.LocalDateTime;

public class MessageRecord {
    private Long id;
    private Long emisorId;
    private Long receptorId;
    private String tipoMensaje; // 'TEXTO'|'ARCHIVO'
    private String contenido;
    private Long archivoId;
    private String sesionId;
    private LocalDateTime creadoEn;

    public MessageRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmisorId() { return emisorId; }
    public void setEmisorId(Long emisorId) { this.emisorId = emisorId; }

    public Long getReceptorId() { return receptorId; }
    public void setReceptorId(Long receptorId) { this.receptorId = receptorId; }

    public String getTipoMensaje() { return tipoMensaje; }
    public void setTipoMensaje(String tipoMensaje) { this.tipoMensaje = tipoMensaje; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public Long getArchivoId() { return archivoId; }
    public void setArchivoId(Long archivoId) { this.archivoId = archivoId; }

    public String getSesionId() { return sesionId; }
    public void setSesionId(String sesionId) { this.sesionId = sesionId; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
