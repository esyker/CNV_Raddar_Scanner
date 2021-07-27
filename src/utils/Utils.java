package utils;

import com.sun.net.httpserver.Headers;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class Utils {
    public static void setupLogger(String file) {
        try {
            System.out.println("[Utils] starting logging to file: " + file);
            System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(file)), true));
        } catch (Exception e) {
            throw new RuntimeException("Failed setting up logger");
        }
    }

    public static void sleep(double secs) {
        try {
            Thread.sleep((long) secs * 1000);
        } catch (InterruptedException e) {
            // wait
        }
    }

    public static void setHeaders(Headers headers, String fileExtension) {
        headers.add("Content-Type", "image/" + fileExtension);
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Credentials", "true");
        headers.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
    }
}
