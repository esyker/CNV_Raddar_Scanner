package master;


import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import java.util.*;
import java.util.concurrent.Executors;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import master.cluster.Cluster;
import org.apache.commons.io.IOUtils;
import utils.DynamoConfig;

import javax.imageio.ImageIO;
import javax.rmi.CORBA.Util;

import utils.*;

public class LoadBalancer extends Thread {

    ServerArgumentParser sap = null;
    private final DynamoConfig dynamoDB = new DynamoConfig();
    private final Cluster cluster = Cluster.get();
    private int requestsServed = 0;
    private final int maxAttempts;
    private final double minSimilarity;

    public enum instrumentationRequirement {
        FULL,
        HALF,
        NONE,
    }

    public LoadBalancer(AppConfig.LoadBalancer config) {
        maxAttempts = config.maxAttempts;
        minSimilarity = config.minSimilarity;

        try {
            // Get user-provided flags.
            String[] args = {"-address", "0.0.0.0", "-port", config.port};
            sap = new ServerArgumentParser(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        final HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(sap.getServerAddress(), sap.getServerPort()), 0);

            server.createContext("/test", new HealthCheck());
            server.createContext("/scan", new RouteRequest());
            server.createContext("/log", new LogRequest());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println("[LoadBalancer] online on " + server.getAddress());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Estimates the complexity of the request
     * and reserves a worker accordingly.
     * Currently using the lowest used worker.
     * */
    String reserveWorker(double estimatedLoad) {
        /*
         * In case the estimated load is bigger than
         * the maximum allowed, we'll tone the estimation
         * done so it fits in a unit.
         * Preferably, in an almost empty one.
         */
        estimatedLoad = Math.min(estimatedLoad, cluster.getMaxUnitLoad() * 0.99);
        cluster.addWaitingLoad(estimatedLoad);
        while (true) {
            // getAvailable always returns a unit
            System.out.println("[LoadBalancer] waiting to load " + estimatedLoad);
            for (String unitId : cluster.getAvailableUnitsSorted()) {
                if (cluster.getUnitLoad(unitId) + estimatedLoad < cluster.getMaxUnitLoad()) {
                    cluster.freeWaitingLoad(estimatedLoad);
                    return cluster.reserveUnit(unitId, estimatedLoad);
                }
            }
            /*
             * wait for either a unit to unload or
             * for the autoscaler to take action
             */
            Utils.sleep(5);
        }
    }


    public double estimateLoad(DeserializedParameters params) {
        double estimatedLoad = scanLoad(params);
        /*
         * Previous recorded queries weren't
         * sufficiently satisfactory.
         * Therefore, it is better to use heuristics.
         */
        if (estimatedLoad == -1) {
            System.out.println("[LoadBalancer] no similar requests found. Using heuristic...");
            estimatedLoad = estimateLoadHeuristic(params);
        }
        return estimatedLoad;
    }


    /**
     * Could be used for different scanning
     * endpoints differing on instrumentation intensity.
     **/
    String determineScanType(DeserializedParameters params) {
        return "scan";
    }

    double estimateLoadHeuristic(DeserializedParameters params) {
        double search_area = (params.x1 - params.x0) * (params.y1 - params.y0);
        double estimatedLoad;
        /*
         * These values were obtained using linear regressions
         * on collected data
         * */
        switch (params.strategy) {
            case "GRID_SCAN":
                estimatedLoad = search_area * 391.96 + -6068305.35;
                if (estimatedLoad < 0)
                    estimatedLoad = 1e6;
                break;
            case "PROGRESSIVE_SCAN":
                estimatedLoad = search_area * 4.55 + 409230.34;
                break;
            case "GREEDY_RANGE_SCAN":
                estimatedLoad = search_area * 3.14 + 195721.04;
                break;
            default:
                throw new IllegalArgumentException("[LoadBalancer] unknown strategy: " + params.strategy);
        }

        return estimatedLoad;
    }

    private static class SimilarityEntry implements Comparable<SimilarityEntry> {
        private final double similarity;
        private final long methodCount;

        public SimilarityEntry(double similarity, long dyn_method_count) {
            this.similarity = similarity;
            this.methodCount = dyn_method_count;
        }

        // getters
        @Override
        public int compareTo(SimilarityEntry other) {
            return Double.compare(this.similarity, other.similarity);
        }

        public double getSimilarity() {
            return similarity;
        }

        public long getMethodCount() {
            return methodCount;
        }
    }

    public PriorityQueue<SimilarityEntry> getSimilarityQ(DeserializedParameters params) {
        List<Map<String, AttributeValue>> result = dynamoDB.read("instrumentation", params).getItems();
        PriorityQueue<SimilarityEntry> q = new PriorityQueue<>();
        for (Map<String, AttributeValue> entry : result) {
            long methodCount = Long.parseLong(entry.get("dyn_method_count").getN());
            double xS = Double.parseDouble(entry.get("xS").getN());
            double yS = Double.parseDouble(entry.get("yS").getN());
            double area = Double.parseDouble(entry.get("area").getN());

            if (isQueryEqual(entry, params)) {
                System.out.println("[LoadBalancer] identical request found");
                q.clear();
                q.add(new SimilarityEntry(1, methodCount));
                return q;
            } else {
                double similarity = relativeQuerySimilarity(xS, yS, area, params);
                q.add(new SimilarityEntry(similarity, methodCount));
            }
        }

        return q;
    }

    public double getWeightedAverageLoad(PriorityQueue<SimilarityEntry> q) {
        double totalWeightedLoad = 0;
        double totalSimilarity = 0;
        int i;

        for (i = 0; i < 3 && q.size() > 0; i++) {
            SimilarityEntry entry = q.poll();
            double weight = entry.getSimilarity();
            totalWeightedLoad += entry.getMethodCount() * weight;
            totalSimilarity += weight;
        }

        double averageSimilarity = totalSimilarity / i; //normal average of the similarities
        if (averageSimilarity < minSimilarity)  // In case the most similar queries are not that similar
            return -1;

        double averageWeightedLoad = totalWeightedLoad / totalSimilarity; //weighted average
        System.out.println("[LoadBalancer] weighted average method count: " + averageWeightedLoad +
                " with average similarity " + averageSimilarity);
        //compute the average between
        return averageWeightedLoad;
    }

    boolean isQueryEqual(Map<String, AttributeValue> entry, DeserializedParameters params) {
        double x0 = Double.parseDouble(entry.get("x0").getN());
        double y0 = Double.parseDouble(entry.get("y0").getN());
        double xS = Double.parseDouble(entry.get("xS").getN());
        double yS = Double.parseDouble(entry.get("yS").getN());
        double area = Double.parseDouble(entry.get("area").getN());

        return params.xS == xS
                && params.yS == yS
                && params.area == area
                && params.x0 == x0
                && params.y0 == y0;
    }

    double relativeQuerySimilarity(double xS, double yS, double area, DeserializedParameters params) {
        double areaSimilarity = Math.abs(params.area - area) / params.width / params.height;
        double xS_similarity = Math.abs(params.xS - xS) / params.width;
        double yS_similarity = Math.abs(params.yS - yS) / params.height;
        return 1 - (3 * areaSimilarity + xS_similarity + yS_similarity) / 5;
    }

    /**
     * Estimates the complexity of the request
     * using a machine learning model
     */
    double scanLoad(DeserializedParameters params) {
        try {
            PriorityQueue<SimilarityEntry> q = getSimilarityQ(params);
            if (q.size() < 1) return -1;
            return getWeightedAverageLoad(q);
        } catch (Exception e) {
            System.out.println("[LoadBalancer] problem scanning load with dynamo: " + e.getMessage());
            return -1;
        }
    }

    class HealthCheck implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            /*
             @see https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpExchange.html#sendResponseHeaders-int-long-
             If response length has the value -1 then no response body is being sent.

             @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Status
             200 is ok
             */
            t.sendResponseHeaders(200, -1);
        }
    }

    class RouteRequest implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            System.out.println("[LoadBalancer] incoming scan request number " + requestsServed);
            requestsServed++;
            // Get the query.
            String query = t.getRequestURI().getQuery();
            // System.out.println(query);

            String[] args;
            DeserializedParameters params;
            /*
             * Try to process the request
             */
            double estimatedLoad;
            String scanEndpoint;
            try {
                args = deserializeQuery(query);
                params = new DeserializedParameters(args);
                estimatedLoad = estimateLoad(params);
                scanEndpoint = determineScanType(params);
            } catch (Exception e) {
                e.printStackTrace();
                failedRequest(t, "processing_error.jpg", 400);
                return;
            }

            String unitId = null;
            URL baseUrl, targetUrl;

            int attemptNumber = 0;
            while (attemptNumber < maxAttempts) {
                try {
                    unitId = reserveWorker(estimatedLoad);
                    baseUrl = cluster.getUnitAddress(unitId);
                    targetUrl = new URL(baseUrl, scanEndpoint + "?" + query);
                    System.out.println("[LoadBalancer] rerouting to url: " + targetUrl);
                    URLConnection conn = targetUrl.openConnection();
                    conn.setReadTimeout(0);
                    conn.setConnectTimeout(10);

                    BufferedImage result = ImageIO.read(conn.getInputStream());
                    ByteArrayOutputStream tmp = new ByteArrayOutputStream();
                    ImageIO.write(result, "png", tmp);
                    tmp.close();

                    // Send response to browser.
                    Utils.setHeaders(t.getResponseHeaders(), "png");
                    t.sendResponseHeaders(200, tmp.size());
                    final OutputStream os = t.getResponseBody();
                    os.write(tmp.toByteArray());
                    os.close();

                    System.out.println("[LoadBalancer] sent response to " + t.getRemoteAddress().toString());
                    cluster.freeUnitLoad(unitId, estimatedLoad);
                    return;
                } catch (Exception e) {
                    attemptNumber++;
                    cluster.freeUnitLoad(unitId, estimatedLoad);
                    e.printStackTrace();
                    System.out.println("[LoadBalancer] tried serving the request number " + requestsServed + " " + attemptNumber + " time(s)");
                    System.out.println("[LoadBalancer] health checking instance " + unitId + " before trying again");

                    /*
                     * If the unit is Ok, we'll try again.
                     * If not, we mark it as unresponsive and
                     * as such, the load balancer will only direct
                     * traffic to it after the autoscaler has deemed
                     * it healthy again.
                     * If that doesn't happen for 1 minute
                     * it will be terminated.
                     * */
                    if (!cluster.healthyUnit(unitId, false)) {
                        if (cluster.getUnitState(unitId) != UnitState.UNLOADING)
                            cluster.setUnitState(unitId, UnitState.UNRESPONSIVE);
                    }
                }
            }
            failedRequest(t, "drone_crash.gif", 503);
        }
    }

    public void failedRequest(final HttpExchange t, String imgPath, int code) throws IOException {
        System.out.println("[LoadBalancer] request failed, warning client with " + imgPath);
        String fileExtension = imgPath.split("\\.")[1];
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        try {
            File imgFile = new File("master/assets/" + imgPath);
            InputStream img = new FileInputStream(imgFile);
            IOUtils.copy(img, tmp);
            tmp.close();
        } catch (IOException e) {
            System.out.println("[LoadBalancer] couldn't load:" + e.getMessage());
            System.out.println("[LoadBalancer] failed sending 'failed request' to client " + t.getRemoteAddress().toString());
            t.sendResponseHeaders(500, -1);
            return;
        }

        // Send response to browser.
        Utils.setHeaders(t.getResponseHeaders(), fileExtension);
        t.sendResponseHeaders(code, tmp.size());
        final OutputStream os = t.getResponseBody();
        os.write(tmp.toByteArray());
        os.close();
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


    public String[] deserializeQuery(String query) {
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

        return args;
    }
}