package com.proyecto.demo.dao;

public interface ArchivoDao {
    long insertArchivo(String filename, String path, long size, Long propietarioId);
    ArchivoInfo findById(long id);

    public static class ArchivoInfo {
        public long id;
        public String filename;
        public String path;
        public long size;
        public java.time.LocalDateTime creadoEn;
    }
}
