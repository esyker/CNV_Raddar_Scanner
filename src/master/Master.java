package master;

import master.cluster.Cluster;
import utils.AppConfig;

public class Master {
    public static void MasterEntry(AppConfig config) {
        Cluster.startUp(config.cluster);
        Thread loadBalancer = new LoadBalancer(config.loadbalancer);
        Thread autoScaler = new AutoScaler(config.autoscaler);

        loadBalancer.start();
        autoScaler.start();
    }
}
