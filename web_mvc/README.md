# web_mvc module

Mini módulo web MVC para consultar usuarios y mensajes.

Run

From repository root:

```powershell
cd web_mvc
.\mvnw spring-boot:run
```

Then open in your browser:

- Listado de usuarios: http://localhost:9300/web/usuarios
Additional pages after implementation:

- Mensajes usuario: http://localhost:9300/web/usuarios/{id}/mensajes
- Detalle mensaje: http://localhost:9300/web/mensajes/{id}
- Informes: http://localhost:9300/web/informes
- Usuarios conectados: http://localhost:9300/web/conectados
- Usuarios desconectados: http://localhost:9300/web/desconectados

Notes
- Uses the same MySQL database as other modules (check `application.properties`).
- Templates use Thymeleaf and Bootstrap via webjars.
