package master;

import master.cluster.Cluster;
import utils.AppConfig;
import utils.ClusterState;
import utils.UnitState;
import utils.Utils;


public class AutoScaler extends Thread {

    Cluster cluster = Cluster.get();
    ClusterState clusterState;

    int unitsToUnload;
    int unitsToCreate;

    /*
     * Cluster state and change
     * thresholds as measured in %
     * */
    final double clusterOverLoadThreshold;

    final double clusterUnderLoadThreshold;

    double unitOverLoadThreshold;
    final double unitUnderLoadThreshold;

    final double mainLoopPeriod;
    /**
     * time before unit is rebooted =
     * mainLoopPeriod*unitFailedPingsThreshold
     */
    final int unitFailedPingsThreshold;
    final int unitStartupCountThreshold;


    public AutoScaler(AppConfig.AutoScaler config) {
        mainLoopPeriod = config.mainLoopPeriod;

        clusterOverLoadThreshold = config.clusterOverLoadThreshold;
        clusterUnderLoadThreshold = config.clusterUnderLoadThreshold;

        unitStartupCountThreshold = config.unitStartupCountThreshold;
        unitFailedPingsThreshold = config.unitFailedPingsThreshold;
        unitUnderLoadThreshold = config.unitUnderLoadThreshold;
        unitOverLoadThreshold = config.unitOverLoadThreshold;
    }

    public void run() {
        System.out.println("[AutoScaler] online");
        while (true) {
            updateClusterState();
            for (String id : cluster.getUnitsSorted()) {
                try {
                    evaluateUnit(id);
                } catch (Exception e) {
                    System.out.println("[AutoScaler] exception managing unit " + id);
                    System.out.println("[AutoScaler] " + e.getMessage());
                }
            }
            fixClusterOverload();
            Utils.sleep(mainLoopPeriod);
        }
    }

    public void evaluateUnit(String id) {
        switch (cluster.getUnitState(id)) {
            case CREATED:
                manageUnit_CREATED(id);
                break;
            case STARTUP:
                manageUnit_STARTUP(id);
                break;
            case RUNNING:
                manageUnit_RUNNING(id);
                break;
            case OVERLOADED:
                manageUnit_OVERLOADED(id);
                break;
            case UNLOADING:
                manageUnit_UNLOADING(id);
                break;
            case UNRESPONSIVE:
                manageUnit_UNRESPONSIVE(id);
                break;
            default:
                throw new IllegalStateException("[AutoScaler] unit " + id + " has invalid state " + cluster.getUnitState(id));
        }
    }


    public void updateClusterState() {
        double averageClusterCpu = cluster.updateClusterCpuUsage();
        double averageClusterLoad = cluster.estimatedLoad();
        System.out.println("[AutoScaler] average cluster CPU: " + averageClusterCpu);
        System.out.println("[AutoScaler] average cluster load: " + averageClusterLoad);
        double averageLoad = Math.max(averageClusterCpu, averageClusterLoad);
        if (isClusterOverLoaded(averageLoad)) {
            unitsToCreate = calculateClusterOverload(averageLoad);
            if (unitsToCreate > 0) {
                setClusterState(ClusterState.OVERLOADED);
                System.out.println("[AutoScaler] need " + unitsToCreate + " additional units");
            } else // safe guard
                setClusterState(ClusterState.NORMAL);
        } else if (isClusterUnderLoaded(averageLoad) && cluster.effectiveSize() > cluster.minimumSize()) {
            unitsToUnload = calculateClusterUnderload(averageLoad);
            if (unitsToUnload > 0) {
                setClusterState(ClusterState.UNDERLOADED);
                System.out.println("[AutoScaler] trying to unload " + unitsToUnload + " units");
            } else // safe guard
                setClusterState(ClusterState.NORMAL);
        } else {
            setClusterState(ClusterState.NORMAL);
        }
    }

    public int calculateClusterOverload(double averageLoad) {
        /*
         * Equation used:
         * totalCpu/(currSize + unitsToCreate) = 50% (desiredCpu)
         * solving for unitsToCreate:
         * unitsToCreate = currSize + totalCpu/50%
         * */
        double unitsToCreate = averageLoad * cluster.effectiveSize() / 50 - cluster.effectiveSize();
        /*
         * Cluster size can't be bigger than the
         * imposed maximum.
         */
        if (cluster.effectiveSize() + unitsToCreate > cluster.maximumSize())
            unitsToCreate = cluster.maximumSize() - cluster.effectiveSize();

        return (int) Math.round(unitsToCreate);
    }

    public void fixClusterOverload() {
        if (unitsToCreate > 0) {
            System.out.println("[AutoScaler] instantiating " + unitsToCreate + " new units");
            cluster.newUnits(unitsToCreate);
            setClusterState(ClusterState.NORMAL);
            unitsToCreate = 0;
        }
    }

    public int calculateClusterUnderload(double averageLoad) {
        /*
         * Equation used:
         * totalCpu/(currSize - unitsToUnload) = 50% (desiredCpu)
         * solving for unitsToUnload:
         * unitsToUnload = currSize - totalCpu/50%
         * */
        double unitsToUnload = cluster.effectiveSize() - averageLoad * cluster.effectiveSize() / 50;
        /*
         * Can't remove more units than the minimum
         */
        if (cluster.effectiveSize() - unitsToUnload < cluster.minimumSize())
            unitsToUnload = cluster.effectiveSize() - cluster.minimumSize();
        return (int) Math.floor(unitsToUnload);
    }

    public boolean isClusterOverLoaded(double averageLoad) {
        return averageLoad > clusterOverLoadThreshold;
    }

    public boolean isClusterUnderLoaded(double averageCpuUsage) {
        return averageCpuUsage < clusterUnderLoadThreshold;
    }

    public boolean isUnitOverLoaded(String id) {
        return cluster.getUnitCpu(id) > unitOverLoadThreshold;
    }

    public boolean isUnitUnderLoaded(String id) {
        return cluster.getUnitCpu(id) < unitUnderLoadThreshold;
    }

    public void manageUnit_CREATED(String id) {
        /*
         * Checks if AWS has attributed an ip
         * to the unit.
         * If so, we may enter the unit
         * creation grace period.
         */
        if (cluster.getUnitIp(id) != null) {
            pingAndReboot(id, "[AutoScaler] unit " + id + " failed creation");
        }
    }

    public void manageUnit_STARTUP(String id) {
        /*
         * No action taken as the unit is still
         * warming up.
         * This variable flags the autoscaler
         * that it shouldn't create another unit
         * just yet.
         */
        if (cluster.getUnitStartupCount(id) > unitStartupCountThreshold) {
            cluster.resetUnitStartupCount(id);
            cluster.setUnitState(id, UnitState.RUNNING);
        } else {
            cluster.incUnitStartupCount(id);
        }
    }


    public void manageUnit_RUNNING(String id) {
        if (isUnitOverLoaded(id)) {
            /*
             * Warns the load balancer
             * that the unit is already
             * consuming too much cpu.
             * */
            cluster.setUnitState(id, UnitState.OVERLOADED);
        } else if (isClusterState(ClusterState.UNDERLOADED) && isUnitUnderLoaded(id)) {
            /*
             * The load balancer won't direct more
             * traffic to this unit in order to let
             * it finish scanning before being terminated.
             */
            if (unitsToUnload >= 1) {
                cluster.setUnitState(id, UnitState.UNLOADING);
                unitsToUnload--;
            } else {
                setClusterState(ClusterState.NORMAL);
            }
        }
    }


    public void manageUnit_OVERLOADED(String id) {
        if (!isUnitOverLoaded(id))
            cluster.setUnitState(id, UnitState.RUNNING);
    }

    public void manageUnit_UNLOADING(String id) {
        /* UNLOADING unit finished all requests */
        if (isClusterState(ClusterState.OVERLOADED) && unitsToCreate > 0) {
            cluster.setUnitState(id, UnitState.RUNNING);
            unitsToCreate--;
            if (unitsToCreate < 1)
                setClusterState(ClusterState.NORMAL);
        } else if (cluster.getUnitRequests(id) == 0) {
            cluster.stopUnit(id);
        }
    }

    public void manageUnit_UNRESPONSIVE(String id) {
        pingAndReboot(id, "[AutoScaler] unit " + id + " failed to become responsive");
    }

    public ClusterState setClusterState(ClusterState state) {
        System.out.println("[AutoScaler] cluster " + state.toString());
        return clusterState = state;
    }

    public boolean isClusterState(ClusterState state) {
        return clusterState == state;
    }

    public void pingAndReboot(String id, String message) {
        if (cluster.healthyUnit(id, true)) {
            cluster.setUnitState(id, UnitState.STARTUP);
        } else if (cluster.getUnitFailedPings(id) >= unitFailedPingsThreshold) {
            System.out.println(message);
            cluster.setUnitState(id, UnitState.REBOOTING);
            cluster.rebootUnit(id);
            cluster.setUnitState(id, UnitState.CREATED);
            cluster.resetUnitFailedPings(id);
        } else {
            System.out.println("[AutoScaler] unit " + id + " failed " + cluster.incUnitFailedPings(id) + " pings");
        }
    }
}