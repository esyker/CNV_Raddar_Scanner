package master.cluster;

import com.amazonaws.services.ec2.model.*;
import utils.UnitState;

import java.net.MalformedURLException;
import java.net.URL;

/* could also be private and allow the cluster to provide the interface */
class Unit implements Comparable<Unit> {
    private final Cluster cluster;
    /**
     * AWS attributed ID
     */
    private final String id;

    /* Useful storing the IP for informative messages */
    private String ip;
    /**
     * Instance url domain.
     * Computed just once from the IP
     * for lower overhead.
     */
    private URL url;

    /**
     * Number of requests this unit is serving
     * <p>
     * When in state UNLOADING && numberRequests == 0
     * unit state gets set to TERMINATE
     */
    private int numberRequests;

    /**
     * Estimated load based on request complexity.
     **/
    private double load = 0;

    /**
     * Reflects the current unit
     * lifecycle for AS/LB purposes
     */
    private UnitState state = UnitState.CREATED;

    /**
     * Number of failed health checks.
     */
    private int failedPings = 0;
    private int startupCount = 0;
    /**
     * in %
     */
    private double cpuUtilization;


    public Unit(Cluster cluster) {
        this.cluster = cluster;
        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(cluster.config.imageId)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withMonitoring(true)
                .withSecurityGroups(cluster.config.securityGroup);

        RunInstancesResult runInstancesResult =
                cluster.ec2.runInstances(runInstancesRequest);
        final Instance unit = runInstancesResult.getReservation().getInstances()
                .get(0);

        id = unit.getInstanceId();
        System.out.println("[Unit] " + id + " in state CREATED");
        cluster.units.put(id, this);
        cluster.unitsPriorityQueue.add(Unit.this);
    }

    void updateIp() {
        try {
            DescribeInstancesRequest req = new DescribeInstancesRequest().withInstanceIds(id);
            Instance instance = cluster.ec2.describeInstances(req).getReservations().get(0).getInstances().get(0);
            ip = instance.getPublicIpAddress();
        } catch (Exception e) {
            System.out.println("[Cluster] exception when updating IP of unit " + id);
            e.printStackTrace();
        }

        url = computeUrl("8000");
    }

    URL computeUrl(String port) {
        URL urL;
        try {
            url = new URL("http://" + this.ip + ":" + port + "/");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException("[Unit] problem computing url");
        }

        return url;
    }

    URL getUrl() {
        return url;
    }

    String getId() {
        return id;
    }

    String getIp() {
        if (ip == null)
            updateIp();
        return ip;
    }

    int getStartupCount() {
        return startupCount;
    }

    int incStartupCount() {
        return ++startupCount;
    }

    void resetStartupCount() {
        startupCount = 0;
    }

    int getFailedPings() {
        return failedPings;
    }

    int incFailedPings() {
        return ++failedPings;
    }

    void resetFailedPings() {
        failedPings = 0;
    }

    int getNumberRequests() {
        return numberRequests;
    }

    UnitState getState() {
        return state;
    }

    UnitState setState(UnitState state) {
        return this.state = state;
    }

    boolean isSane() {
        return state == UnitState.RUNNING
                || state == UnitState.STARTUP;
    }

    /**
     * Unit load getter
     *
     * @return load
     */
    double getLoad() {
        return this.load;
    }

    /**
     * Sets the unit load.
     * Used when the load balancer asks the
     * instance to update its load mid request.
     *
     * @param load
     * @return updated load
     */
    double setLoad(double load) {
        this.load = load;
        return this.load;
    }

    /**
     * Increases the load and number
     * of requests the unit is serving.
     *
     * @param estimatedLoad
     * @return increased load
     */
    double addLoad(double estimatedLoad) {
        this.load += estimatedLoad;
        this.numberRequests++;
        return this.load;
    }

    /**
     * Decreases the load and number
     * of requests the unit is serving.
     *
     * @param estimatedLoad
     * @return decreased load
     */
    double freeLoad(double estimatedLoad) {
        this.load -= estimatedLoad;
        if (this.load < 0)
            this.load = 0;
        this.numberRequests--;
        return this.load;
    }

    double getCpu() {
        return cpuUtilization;
    }

    double updateCpuUtilization(double cpuUtilization) {
        return this.cpuUtilization = cpuUtilization;
    }

    void boot() {
        StartInstancesRequest request = new StartInstancesRequest()
                .withInstanceIds(id);
        cluster.ec2.startInstances(request);
    }

    void reboot() {
        RebootInstancesRequest request = new RebootInstancesRequest()
                .withInstanceIds(id);
        cluster.ec2.rebootInstances(request);
    }

    void stop() {
        try {
            TerminateInstancesRequest request = new TerminateInstancesRequest()
                    .withInstanceIds(id);
            cluster.ec2.terminateInstances(request);
        } catch (Exception e) {
            System.out.println("[Unit] problem terminating unit " + id);
        }
    }

    @Override
    public int compareTo(Unit unit) {
        return Double.compare(unit.load, this.load);
    }
}
