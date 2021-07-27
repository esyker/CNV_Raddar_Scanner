package worker;// StatisticsTool.java
//
// This program measures and instruments to obtain different statistics
// about Java programs.
//
// Copyright (c) 1998 by Han B. Lee (hanlee@cs.colorado.edu).
// ALL RIGHTS RESERVED.
//
// Permission to use, copy, modify, and distribute this software and its
// documentation for non-commercial purposes is hereby granted provided
// that this copyright notice appears in all copies.
//
// This software is provided "as is".  The licensor makes no warrenties, either
// expressed or implied, about its correctness or performance.  The licensor
// shall not be liable for any damages suffered as a result of using
// and modifying this software.

import BIT.highBIT.BasicBlock;
import BIT.highBIT.ClassInfo;
import BIT.highBIT.Routine;
import bitsamples.StatisticsBranch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class StatisticsTool {
    private static final ConcurrentHashMap<Long, Long> methodCount = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> bbCount = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> instructionCount = new ConcurrentHashMap<>();

    private static StatisticsBranch[] branch_info;
    private static int branch_number;
    private static int branch_pc;
    private static String branch_class_name;
    private static String branch_method_name;

    private static final int INPUT = 0;
    private static final int OUTPUT = 1;

    public static void printUsage() {
        System.out.println("Syntax: java StatisticsTool -stat_type in_path [out_path]");
        System.out.println("        where stat_type can be:");
        System.out.println("        full:    ....");
        System.out.println("        half:    .... ");
        System.out.println("        little:    .... ");
        System.out.println("        all:    all of the above");
        System.out.println("        in_path:  directory from which the class files are read");
        System.out.println("        out_path: directory to which the class files are written");
        System.out.println("        Both in_path and out_path are required unless stat_type is static");
        System.out.println("        in which case only in_path is required");
        System.exit(-1);
    }

    public static void fullInstrumentation(String[] inputFiles, String[] outputFiles) {
        int numberOfFiles = inputFiles.length;
        for (int i = 0; i < numberOfFiles; i++) {
            String inFilename = inputFiles[i];
            String outFilename = outputFiles[i];
            ClassInfo ci = new ClassInfo(inFilename);
            System.out.println(inFilename);
            for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                Routine routine = (Routine) e.nextElement();

                routine.addBefore("worker/StatisticsTool", "dynMethodCount", 1);
                for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                    BasicBlock bb = (BasicBlock) b.nextElement();
                    bb.addBefore("worker/StatisticsTool", "dynInstrCount", bb.size());
                }
            }
            ci.addAfter("worker/StatisticsTool", "printDynamic", "null");
            ci.write(outFilename);
        }
    }

    public static void halfInstrumentation(String[] inputFiles, String[] outputFiles) {
        int numberOfFiles = inputFiles.length;
        for (int i = 0; i < numberOfFiles; i++) {
            String inFilename = inputFiles[i];
            String outFilename = outputFiles[i];
            ClassInfo ci = new ClassInfo(inFilename);
            for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                Routine routine = (Routine) e.nextElement();
                routine.addBefore("worker/StatisticsTool", "dynMethodCount", 1);
            }
            ci.addAfter("worker/StatisticsTool", "printDynamic", "null");
            ci.write(outFilename);
        }
    }

    public static void littleInstrumentation(String[] inputFiles, String[] outputFiles) {
        int numberOfFiles = inputFiles.length;
        for (int i = 0; i < numberOfFiles; i++) {
            String inFilename = inputFiles[i];
            String outFilename = outputFiles[i];
            ClassInfo ci = new ClassInfo(inFilename);
            for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                Routine routine = (Routine) e.nextElement();
                routine.addBefore("worker/StatisticsTool", "dynMethodCount", 1);
            }
            ci.addAfter("worker/StatisticsTool", "printDynamic", "null");
            ci.write(outFilename);
        }
    }

    public static synchronized void dynInstrCount(int incr) {
        Long key = Thread.currentThread().getId();
        instructionCount.put(key, instructionCount.get(key) + incr);
        bbCount.put(key, bbCount.get(key) + 1);
    }

    public static synchronized void dynMethodCount(int incr) {
        Long key = Thread.currentThread().getId();
        methodCount.put(key, methodCount.get(key) + 1);
    }

    public static void initializeKey(long Key, long value) {
        methodCount.put(Key, value);
        instructionCount.put(Key, value);
        bbCount.put(Key, value);
    }

    public static long getDynInstrCount(long key) {
        return instructionCount.get(key);

    }

    public static long getDynBBCount(long key) {
        return bbCount.get(key);
    }

    public static long getDynMethodCount(long key) {
        return methodCount.get(key);
    }

    public static void main(String[] argv) {
        if (argv.length != 3 || !argv[0].startsWith("-")) {
            printUsage();
        }
        String instrumentationType = argv[0].substring(1);

        File inDir = new File(argv[1]);
        if (instrumentationType.equals("all")) {
            File baseDir = new File(argv[2]);
            List<String> instrumentationTypes = Arrays.asList("full", "half", "little");
            for (String type : instrumentationTypes) {
                System.out.println("[Instrumentation] running '" + type + "' coverage");
                File subDir = new File(baseDir, type);
                if (!inDir.isDirectory() || !subDir.isDirectory())
                    printUsage();

                ArrayList<String> ioFilenames = getFilenames(inDir, "Scan");
                String[] inputFiles = getFiles(inDir, ioFilenames);
                String[] outputFiles = getFiles(subDir, ioFilenames);
                runInstrumentation(type, inputFiles, outputFiles);
            }
        } else {
            File outDir;
            if (instrumentationType.equals("normal")) {
                outDir = new File(argv[2] + System.getProperty("file.separator"));
                instrumentationType = "half";
            } else
                outDir = new File(argv[2] + System.getProperty("file.separator") + instrumentationType);

            if (!inDir.isDirectory() || !outDir.isDirectory())
                printUsage();

            ArrayList<String> ioFilenames = getFilenames(inDir, ".class");
            String[] inputFiles = getFiles(inDir, ioFilenames);
            String[] outputFiles = getFiles(outDir, ioFilenames);
            runInstrumentation(instrumentationType, inputFiles, outputFiles);
        }
    }

    public static void runInstrumentation(String type, String[] inputFiles, String[] outputFiles) {
        switch (type) {
            case "full":
                fullInstrumentation(inputFiles, outputFiles);
                break;
            case "half":
                halfInstrumentation(inputFiles, outputFiles);
                break;
            case "little":
                littleInstrumentation(inputFiles, outputFiles);
                break;
            default:
                throw new RuntimeException("[Instrumentation] unknown type");
        }
    }

    public static String[] getFiles(File inDir, ArrayList<String> filenames) {
        String[] inputFiles = new String[filenames.size()];
        int i = 0;
        for (String filename : filenames) {
            inputFiles[i] = inDir.getAbsolutePath() + System.getProperty("file.separator") + filename;
            i++;
        }
        return inputFiles;
    }


    public static ArrayList<String> getFilenames(File inDir, String keyWord) {
        try {
            String filelist[] = inDir.list();
            ArrayList<String> filenames = new ArrayList<>();
            for (int i = 0; i < filelist.length; i++) {
                String filename = filelist[i];
                if (filename.endsWith(keyWord)) {
                    filenames.add(filename);
                }
            }
            return filenames;
        } catch (NullPointerException e) {
            printUsage();
        }
        return null;
    }
}