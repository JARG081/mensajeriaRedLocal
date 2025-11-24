DROP TABLE IF EXISTS mensajes;
DROP TABLE IF EXISTS archivos;
DROP TABLE IF EXISTS sesiones;
DROP TABLE IF EXISTS usuarios;

CREATE TABLE IF NOT EXISTS usuarios (
  id BIGINT NOT NULL PRIMARY KEY,
  nombre_usuario VARCHAR(100) NOT NULL UNIQUE,
  contrasena_hash VARCHAR(512) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sesiones (
  id CHAR(36) NOT NULL PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  token VARCHAR(1024),
  ip VARCHAR(45) NOT NULL,
  fecha_inicio DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  fecha_fin DATETIME(6),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
  INDEX idx_sesiones_usuario_ip (usuario_id, ip),
  CONSTRAINT fk_sesiones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS archivos (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  propietario_id BIGINT,
  nombre VARCHAR(1000) NOT NULL,
  ruta VARCHAR(2000) NOT NULL,
  tipo_mime VARCHAR(255),
  tamano BIGINT,
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_archivos_propietario (propietario_id),
  CONSTRAINT fk_archivos_propietario FOREIGN KEY (propietario_id) REFERENCES usuarios(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mensajes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  emisor_id BIGINT NOT NULL,
  receptor_id BIGINT NOT NULL,
  tipo ENUM('TEXTO','ARCHIVO') NOT NULL DEFAULT 'TEXTO',
  contenido TEXT,
  archivo_id BIGINT,
  sesion_id CHAR(36),
  creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_mensajes_emisor_receptor_creado (emisor_id, receptor_id, creado_en),
  INDEX idx_mensajes_receptor_creado (receptor_id, creado_en),
  INDEX idx_mensajes_sesion (sesion_id),
  CONSTRAINT fk_mensajes_emisor FOREIGN KEY (emisor_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_mensajes_receptor FOREIGN KEY (receptor_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_mensajes_archivo FOREIGN KEY (archivo_id) REFERENCES archivos(id) ON DELETE SET NULL,
  CONSTRAINT fk_mensajes_sesion FOREIGN KEY (sesion_id) REFERENCES sesiones(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
