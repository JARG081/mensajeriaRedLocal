
DROP TABLE IF EXISTS mensajes;
DROP TABLE IF EXISTS archivos;
DROP TABLE IF EXISTS sesiones;
DROP TABLE IF EXISTS usuarios;

CREATE TABLE IF NOT EXISTS usuarios (
  id BIGINT PRIMARY KEY,
  nombre_usuario VARCHAR(100) NOT NULL UNIQUE,
  contrasena_hash VARCHAR(512) NOT NULL
);

CREATE TABLE IF NOT EXISTS sesiones (
  id CHAR(36) PRIMARY KEY,
  usuario_id BIGINT NOT NULL,
  token VARCHAR(1024),
  ip VARCHAR(45) NOT NULL,
  fecha_inicio TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  fecha_fin TIMESTAMP(6),
  estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
  CONSTRAINT fk_sesiones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS archivos (
  id BIGSERIAL PRIMARY KEY,
  propietario_id BIGINT,
  nombre VARCHAR(1000) NOT NULL,
  ruta VARCHAR(2000) NOT NULL,
  tipo_mime VARCHAR(255),
  tamano BIGINT,
  creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_archivos_propietario FOREIGN KEY (propietario_id) REFERENCES usuarios(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS mensajes (
  id BIGSERIAL PRIMARY KEY,
  emisor_id BIGINT NOT NULL,
  receptor_id BIGINT NOT NULL,
  tipo VARCHAR(10) NOT NULL DEFAULT 'TEXTO' CHECK (tipo IN ('TEXTO','ARCHIVO')),
  contenido TEXT,
  archivo_id BIGINT,
  sesion_id CHAR(36),
  creado_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_mensajes_emisor FOREIGN KEY (emisor_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_mensajes_receptor FOREIGN KEY (receptor_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_mensajes_archivo FOREIGN KEY (archivo_id) REFERENCES archivos(id) ON DELETE SET NULL,
  CONSTRAINT fk_mensajes_sesion FOREIGN KEY (sesion_id) REFERENCES sesiones(id) ON DELETE SET NULL
);


SELECT * FROM usuarios;
SELECT * FROM sesiones;
SELECT * FROM archivos;
SELECT * FROM mensajes;
