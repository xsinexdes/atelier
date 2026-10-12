package dev.atelier.pack;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny embedded HTTP server that serves the generated pack zip. */
final class PackHost {
    private HttpServer server;
    private ExecutorService executor;
    private volatile byte[] data = new byte[0];

    void setData(byte[] zip) { this.data = zip; }

    void start(int port) throws IOException {
        stop();
        executor = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "Atelier-PackHost"); t.setDaemon(true); return t; });
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/pack.zip", ex -> {
            byte[] d = data;
            ex.getResponseHeaders().add("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, d.length);
            try (var os = ex.getResponseBody()) { os.write(d); }
        });
        server.setExecutor(executor);
        server.start();
    }

    void stop() {
        if (server != null) { server.stop(0); server = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }
}
