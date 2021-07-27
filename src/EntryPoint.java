import master.Master;
import utils.AppConfig;
import utils.Utils;
import worker.WebServer;

import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

public class EntryPoint {
    public static void main(String[] args) {
        Utils.setupLogger("log.txt");
        AppConfig config = new AppConfig("appconfig.json");

        if (isThereLoadBalancer(config.loadbalancer)) {
            String[] workerArgs = {"-address", "0.0.0.0", "-port", "8000"};
            try {
                WebServer.main(workerArgs);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("[EntryPoint] worker failed");
            }
        } else {
            /*****
             *  Look at me.
             *  I am the captain now.
             *****/
            try {
                Master.MasterEntry(config);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("[EntryPoint] Master failed");
            }
        }
    }

    public static boolean isThereLoadBalancer(AppConfig.LoadBalancer loadbalancer) {
        try {
            System.out.println("[EntryPoint] checking if the load balancer is deployed...");
            URL baseUrl = new URL("http://" + loadbalancer.elasticIp + ":" + loadbalancer.port);
            URL target = new URL(baseUrl + "/test");
            HttpURLConnection conn = (HttpURLConnection) target.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                System.out.println("[EntryPoint] load balancer available: turning into worker");
                return true;
            }
        } catch (SocketTimeoutException | ConnectException e) {
            // all good
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("[EntryPoint] no load balancer available: becoming the captain");
        return false;
    }
}
