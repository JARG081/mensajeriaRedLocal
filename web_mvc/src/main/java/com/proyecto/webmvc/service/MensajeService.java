package com.proyecto.webmvc.service;

import com.proyecto.webmvc.dao.MensajeDao;
import com.proyecto.webmvc.dao.ArchivoDao;
import com.proyecto.webmvc.dao.UsuarioDao;
import com.proyecto.webmvc.dao.SesionDao;
import com.proyecto.webmvc.model.Mensaje;
import com.proyecto.webmvc.model.Archivo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MensajeService {
    private final MensajeDao mensajeDao;
    private final ArchivoDao archivoDao;
    private final UsuarioDao usuarioDao;
    private final SesionDao sesionDao;

    public MensajeService(MensajeDao mensajeDao, ArchivoDao archivoDao, UsuarioDao usuarioDao, SesionDao sesionDao) {
        this.mensajeDao = mensajeDao;
        this.archivoDao = archivoDao;
        this.usuarioDao = usuarioDao;
        this.sesionDao = sesionDao;
    }

    public List<Mensaje> mensajesPorEmisorYTipo(Long emisorId, String tipo) {
        return mensajeDao.findByEmisorAndTipo(emisorId, tipo);
    }

    public Mensaje mensajeDetalle(Long id) { return mensajeDao.findById(id); }

    public List<Archivo> archivosPorTamano() { return archivoDao.findAllOrderBySizeDesc(); }

    public List<Map<String,Object>> usuariosConectados() { return sesionDao.findConnectedSessions(); }

    public List<Map<String,Object>> usuariosDesconectados() { return sesionDao.findDisconnectedSessions(); }

    public Map<String,Object> usuarioMasEnvios() {
        java.util.Map<String,Object> row = mensajeDao.topSender();
        if (row == null) return null;
        Object emisorIdObj = row.get("emisor_id");
        Object cntObj = row.get("cnt");
        if (emisorIdObj == null) return null;
        Long emisorId = Long.valueOf(emisorIdObj.toString());
        Long cnt = cntObj == null ? 0L : Long.valueOf(cntObj.toString());
        com.proyecto.webmvc.model.Usuario usuario = usuarioDao.findById(emisorId);
        return java.util.Map.of("id", emisorId, "nombre", usuario.getNombreUsuario(), "cantidad", cnt);
    }
}
