package utils;

import org.json.simple.DeserializationException;
import org.json.simple.JsonObject;
import org.json.simple.Jsoner;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class AppConfig {
    /**
     * Minimum time to wait before a newly responding
     * worker can be marked for termination.
     * This allows the load balancer to have time
     * to direct traffic to it.
     * => 50 secs
     */

    public final AutoScaler autoscaler;
    public final LoadBalancer loadbalancer;
    public final Cluster cluster;

    public static class AutoScaler {
        public final double mainLoopPeriod;

        public final double clusterOverLoadThreshold;
        public final double clusterUnderLoadThreshold;

        public final double unitOverLoadThreshold;
        public final double unitUnderLoadThreshold;


        public final int unitFailedPingsThreshold;
        public final int unitStartupCountThreshold;

        public AutoScaler(JsonObject autoscalerJson) {
            mainLoopPeriod = parseDouble(autoscalerJson, "mainLoopPeriod");

            clusterOverLoadThreshold = parseDouble(autoscalerJson, "clusterOverLoadThreshold");
            clusterUnderLoadThreshold = parseDouble(autoscalerJson, "clusterUnderLoadThreshold");

            unitOverLoadThreshold = parseDouble(autoscalerJson, "unitOverLoadThreshold");
            unitUnderLoadThreshold = parseDouble(autoscalerJson, "unitUnderLoadThreshold");

            unitFailedPingsThreshold = parseInt(autoscalerJson, "unitFailedPingsThreshold");
            unitStartupCountThreshold = parseInt(autoscalerJson, "unitStartupCountThreshold");
        }
    }

    public static class LoadBalancer {
        public final int maxAttempts;
        public final double minSimilarity;
        public final String elasticIp;
        public final String port;

        public LoadBalancer(JsonObject loadbalancerJson) {
            maxAttempts = parseInt(loadbalancerJson, "maxAttempts");
            minSimilarity = parseDouble(loadbalancerJson, "minSimilarity");
            elasticIp = parseString(loadbalancerJson, "elasticIp");
            port = parseString(loadbalancerJson, "port");
        }
    }

    public static class Cluster {
        public final String credentialsFile;
        public final String elasticAllocationId;
        public final String imageId;
        public final String securityGroup;
        public final int minSize;
        public final int maxSize;
        public final double maxUnitLoad;


        public Cluster(JsonObject clusterJson) {
            credentialsFile = parseString(clusterJson, "credentialsFile");
            elasticAllocationId = parseString(clusterJson, "elasticAllocationId");
            imageId = parseString(clusterJson, "imageId");
            securityGroup = parseString(clusterJson, "securityGroup");
            minSize = parseInt(clusterJson, "minSize");
            maxSize = parseInt(clusterJson, "maxSize");
            maxUnitLoad = parseDouble(clusterJson, "maxUnitLoad");
        }
    }

    public AppConfig(String configPath) {
        System.out.println("[AppConfig] loading");
        Reader configFile = readConfig(configPath);
        JsonObject configJson = deserializeFile(configFile);

        loadbalancer = new LoadBalancer((JsonObject) configJson.get("loadbalancer"));
        autoscaler = new AutoScaler((JsonObject) configJson.get("autoscaler"));
        cluster = new Cluster((JsonObject) configJson.get("cluster"));
        System.out.println("[AppConfig] loaded successfully");
    }

    public Reader readConfig(String configPath) {
        try {
            return new FileReader(configPath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("[AppConfig] config not found: " + configPath);
        }
    }

    public JsonObject deserializeFile(Reader jsonFile) {
        try {
            return (JsonObject) Jsoner.deserialize(jsonFile);
        } catch (DeserializationException | IOException e) {
            throw new RuntimeException("[AppConfig] can't deserialize JSON: " + jsonFile, e);
        }
    }

    static public String parseString(JsonObject obj, String field) {
        return obj.get(field).toString();
    }

    static public int parseInt(JsonObject obj, String field) {
        return Integer.parseInt(obj.get(field).toString());
    }

    static public double parseDouble(JsonObject obj, String field) {
        return Double.parseDouble(obj.get(field).toString());
    }
}
