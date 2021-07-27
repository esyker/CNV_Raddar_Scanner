package utils;

import java.io.File;
import java.io.IOException;

public class DeserializedParameters {
    public String strategy;
    public String mapType;
    public long width, height;
    public long x0, y0;
    public long x1, y1;
    public long xS, yS;
    public double area;

    public DeserializedParameters(String[] args) throws IOException {
        strategy = args[17];
        switch (strategy) {
            case "GRID_SCAN":
            case "PROGRESSIVE_SCAN":
            case "GREEDY_RANGE_SCAN":
                break;
            default:
                throw new IllegalArgumentException("[DeserializedParameters] unknown strategy: " + strategy);
        }

        mapType = args[19];
        if (!new File(mapType).isFile())
            throw new IllegalArgumentException("[DeserializedParameters] requested file doesn't exist: " + mapType);
        mapType = mapType.substring(mapType.lastIndexOf("/") + 1, mapType.indexOf("."));

        try {
            width = Long.parseLong(args[1]);
            height = Long.parseLong(args[3]);
            x0 = Long.parseLong(args[5]);
            x1 = Long.parseLong(args[7]);
            y0 = Long.parseLong(args[9]);
            y1 = Long.parseLong(args[11]);
            xS = Long.parseLong(args[13]);
            yS = Long.parseLong(args[15]);
        } catch (NumberFormatException e) {
            System.out.println("[DeserializedParameters] " + e.getMessage());
            throw new IOException("[DeserializedParameters] bad input");
        }
        area = Math.abs((x1 - x0) * (y1 - y0));
    }
}