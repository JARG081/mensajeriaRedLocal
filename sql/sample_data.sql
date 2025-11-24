START TRANSACTION;

MERGE INTO usuarios (id, nombre_usuario, contrasena_hash) VALUES
  (1, 'manuel', SHA2('1234', 256)),
  (2, 'laura', SHA2('1234', 256));

COMMIT;