package com.proyecto.demo;

import com.proyecto.demo.auth.FileAuthService;
import com.proyecto.demo.server.TcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import com.proyecto.demo.ui.UiServerWindow;

@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoApplication.class);

	@Autowired
	private Environment env;

	@Autowired(required = false)
	private TcpServer tcpServer;

	@Autowired(required = false)
	private FileAuthService fileAuthService;

	public static void main(String[] args) {
		try {
			java.nio.file.Path root = locateProjectRoot();
			if (root != null) {
				String usersAbs = root.resolve("usuarios.txt").toAbsolutePath().toString();
				String logAbs = root.resolve("servidor.log").toAbsolutePath().toString();
				// Set system properties so Spring will pick them up as overrides
				System.setProperty("auth.users.file", usersAbs);
				System.setProperty("logging.file.name", logAbs);
				System.out.println("Configuración absoluta aplicada: users=" + usersAbs + " log=" + logAbs);
			}
		} catch (Exception e) {
			System.err.println("No se pudo determinar project root: " + e.toString());
		}
		try (java.io.InputStream is = DemoApplication.class.getResourceAsStream("/application.properties")) {
			if (is != null) {
				var p = com.proyecto.demo.factory.ServerFactory.createProperties();
				p.load(is);
				String clear = p.getProperty("app.log.clearOnStartup", "false");
				if ("true".equalsIgnoreCase(clear)) {
					String logFile = System.getProperty("logging.file.name", p.getProperty("logging.file.name", "servidor.log"));
					try {
						java.io.File f = com.proyecto.demo.factory.ServerFactory.createFile(logFile);
						// Create if not exists
						if (!f.exists()) {
							if (f.getParentFile() != null) f.getParentFile().mkdirs();
							f.createNewFile();
						}
						try (java.io.RandomAccessFile raf = com.proyecto.demo.factory.ServerFactory.createRandomAccessFile(f, "rw")) {
							raf.setLength(0);
						}
						System.out.println("Log truncado en el arranque: " + f.getAbsolutePath());
					} catch (Exception ex) {
						System.err.println("No se pudo truncar el log al inicio: " + ex.toString());
					}
				}
			}
		} catch (Exception ignored) {}

		System.setProperty("java.awt.headless", System.getProperty("java.awt.headless", "false"));
		SpringApplication.run(DemoApplication.class, args);
	}

	private static java.nio.file.Path locateProjectRoot() {
		try {
			// Location of the running classes/jar
			java.net.URI uri = DemoApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			java.nio.file.Path p = java.nio.file.Paths.get(uri).toAbsolutePath().normalize();
			String name = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase();
			if (name.equals("classes") || name.equals("target")) {
				java.nio.file.Path candidate = p;
				if (name.equals("classes")) candidate = p.getParent();
				if (candidate != null && candidate.getParent() != null) return candidate.getParent();
			}
			if (!java.nio.file.Files.isDirectory(p)) {
				return p.getParent();
			}
			return p;
		} catch (Exception e) {
			try { return java.nio.file.Paths.get(".").toAbsolutePath().normalize(); } catch (Exception ex) { return null; }
		}
	}

	@Override
	public void run(String... args) throws Exception {
		String appName = env.getProperty("spring.application.name", "<unknown>");
		String host = env.getProperty("server.address", "0.0.0.0");
		String port = env.getProperty("server.port", "");
		String usersFile = env.getProperty("auth.users.file", "usuarios.txt");

		log.info("Aplicación '{}' iniciada. Server address={}, port={}", appName, host, port);
		log.info("Archivo de usuarios configurado: {}", usersFile);

		// Intentar inicializar la UI de servidor de forma determinista si está habilitada
		String uiEnabled = env.getProperty("server.ui.enabled", "true");
		if ("true".equalsIgnoreCase(uiEnabled)) {
			log.info("UI enabled property = true; intentando inicializar UI");
			try {
				UiServerWindow.initialize();
				log.info("UiServerWindow.initialize() invocado desde DemoApplication.run");
			} catch (Throwable t) {
				log.warn("No se pudo inicializar la UI en startup: {}", t.toString());
			}
		} else {
			log.info("UI deshabilitada por configuración (server.ui.enabled={})", uiEnabled);
		}

		if (tcpServer != null) {
			log.info("Componente TcpServer activo");
		} else {
			log.warn("Componente TcpServer no inyectado");
		}

		if (fileAuthService != null) {
			log.info("Servicio de autenticación activo");
		} else {
			log.warn("Servicio de autenticación no inyectado");
		}
	}

}
