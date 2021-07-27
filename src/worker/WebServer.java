package worker;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;

import java.util.concurrent.Executors;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.apache.commons.io.IOUtils;
import utils.*;
import org.json.simple.JsonArray;
import org.json.simple.JsonObject;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

import javax.imageio.ImageIO;

public class WebServer {
    private static List<Long> SolverThreadIds = new ArrayList<Long>();
    static ServerArgumentParser sap = null;
    private static DynamoConfig dynamoDB = new DynamoConfig();

    public static void main(final String[] args) throws Exception {
        try {
            // Get user-provided flags.
            WebServer.sap = new ServerArgumentParser(args);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        System.out.println("[Worker] Finished parsing Server args.");

        //final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8000), 0);

        final HttpServer server = HttpServer.create(new InetSocketAddress(WebServer.sap.getServerAddress(), WebServer.sap.getServerPort()), 0);


        server.createContext("/scan", new ScanRequest());

        server.createContext("/test", new HealthCheck());
        server.createContext("/log", new LogRequest());

        // be aware! infinite pool of threads!
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println(server.getAddress().toString());
    }


    static class HealthCheck implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            t.sendResponseHeaders(200, -1);
        }
    }

    static class LogRequest implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            try {
                File logFile = new File("log.txt");
                InputStream log = new FileInputStream(logFile);
                IOUtils.copy(log, tmp);
                tmp.close();
            } catch (IOException e) {
                System.out.println("[Worker] couldn't load:" + e.getMessage());
                System.out.println("[Worker] failed sending logs to " + t.getRemoteAddress().toString());
                t.sendResponseHeaders(500, -1);
                return;
            }
            t.sendResponseHeaders(200, tmp.size());
            final OutputStream os = t.getResponseBody();
            os.write(tmp.toByteArray());
            os.close();
        }
    }

    private static Map<String, AttributeValue> newItem(long dyn_method_count, String ts,
                                                       DeserializedParameters args) {

        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();

        System.out.println("[Worker] dyn_method_count: " + dyn_method_count);
        item.put("dyn_method_count", new AttributeValue().withN(Long.toString(dyn_method_count)));

        item.put("ts", new AttributeValue(ts));

        item.put("strategy", new AttributeValue(args.strategy));
        item.put("mapType", new AttributeValue(args.mapType));

        item.put("w", new AttributeValue().withN(Double.toString(args.width)));
        item.put("h", new AttributeValue().withN(Double.toString(args.height)));
        item.put("x0", new AttributeValue().withN(Double.toString(args.x0)));
        item.put("x1", new AttributeValue().withN(Double.toString(args.x1)));
        item.put("y0", new AttributeValue().withN(Double.toString(args.y0)));
        item.put("y1", new AttributeValue().withN(Double.toString(args.y1)));
        item.put("xS", new AttributeValue().withN(Double.toString(args.xS)));
        item.put("yS", new AttributeValue().withN(Double.toString(args.yS)));
        item.put("area", new AttributeValue().withN(Double.toString(Math.abs((args.x1 - args.x0) * (args.y1 - args.y0)))));
        return item;
    }

    public static void insertItem(String[] args_) {
        long key = Thread.currentThread().getId();

        long dyn_method_count = StatisticsTool.getDynMethodCount(key);

        String ts = new Timestamp(System.currentTimeMillis()).toString();
        DeserializedParameters args;
        try {
            args = new DeserializedParameters(args_);
        } catch (IOException e) {
            System.out.println("[Worker] " + e.getMessage());
            ;
            return;
        }

        Map<String, AttributeValue> item = newItem(dyn_method_count,
                ts, args);

        dynamoDB.insert("instrumentation", item);
        System.out.println("[" + ts + " - WebServer] Number of methods: " + dyn_method_count);
    }

    static class ScanRequest implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            long key = Thread.currentThread().getId();
            SolverThreadIds.add(key);
            StatisticsTool.initializeKey(Thread.currentThread().getId(), 0);

            // Get the query.
            final String[] args = deserializeQuery(t.getRequestURI().getQuery());

            // Create solver instance from factory.
            final Solver s = SolverFactory.getInstance().makeSolver(args);
            if (s == null) {
                System.out.println("[Worker] problem creating Solver \n" +
                        "Exiting");
                t.sendResponseHeaders(500, -1);
                return;
            }

            // Write figure file to disk.
            File responseFile;
            try {
                final BufferedImage outputImg = s.solveImage();
                final String outPath = WebServer.sap.getOutputDirectory();
                final String imageName = s.toString();

                final Path imagePathPNG = Paths.get(outPath, imageName);
                ImageIO.write(outputImg, "png", imagePathPNG.toFile());

                responseFile = imagePathPNG.toFile();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("[Worker] failed scan");
            }

            // Send response to browser.
            Utils.setHeaders(t.getResponseHeaders(), "png");
            t.sendResponseHeaders(200, responseFile.length());

            final OutputStream os = t.getResponseBody();
            Files.copy(responseFile.toPath(), os);
            os.close();

            System.out.println("[Worker] responded to " + t.getRemoteAddress().toString());
            insertItem(args);
            SolverThreadIds.remove(key);
        }
    }

    static public String[] deserializeQuery(String query) {
        // Break it down into String[].
        final String[] params = query.split("&");
        // Store as if it was a direct call to SolverMain.
        final ArrayList<String> newArgs = new ArrayList<>();
        for (final String p : params) {
            final String[] splitParam = p.split("=");

            if (splitParam[0].equals("i")) {
                splitParam[1] = sap.getMapsDirectory() + "/" + splitParam[1];
            }

            newArgs.add("-" + splitParam[0]);
            newArgs.add(splitParam[1]);
        }
        if (sap.isDebugging()) {
            newArgs.add("-d");
        }
        // Store from ArrayList into regular String[].
        final String[] args = new String[newArgs.size()];
        int i = 0;
        for (String arg : newArgs) {
            args[i] = arg;
            i++;
        }
        System.out.println("[Worker] finished parsing args");

        return args;
    }
}

