package master.cluster;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.util.EC2MetadataUtils;
import utils.AppConfig;
import utils.UnitState;
import utils.Utils;
import worker.WebServer;

import java.util.Collections;

public class Cluster {
    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */


    /**
     * Singleton instance pertaining to the APP cluster.
     *
     * @see "https://www.geeksforgeeks.org/singleton-class-java/"
     **/
    private static Cluster cluster = null;
    /**
     * Stores AWS instances and their information
     */
    final ConcurrentHashMap<String, Unit> units = new ConcurrentHashMap<>();
    /**
     * Heap ordered instances in terms of their
     * current load for efficient Load Balancing
     */
    final PriorityQueue<Unit> unitsPriorityQueue = new PriorityQueue<>();
    /**
     * Minimum time to wait before a newly responding
     * worker can be marked for termination.
     * This allows the load balancer to have time
     * to direct traffic to it.
     * => 50 secs
     */

    AmazonEC2 ec2;
    AmazonCloudWatch cloudWatch;
    final AppConfig.Cluster config;
    final String clusterId;
    double waitingLoad;


    public static void startUp(AppConfig.Cluster config) {
        if (cluster == null) {
            cluster = new Cluster(config);
            // minimum one unit working
            cluster.newUnits(config.minSize);
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    System.out.println("[Cluster] Shutdown hook engaged");
                    cluster.cleanup();
                }
            });
        }
    }

    /**
     * @return cluster singleton
     */
    public static Cluster get() {
        return cluster;
    }

    public void newUnits(int num) {
        System.out.println("[Cluster] starting " + num + " unit(s)");
        /* IDEA:
         * The autoscaler might want to create more than one unit
         * at the time. For example, if you have 1 unit, creating another
         * will halve the workload. But if you have 10 units, one
         * more will barely make a difference. So we might want to
         * create units proportional to the size of the cluster,
         * for example, 1/4 ?
         * */
        for (int i = 0; i < num; i++) {
            new Unit(this);
        }
    }

    /**
     * Get all unit ids for iteration
     */
    public Set<String> getUnitIds() {
        return units.keySet();
    }

    /**
     * Cluster size, aka, number of units
     */
    public int size() {
        return units.size();
    }

    /**
     * excludes unloading
     */
    public int effectiveSize() {
        int size = 0;
        for (String id : getUnitIds()) {
            if (getUnitState(id) != UnitState.UNLOADING)
                size++;
        }
        return size;
    }

    public int minimumSize() {
        return config.minSize;
    }

    public int maximumSize() {
        return config.maxSize;
    }

    public double getMaxUnitLoad() {
        return config.maxUnitLoad;
    }

    /**
     * sorted by descending load
     */
    public List<String> getAvailableUnitsSorted() {
        List<String> sortedUnits = new ArrayList<>();

        while (true) {
            for (Unit unit : unitsPriorityQueue) {
                if (unit.isSane()) {
                    sortedUnits.add(unit.getId());
                }
            }
            if (sortedUnits.size() > 0) {
                return sortedUnits;
            } else {
                System.out.println("[Cluster] waiting for units to be available...");
                Utils.sleep(2.5);
            }
        }
    }

    /**
     * sorted by ascending load
     */
    public List<String> getUnitsSorted() {
        List<String> sortedUnits = new ArrayList<>();

        for (Unit unit : unitsPriorityQueue) {
            sortedUnits.add(unit.getId());
        }

        Collections.reverse(sortedUnits);
        return sortedUnits;
    }

    /*
     * Unit interfacing methods
     * */
    public String reserveUnit(String id, double estimatedLoad) {
        Unit unit = units.get(id);
        unitsPriorityQueue.remove(unit);
        unit.addLoad(estimatedLoad);
        System.out.println("[Cluster] " + id + " loaded with " + estimatedLoad);
        System.out.println("[Cluster] current load " + unit.getLoad());
        unitsPriorityQueue.add(unit);
        return id;
    }

    public int getUnitRequests(String id) {
        return units.get(id).getNumberRequests();
    }

    public double getUnitLoad(String id) {
        return units.get(id).getLoad();
    }

    public double setUnitLoad(String id, double load) {
        return units.get(id).setLoad(load);
    }

    public double addUnitLoad(String id, double estimatedLoad) {
        return units.get(id).addLoad(estimatedLoad);
    }

    public void freeUnitLoad(String id, double estimatedLoad) {
        System.out.println("[Cluster] freeing load of unit " + id);
        System.out.println("[Cluster] current load " + units.get(id).freeLoad(estimatedLoad));
    }

    public UnitState getUnitState(String id) {
        return units.get(id).getState();
    }

    public UnitState setUnitState(String id, UnitState state) {
        System.out.println("[Cluster] unit " + id + " set to state " + state.toString());
        return units.get(id).setState(state);
    }

    public int getUnitFailedPings(String id) {
        return units.get(id).getFailedPings();
    }

    public int incUnitFailedPings(String id) {
        return units.get(id).incFailedPings();
    }

    public void resetUnitFailedPings(String id) {
        units.get(id).resetFailedPings();
    }

    public String getUnitIp(String id) {
        return units.get(id).getIp();
    }

    public int getUnitStartupCount(String id) {
        return units.get(id).getStartupCount();
    }

    public int incUnitStartupCount(String id) {
        return units.get(id).incStartupCount();
    }

    public void resetUnitStartupCount(String id) {
        units.get(id).resetStartupCount();
    }

    public double getUnitCpu(String id) {
        return units.get(id).getCpu();
    }

    public boolean isUnitSane(String id) {
        return units.get(id).isSane();
    }


    public URL getUnitAddress(String id) {
        return units.get(id).getUrl();
    }

    public void stopUnit(String id) {
        Unit unit = units.remove(id);
        unitsPriorityQueue.remove(unit);
        unit.stop();
        System.out.println("[Cluster] unit " + id + " stopped");
    }

    /*
     * Master <-> Worker communication methods
     **/
    public boolean healthyUnit(String id, boolean silent) {
        try {
            URL target = new URL(getUnitAddress(id), "test");
            HttpURLConnection conn = (HttpURLConnection) target.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            if (conn.getResponseCode() == 200) {
                if (!silent) System.out.println("[Cluster] unit " + id + " reachable");
                return true;
            }
        } catch (IOException e) {
            if (!silent)
                e.printStackTrace();
        }
        if (!silent)
            System.out.println("[Cluster] unit " + id + " might be unreachable");
        return false;
    }

    /*
     * AWS wrappers
     */
    private Cluster(AppConfig.Cluster loadedConfig) {
        config = loadedConfig;

        AWSCredentials credentials;
        try {
            ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider(".aws/" + config.credentialsFile, "default");
            credentials = credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "[Cluster] Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        clusterId = EC2MetadataUtils.getInstanceId();
        waitingLoad = 0;
        associateElasticAddress(config.elasticAllocationId);
        System.out.println("[Cluster] successfully deployed");
    }

    public void associateElasticAddress(String elasticId) {
        try {
            AssociateAddressRequest associateRequest =
                    new AssociateAddressRequest()
                            .withAllowReassociation(false)
                            .withInstanceId(clusterId)
                            .withAllocationId(elasticId);

            AssociateAddressResult associate_response =
                    ec2.associateAddress(associateRequest);
        } catch (AmazonServiceException e) {
            System.out.println("[Cluster] problem associating elastic IP");
            System.out.println("[Cluster] " + e.getMessage());
            System.out.println("[Cluster] falling back to becoming a worker...");
            cleanup();
            String[] workerArgs = {"-address", "0.0.0.0", "-port", "8000"};
            try {
                WebServer.main(workerArgs);
            } catch (Exception exception) {
                throw new RuntimeException("[Cluster] becoming worker fallback failed");
            }
        }
    }

    /**
     * Used when the unit is UNRESPONSIVE
     * for too long.
     */
    public void rebootUnit(String id) {
        try {
            units.get(id).reboot();
        } catch (Exception e) {
            System.out.println("[Cluster] problem rebooting unit " + id);
            System.out.println("[Cluster] " + e.getMessage());
            bootUnit(id);
        }
    }

    public void bootUnit(String id) {
        System.out.println("[Cluster] booting unit " + id);
        try {
            units.get(id).boot();
        } catch (Exception e) {
            System.out.println("[Cluster] problem booting unit " + id);
            System.out.println("[Cluster] " + e.getMessage());
            System.out.println("[Cluster] substituting unit");
            stopUnit(id);
            newUnits(1);
        }
    }

    public Date observationMinutes(double minutes) {
        double obsTime = 1000 * 60 * minutes;
        return new Date(new Date().getTime() - (long) obsTime);
    }

    public double addWaitingLoad(double estimatedLoad) {
        waitingLoad += estimatedLoad;
        return waitingLoad;
    }

    public double freeWaitingLoad(double estimatedLoad) {
        waitingLoad -= estimatedLoad;
        return waitingLoad;
    }

    public double estimatedLoad() {
        double totalLoad = waitingLoad;
        for (String id : getUnitIds()) {
            totalLoad += getUnitLoad(id);
        }
        return 100 * totalLoad / (config.maxUnitLoad * effectiveSize());
    }

    public double updateClusterCpuUsage() {
        double totalCpu = 0;
        try {
            for (String unitId : getUnitIds()) {
                Dimension instanceDimension = new Dimension();
                instanceDimension.setName("InstanceId");
                instanceDimension.setValue(unitId);

                GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                        .withStartTime(observationMinutes(2.1))
                        .withNamespace("AWS/EC2")
                        .withPeriod(60)
                        .withMetricName("CPUUtilization")
                        .withStatistics("Average")
                        .withDimensions(instanceDimension)
                        .withEndTime(new Date());

                GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);
                List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                if (datapoints.size() > 0) {
                    double averageCpu = datapoints.get(datapoints.size() - 1).getAverage();
                    System.out.println("[Cluster] " + unitId + " serving " + getUnitRequests(unitId) + " request(s) with CPU usage at: " + averageCpu);
                    totalCpu += averageCpu;

                    units.get(unitId).updateCpuUtilization(averageCpu);
                }
            }
        } catch (AmazonServiceException ase) {
            System.out.println("[Cluster]:");
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        } catch (Exception e) {
            System.out.println("[Cluster] exception getting CPU stats");
            System.out.println(e.getMessage());
        }

        return totalCpu / effectiveSize();
    }

    /**
     * Terminates all instances
     */
    public void cleanup() {
        TerminateInstancesRequest request = new TerminateInstancesRequest()
                .withInstanceIds(units.keySet());
        ec2.terminateInstances(request);
        System.out.println("[Cluster] all instances terminated");
    }
}
