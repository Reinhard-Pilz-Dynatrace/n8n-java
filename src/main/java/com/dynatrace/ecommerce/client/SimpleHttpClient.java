package com.dynatrace.ecommerce.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Simple HTTP client using raw sockets
 * This bypasses Dynatrace OneAgent instrumentation of HttpURLConnection
 */
public class SimpleHttpClient {
    
    private final String host;
    private final int port;
    
    public SimpleHttpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    /**
     * Perform a GET request and return the response body
     */
    public String get(String path) throws Exception {
        try (Socket socket = new Socket(host, port)) {
            // Send HTTP GET request
            OutputStream out = socket.getOutputStream();
            String request = "GET " + path + " HTTP/1.1\r\n" +
                           "Host: " + host + "\r\n" +
                           "Connection: close\r\n" +
                           "\r\n";
            out.write(request.getBytes("UTF-8"));
            out.flush();
            
            // Read HTTP response
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            
            // Skip headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Skip HTTP headers
            }
            
            // Read body
            StringBuilder body = new StringBuilder();
            while ((line = in.readLine()) != null) {
                body.append(line).append("\n");
            }
            
            return body.toString();
        }
    }
}
