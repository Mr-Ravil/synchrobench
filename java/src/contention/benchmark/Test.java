package contention.benchmark;

import contention.abstractions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Formatter;
import java.util.Locale;
import java.util.Random;

import contention.benchmark.ThreadLoops.*;
import contention.benchmark.ThreadLoops.workloads.*;
import contention.benchmark.keygenerators.builders.*;
import contention.benchmark.keygenerators.parameters.*;

/**
 * Synchrobench-java, a benchmark to evaluate the implementations of
 * high level abstractions including Map and Set.
 *
 * @author Vincent Gramoli
 */
public class Test {

    public static final String VERSION = "11-17-2014";

    public enum Type {
        INTSET, MAP, SORTEDSET
    }

    /**
     * The array of threads executing the benchmark
     */
    private Thread[] threads;
    /**
     * The array of runnable thread codes
     */
    private ThreadLoop[] threadLoops;
    /**
     * The observed duration of the benchmark
     */
    private double elapsedTime;
    /**
     * The throughput
     */
    private double[] throughput = null;
    /**
     * The iteration
     */
    private int currentIteration = 0;

    /**
     * The total number of operations for all threads
     */
    private long total = 0;
    /**
     * The total number of successful operations for all threads
     */
    private long numAdd = 0;
    private long numRemove = 0;
    private long numAddAll = 0;
    private long numRemoveAll = 0;
    private long numSize = 0;
    private long numContains = 0;
    /**
     * The total number of failed operations for all threads
     */
    private long failures = 0;
    /**
     * The total number of aborts
     */
    private long aborts = 0;
    /**
     * The instance of the benchmark
     */
    private Type benchType = null;
    private Parameters parameters;
    private KeyGeneratorBuilder keyGeneratorBuilder;
    private CompositionalIntSet setBench = null;
    private CompositionalSortedSet<Integer> sortedBench = null;
    private CompositionalMap<Integer, Integer> mapBench = null;
    /**
     * The instance of the benchmark
     */
    /**
     * The benchmark methods
     */
    private Method[] methods;

    private long nodesTraversed;
    public long structMods;
    private long getCount;

    /**
     * The thread-private PRNG
     */
    final private static ThreadLocal<Random> s_random = new ThreadLocal<Random>() {
        @Override
        protected synchronized Random initialValue() {
            return new Random();
        }
    };

    //TODO delete parameters
    public void fill(final int range, final long size) throws InterruptedException {
        Thread[] prefillThreads = new Thread[parameters.numPrefillThreads];
        for (int threadNum = 0; threadNum < parameters.numPrefillThreads; threadNum++) {
            final int finalThreadNum = threadNum;
            prefillThreads[threadNum] = new Thread(() -> threadLoops[finalThreadNum].prefill());
        }

        for (Thread thread : prefillThreads)
            thread.start();

        for (Thread thread : prefillThreads)
            thread.join();

    }


    /**
     * Initialize the benchmark
     *
     * @param benchName the class name of the benchmark
     * @return the instance of the initialized corresponding benchmark
     */
    @SuppressWarnings("unchecked")
    public void instanciateAbstraction(String benchName) {
        try {
            Class<CompositionalMap<Integer, Integer>> benchClass = (Class<CompositionalMap<Integer, Integer>>) Class
                    .forName(benchName);
            Constructor<CompositionalMap<Integer, Integer>> c = benchClass
                    .getConstructor();
            methods = benchClass.getDeclaredMethods();

            if (CompositionalIntSet.class.isAssignableFrom((Class<?>) benchClass)) {
                setBench = (CompositionalIntSet) c.newInstance();
                benchType = Type.INTSET;
            } else if (CompositionalMap.class.isAssignableFrom((Class<?>) benchClass)) {
                mapBench = (CompositionalMap<Integer, Integer>) c.newInstance();
                benchType = benchType != null ? benchType : Type.MAP;
            } else if (CompositionalSortedSet.class.isAssignableFrom((Class<?>) benchClass)) {
                sortedBench = (CompositionalSortedSet<Integer>) c.newInstance();
                benchType = Type.SORTEDSET;
            }

        } catch (Exception e) {
            System.err.println("Cannot find benchmark class: " + benchName);
            System.exit(-1);
        }
    }


    /**
     * Creates as many threads as requested
     */
    private void initThreads() {
        KeyGenerator[] keygens = keyGeneratorBuilder.generateKeyGenerators(parameters);

        threads = new Thread[parameters.numThreads];
        threadLoops = new ThreadLoop[parameters.numThreads];

        for (short threadNum = 0; threadNum < parameters.numThreads; threadNum++) {

            threadLoops[threadNum] = switch (benchType) {
                case INTSET -> new ThreadSetLoop(threadNum, setBench, methods, keygens[threadNum], parameters);
                case MAP -> switch (parameters.workloadType) {
                    case REGULAR -> new ThreadMapLoop(threadNum, mapBench, methods, keygens[threadNum], parameters);
                    case DELETE_SPEED_TEST -> new DeleteSpeedTest(threadNum, mapBench, methods, parameters);
                    case DELETE_LEAFS -> new DeleteLeafsWorkload(threadNum, mapBench, methods, parameters);
                };
                case SORTEDSET ->
                        new ThreadSortedSetLoop(threadNum, sortedBench, methods, keygens[threadNum], parameters);

            };

            threads[threadNum] = new Thread(threadLoops[threadNum]);
        }
    }

    /**
     * Constructor sets up the benchmark by reading parameters and creating
     * threads
     *
     * @param args the arguments of the command-line
     */
    public Test(String[] args) throws InterruptedException {
        printHeader();
        try {
            parseCommandLineParameters(args);
        } catch (Exception e) {
            System.err.println("Cannot parse parameters.");
            e.printStackTrace();
        }
        instanciateAbstraction(parameters.benchClassName);
        this.throughput = new double[parameters.iterations];
    }

    /**
     * Execute the main thread that starts and terminates the benchmark threads
     *
     * @throws InterruptedException
     */
    private void execute(int milliseconds, boolean maint) throws InterruptedException {
        long startTime;
        fill(parameters.range, parameters.size);
        Thread.sleep(parameters.afterFillRelaxMilliseconds);

        startTime = System.currentTimeMillis();
        for (Thread thread : threads)
            thread.start();
        try {
            Thread.sleep(milliseconds);
        } finally {
            for (ThreadLoop threadLoop : threadLoops)
                threadLoop.stopThread();
        }
        for (Thread thread : threads)
            thread.join();

        long endTime = System.currentTimeMillis();
        elapsedTime = ((double) (endTime - startTime)) / 1000.0;
    }

    public void clear() {
        switch (benchType) {
            case INTSET -> setBench.clear();
            case MAP -> mapBench.clear();
            case SORTEDSET -> sortedBench.clear();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        boolean firstIteration = true;
        Test test = new Test(args);
        test.printParams();

        // warming up the JVM
        if (test.parameters.warmUp != 0) {
            try {
                test.initThreads();
            } catch (Exception e) {
                System.err.println("Cannot launch operations.");
                e.printStackTrace();
            }
            test.execute(test.parameters.warmUp * 1000, true);
            // give time to the JIT
            Thread.sleep(100);
            if (test.parameters.detailedStats)
                test.recordPreliminaryStats();
            test.clear();
            test.resetStats();
        }

        // check that the structure is empty
        switch (test.benchType) {
            case INTSET:
                assert test.setBench.size() == 0 : "Warmup corrupted the data structure, rerun with -W 0.";
            case MAP:
                assert test.mapBench.size() == 0 : "Warmup corrupted the data structure, rerun with -W 0.";
            case SORTEDSET:
                assert test.sortedBench.size() == 0 : "Warmup corrupted the data structure, rerun with -W 0.";
        }

        // running the bench
        for (int i = 0; i < test.parameters.iterations; i++) {
            if (!firstIteration) {
                // give time to the JIT
                Thread.sleep(100);
                test.resetStats();
                test.clear();
                org.deuce.transaction.estmstats.Context.threadIdCounter.set(0);
            }
            try {
                test.initThreads();
            } catch (Exception e) {
                System.err.println("Cannot launch operations.");
                e.printStackTrace();
            }
            test.execute(test.parameters.numMilliseconds, false);

            if (test.setBench instanceof MaintenanceAlg) {
                ((MaintenanceAlg) test.setBench).stopMaintenance();
                test.structMods += ((MaintenanceAlg) test.setBench)
                        .getStructMods();
            }
            if (test.mapBench instanceof MaintenanceAlg) {
                ((MaintenanceAlg) test.mapBench).stopMaintenance();
                test.structMods += ((MaintenanceAlg) test.mapBench)
                        .getStructMods();
            }
            if (test.sortedBench instanceof MaintenanceAlg) {
                ((MaintenanceAlg) test.sortedBench).stopMaintenance();
                test.structMods += ((MaintenanceAlg) test.sortedBench)
                        .getStructMods();
            }

            test.printBasicStats();
            if (test.parameters.detailedStats)
                test.printDetailedStats();

            firstIteration = false;
            test.currentIteration++;
        }

        if (test.parameters.iterations > 1) {
            test.printIterationStats();
        }
    }

    /* ---------------- Input/Output -------------- */

    /**
     * Parse the parameters on the command line
     */
    private void parseCommandLineParameters(String[] args) {

        int startArgNumber = 1;
        switch (args[0]) {
            case "--help", "-h" -> {
                printUsage();
                System.exit(0);
            }
            case "-skewed-sets" -> {
                keyGeneratorBuilder = new SkewedSetsKeyGeneratorBuilder();

                parameters = new SkewedSetsParameters();
                parameters.keygenType = KeyGeneratorType.SKEWED_SETS;
            }
            case "-creakers-and-wave" -> {
                keyGeneratorBuilder = new CreakersAndWaveKeyGeneratorBuilder();

                parameters = new CreakersAndWaveParameters();
                parameters.keygenType = KeyGeneratorType.CREAKERS_AND_WAVE;
            }
            case "-temporary-skewed", "-temp-skewed" -> {
                keyGeneratorBuilder = new TemporarySkewedKeyGeneratorBuilder();

                parameters = new TemporarySkewedParameters();
                parameters.keygenType = KeyGeneratorType.TEMPORARY_SKEWED;
            }
            case "-delete-speed-test" -> {
                keyGeneratorBuilder = new KeyGeneratorBuilder();

                parameters = new Parameters();
                parameters.workloadType = WorkloadType.DELETE_SPEED_TEST;
                parameters.keygenType = KeyGeneratorType.NONE;
                parameters.numMilliseconds = 0;
            }
            case "-delete-leafs" -> {
                keyGeneratorBuilder = new KeyGeneratorBuilder();

                parameters = new Parameters();
                parameters.workloadType = WorkloadType.DELETE_LEAFS;
                parameters.keygenType = KeyGeneratorType.NONE;
            }
            case "-leaf-insert" -> {
                keyGeneratorBuilder = new LeafInsertKeyGeneratorBuilder();

                parameters = new Parameters();
                parameters.keygenType = KeyGeneratorType.LEAF_INSERT;
            }
            case "-leafs-handshake" -> {
                keyGeneratorBuilder = new LeafsHandshakeKeyGeneratorBuilder();

                parameters = new LeafsHandshakeParameters();
                parameters.keygenType = KeyGeneratorType.LEAFS_HANDSHAKE;
            }
            case "-leafs-extension-handshake" -> {
                keyGeneratorBuilder = new LeafsExtensionHandshakeKeyGeneratorBuilder();

                parameters = new LeafsHandshakeParameters();
                parameters.keygenType = KeyGeneratorType.LEAFS_EXTENSION_HANDSHAKE;
            }
            default -> {
                keyGeneratorBuilder = new SimpleKeyGeneratorBuilder();

                parameters = new SimpleParameters();
                parameters.keygenType = KeyGeneratorType.SIMPLE_KEYGEN;
                startArgNumber = 0;
            }
        }

//        keyGeneratorBuilder.parse(args);

        parameters.parse(args, startArgNumber);

        assert (parameters.range >= parameters.size);
        if (parameters.range != 2 * parameters.size)
            System.err
                    .println("Note that the value range is not twice "
                            + "the initial size, thus the size expectation varies at runtime.");
    }

    /**
     * Print a 80 character line filled with the same marker character
     *
     * @param ch the marker character
     */
    private void printLine(char ch) {
        StringBuffer line = new StringBuffer(79);
        for (int i = 0; i < 79; i++)
            line.append(ch);
        System.out.println(line);
    }

    /**
     * Print the header message on the standard output
     */
    private void printHeader() {
        String header = "Synchrobench-java\n"
                + "A benchmark-suite to evaluate synchronization techniques";
        printLine('-');
        System.out.println(header);
        printLine('-');
        System.out.println();
    }

    /**
     * Print the benchmark usage on the standard output
     */
    private void printUsage() {
        String syntax = "Usage:\n"
                + "java synchrobench.benchmark.Test [options] [-- stm-specific options]\n\n"
                + "Options:\n"
                + "\t-v            -- print detailed statistics (default: "
                + parameters.detailedStats
                + ")\n"
                + "\t-t thread-num -- set the number of threads (default: "
                + parameters.numThreads
                + ")\n"
                + "\t-d duration   -- set the length of the benchmark, in milliseconds (default: "
                + parameters.numMilliseconds
                + ")\n"
                + "\t-u updates    -- set the number of threads (default: "
                + parameters.numWrites
                + ")\n"
                + "\t-a writeAll   -- set the percentage of composite updates (default: "
                + parameters.numWriteAlls
                + ")\n"
                + "\t-s snapshot   -- set the percentage of composite read-only operations (default: "
                + parameters.numSnapshots
                + ")\n"
                + "\t-r range      -- set the element range (default: "
                + parameters.range
                + ")\n"
                + "\t-b benchmark  -- set the benchmark (default: "
                + parameters.benchClassName
                + ")\n"
                + "\t-i size       -- set the datastructure initial size (default: "
                + parameters.size
                + ")\n"
                + "\t-n iterations -- set the bench iterations in the same JVM (default: "
                + parameters.iterations
                + ")\n"
                + "\t-W warmup     -- set the JVM warmup length, in seconds (default: "
                + parameters.warmUp + ").";
        System.err.println(syntax);
    }

    /**
     * Print the parameters that have been given as an input to the benchmark
     */
    private void printParams() {
        String params = parameters.toStringBuilder().toString();
        System.out.println(params);
    }

    /**
     * Print the statistics on the standard output
     */
    private void printBasicStats() {
        int finalSize = 0;
        for (short threadNum = 0; threadNum < parameters.numThreads; threadNum++) {
            numAdd += threadLoops[threadNum].getNumAdd();
            numRemove += threadLoops[threadNum].getNumRemove();
            numAddAll += threadLoops[threadNum].getNumAddAll();
            numRemoveAll += threadLoops[threadNum].getNumRemoveAll();
            numSize += threadLoops[threadNum].getNumSize();
            numContains += threadLoops[threadNum].getNumContains();
            failures += threadLoops[threadNum].getFailures();
            total += threadLoops[threadNum].getTotal();
            aborts += threadLoops[threadNum].getAborts();
            getCount += threadLoops[threadNum].getGetCount();
            nodesTraversed += threadLoops[threadNum].getNodesTraversed();
            structMods += threadLoops[threadNum].getStructMods();
        }
        throughput[currentIteration] = ((double) total / elapsedTime);
        printLine('-');
        System.out.println("Benchmark statistics");
        printLine('-');
        System.out.println("  Average traversal length: \t"
                + (double) nodesTraversed / (double) getCount);
        System.out.println("  Struct Modifications:     \t" + structMods);
        System.out.println("  Throughput (ops/s):       \t" + throughput[currentIteration]);
        System.out.println("  Elapsed time (s):         \t" + elapsedTime);
        System.out.println("  Operations:               \t" + total
                + "\t( 100 %)");
        System.out.println("    effective updates:     \t"
                + (numAdd + numRemove + numAddAll + numRemoveAll)
                + "\t( "
                + formatDouble(((double) (numAdd + numRemove
                + numAddAll + numRemoveAll) * 100)
                / (double) total) + " %)");
        System.out.println("    |--add successful:     \t" + numAdd + "\t( "
                + formatDouble(((double) numAdd / (double) total) * 100)
                + " %)");
        System.out.println("    |--remove succ.:       \t" + numRemove + "\t( "
                + formatDouble(((double) numRemove / (double) total) * 100)
                + " %)");
        System.out.println("    |--addAll succ.:       \t" + numAddAll + "\t( "
                + formatDouble(((double) numAddAll / (double) total) * 100)
                + " %)");
        System.out.println("    |--removeAll succ.:    \t" + numRemoveAll
                + "\t( "
                + formatDouble(((double) numRemoveAll / (double) total) * 100)
                + " %)");
        System.out.println("    size successful:       \t" + numSize + "\t( "
                + formatDouble(((double) numSize / (double) total) * 100)
                + " %)");
        System.out.println("    contains succ.:        \t" + numContains
                + "\t( "
                + formatDouble(((double) numContains / (double) total) * 100)
                + " %)");
        System.out.println("    unsuccessful ops:      \t" + failures + "\t( "
                + formatDouble(((double) failures / (double) total) * 100)
                + " %)");
        finalSize = switch (benchType) {
            case INTSET -> setBench.size();
            case MAP -> mapBench.size();
            case SORTEDSET -> sortedBench.size();
        };
        System.out.println("  Final size:              \t" + finalSize);
        assert finalSize == (parameters.size + numAdd - numRemove) : "Final size does not reflect the modifications.";

        //System.out.println("  Otherupdate_bin.bat size:              \t" + map.size());

        // TODO what should print special for maint data structures
        // if (bench instanceof CASSpecFriendlyTreeLockFree) {
        // System.out.println("  Balance:              \t"
        // + ((CASSpecFriendlyTreeLockFree) bench).getBalance());
        // }
        // if (bench instanceof SpecFriendlyTreeLockFree) {
        // System.out.println("  Balance:              \t"
        // + ((SpecFriendlyTreeLockFree) bench).getBalance());
        // }
        // if (bench instanceof TransFriendlyMap) {
        // System.out.println("  Balance:              \t"
        // + ((TransFriendlyMap) bench).getBalance());
        // }
        // if (bench instanceof UpdatedTransFriendlySkipList) {
        // System.out.println("  Bottom changes:              \t"
        // + ((UpdatedTransFriendlySkipList) bench)
        // .getBottomLevelRaiseCount());
        // }

        switch (benchType) {
            case INTSET -> {
                if (setBench instanceof MaintenanceAlg) {
                    System.out.println("  #nodes (inc. deleted): \t"
                            + ((MaintenanceAlg) setBench).numNodes());
                }
            }
            case MAP -> {
                if (mapBench instanceof MaintenanceAlg) {
                    System.out.println("  #nodes (inc. deleted): \t"
                            + ((MaintenanceAlg) mapBench).numNodes());
                }
            }
            case SORTEDSET -> {
                if (mapBench instanceof MaintenanceAlg) {
                    System.out.println("  #nodes (inc. deleted): \t"
                            + ((MaintenanceAlg) sortedBench).numNodes());
                }
            }
        }

    }

    /**
     * Detailed Warmup TM Statistics
     */
    private int numCommits = 0;
    private int numStarts = 0;
    private int numAborts = 0;

    private int numCommitsReadOnly = 0;
    private int numCommitsElastic = 0;
    private int numCommitsUpdate = 0;

    private int numAbortsBetweenSuccessiveReads = 0;
    private int numAbortsBetweenReadAndWrite = 0;
    private int numAbortsExtendOnRead = 0;
    private int numAbortsWriteAfterRead = 0;
    private int numAbortsLockedOnWrite = 0;
    private int numAbortsLockedBeforeRead = 0;
    private int numAbortsLockedBeforeElasticRead = 0;
    private int numAbortsLockedOnRead = 0;
    private int numAbortsInvalidCommit = 0;
    private int numAbortsInvalidSnapshot = 0;

    private double readSetSizeSum = 0.0;
    private double writeSetSizeSum = 0.0;
    private int statSize = 0;
    private int txDurationSum = 0;
    private int elasticReads = 0;
    private int readsInROPrefix = 0;

    /**
     * This method is called between two runs of the benchmark within the same
     * JVM to enable its warmup
     */
    public void resetStats() {

        for (short threadNum = 0; threadNum < parameters.numThreads; threadNum++) {
            threadLoops[threadNum].setNumAdd(0);
            threadLoops[threadNum].setNumRemove(0);
            threadLoops[threadNum].setNumAddAll(0);
            threadLoops[threadNum].setNumRemoveAll(0);
            threadLoops[threadNum].setNumSize(0);
            threadLoops[threadNum].setNumContains(0);
            threadLoops[threadNum].setFailures(0);
            threadLoops[threadNum].setTotal(0);
            threadLoops[threadNum].setAborts(0);
            threadLoops[threadNum].setNodesTraversed(0);
            threadLoops[threadNum].setGetCount(0);
            threadLoops[threadNum].setStructMods(0);
        }
        numAdd = 0;
        numRemove = 0;
        numAddAll = 0;
        numRemoveAll = 0;
        numSize = 0;
        numContains = 0;
        failures = 0;
        total = 0;
        aborts = 0;
        nodesTraversed = 0;
        getCount = 0;
        structMods = 0;

        numCommits = 0;
        numStarts = 0;
        numAborts = 0;

        numCommitsReadOnly = 0;
        numCommitsElastic = 0;
        numCommitsUpdate = 0;

        numAbortsBetweenSuccessiveReads = 0;
        numAbortsBetweenReadAndWrite = 0;
        numAbortsExtendOnRead = 0;
        numAbortsWriteAfterRead = 0;
        numAbortsLockedOnWrite = 0;
        numAbortsLockedBeforeRead = 0;
        numAbortsLockedBeforeElasticRead = 0;
        numAbortsLockedOnRead = 0;
        numAbortsInvalidCommit = 0;
        numAbortsInvalidSnapshot = 0;

        readSetSizeSum = 0.0;
        writeSetSizeSum = 0.0;
        statSize = 0;
        txDurationSum = 0;
        elasticReads = 0;
        readsInROPrefix = 0;
    }

    public void recordPreliminaryStats() {
        numAborts = Statistics.getTotalAborts();
        numCommits = Statistics.getTotalCommits();
        numCommitsReadOnly = Statistics.getNumCommitsReadOnly();
        numCommitsElastic = Statistics.getNumCommitsElastic();
        numCommitsUpdate = Statistics.getNumCommitsUpdate();
        numStarts = Statistics.getTotalStarts();
        numAbortsBetweenSuccessiveReads = Statistics
                .getNumAbortsBetweenSuccessiveReads();
        numAbortsBetweenReadAndWrite = Statistics
                .getNumAbortsBetweenReadAndWrite();
        numAbortsExtendOnRead = Statistics.getNumAbortsExtendOnRead();
        numAbortsWriteAfterRead = Statistics.getNumAbortsWriteAfterRead();
        numAbortsLockedOnWrite = Statistics.getNumAbortsLockedOnWrite();
        numAbortsLockedBeforeRead = Statistics.getNumAbortsLockedBeforeRead();
        numAbortsLockedBeforeElasticRead = Statistics
                .getNumAbortsLockedBeforeElasticRead();
        numAbortsLockedOnRead = Statistics.getNumAbortsLockedOnRead();
        numAbortsInvalidCommit = Statistics.getNumAbortsInvalidCommit();
        numAbortsInvalidSnapshot = Statistics.getNumAbortsInvalidSnapshot();
        readSetSizeSum = Statistics.getSumReadSetSize();
        writeSetSizeSum = Statistics.getSumWriteSetSize();

        statSize = Statistics.getStatSize();
        txDurationSum = Statistics.getSumCommitingTxTime();
        elasticReads = Statistics.getTotalElasticReads();
        readsInROPrefix = Statistics.getTotalReadsInROPrefix();
    }

    /**
     * Print the detailed statistics on the standard output
     */
    private void printDetailedStats() {

        numCommits = Statistics.getTotalCommits() - numCommits;
        numStarts = Statistics.getTotalStarts() - numStarts;
        numAborts = Statistics.getTotalAborts() - numAborts;

        numCommitsReadOnly = Statistics.getNumCommitsReadOnly()
                - numCommitsReadOnly;
        numCommitsElastic = Statistics.getNumCommitsElastic()
                - numCommitsElastic;
        numCommitsUpdate = Statistics.getNumCommitsUpdate() - numCommitsUpdate;

        numAbortsBetweenSuccessiveReads = Statistics
                .getNumAbortsBetweenSuccessiveReads()
                - numAbortsBetweenSuccessiveReads;
        numAbortsBetweenReadAndWrite = Statistics
                .getNumAbortsBetweenReadAndWrite()
                - numAbortsBetweenReadAndWrite;
        numAbortsExtendOnRead = Statistics.getNumAbortsExtendOnRead()
                - numAbortsExtendOnRead;
        numAbortsWriteAfterRead = Statistics.getNumAbortsWriteAfterRead()
                - numAbortsWriteAfterRead;
        numAbortsLockedOnWrite = Statistics.getNumAbortsLockedOnWrite()
                - numAbortsLockedOnWrite;
        numAbortsLockedBeforeRead = Statistics.getNumAbortsLockedBeforeRead()
                - numAbortsLockedBeforeRead;
        numAbortsLockedBeforeElasticRead = Statistics
                .getNumAbortsLockedBeforeElasticRead()
                - numAbortsLockedBeforeElasticRead;
        numAbortsLockedOnRead = Statistics.getNumAbortsLockedOnRead()
                - numAbortsLockedOnRead;
        numAbortsInvalidCommit = Statistics.getNumAbortsInvalidCommit()
                - numAbortsInvalidCommit;
        numAbortsInvalidSnapshot = Statistics.getNumAbortsInvalidSnapshot()
                - numAbortsInvalidSnapshot;

        assert (numAborts == (numAbortsBetweenSuccessiveReads
                + numAbortsBetweenReadAndWrite + numAbortsExtendOnRead
                + numAbortsWriteAfterRead + numAbortsLockedOnWrite
                + numAbortsLockedBeforeRead + numAbortsLockedBeforeElasticRead
                + numAbortsLockedOnRead + numAbortsInvalidCommit + numAbortsInvalidSnapshot));

        assert (numStarts - numAborts) == numCommits;

        readSetSizeSum = Statistics.getSumReadSetSize() - readSetSizeSum;
        writeSetSizeSum = Statistics.getSumWriteSetSize() - writeSetSizeSum;
        statSize = Statistics.getStatSize() - statSize;
        txDurationSum = Statistics.getSumCommitingTxTime() - txDurationSum;

        printLine('-');
        System.out.println("TM statistics");
        printLine('-');

        System.out.println("  Commits:                  \t" + numCommits);
        System.out
                .println("  |--regular read only  (%) \t"
                        + numCommitsReadOnly
                        + "\t( "
                        + formatDouble(((double) numCommitsReadOnly / (double) numCommits) * 100)
                        + " %)");
        System.out
                .println("  |--elastic (%)            \t"
                        + numCommitsElastic
                        + "\t( "
                        + formatDouble(((double) numCommitsElastic / (double) numCommits) * 100)
                        + " %)");
        System.out
                .println("  |--regular update (%)     \t"
                        + numCommitsUpdate
                        + "\t( "
                        + formatDouble(((double) numCommitsUpdate / (double) numCommits) * 100)
                        + " %)");
        System.out.println("  Starts:                   \t" + numStarts);
        System.out.println("  Aborts:                   \t" + numAborts
                + "\t( 100 %)");
        System.out
                .println("  |--between succ. reads:   \t"
                        + (numAbortsBetweenSuccessiveReads)
                        + "\t( "
                        + formatDouble(((double) (numAbortsBetweenSuccessiveReads) * 100)
                        / (double) numAborts) + " %)");
        System.out
                .println("  |--between read & write:  \t"
                        + numAbortsBetweenReadAndWrite
                        + "\t( "
                        + formatDouble(((double) numAbortsBetweenReadAndWrite / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--extend upon read:      \t"
                        + numAbortsExtendOnRead
                        + "\t( "
                        + formatDouble(((double) numAbortsExtendOnRead / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--write after read:      \t"
                        + numAbortsWriteAfterRead
                        + "\t( "
                        + formatDouble(((double) numAbortsWriteAfterRead / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--locked on write:       \t"
                        + numAbortsLockedOnWrite
                        + "\t( "
                        + formatDouble(((double) numAbortsLockedOnWrite / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--locked before read:    \t"
                        + numAbortsLockedBeforeRead
                        + "\t( "
                        + formatDouble(((double) numAbortsLockedBeforeRead / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--locked before eread:   \t"
                        + numAbortsLockedBeforeElasticRead
                        + "\t( "
                        + formatDouble(((double) numAbortsLockedBeforeElasticRead / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--locked on read:        \t"
                        + numAbortsLockedOnRead
                        + "\t( "
                        + formatDouble(((double) numAbortsLockedOnRead / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--invalid commit:        \t"
                        + numAbortsInvalidCommit
                        + "\t( "
                        + formatDouble(((double) numAbortsInvalidCommit / (double) numAborts) * 100)
                        + " %)");
        System.out
                .println("  |--invalid snapshot:      \t"
                        + numAbortsInvalidSnapshot
                        + "\t( "
                        + formatDouble(((double) numAbortsInvalidSnapshot / (double) numAborts) * 100)
                        + " %)");
        System.out.println("  Read set size on avg.:    \t"
                + formatDouble(readSetSizeSum / statSize));
        System.out.println("  Write set size on avg.:   \t"
                + formatDouble(writeSetSizeSum / statSize));
        System.out.println("  Tx time-to-commit on avg.:\t"
                + formatDouble((double) txDurationSum / numCommits)
                + " microsec");
        System.out.println("  Number of elastic reads       " + elasticReads);
        System.out
                .println("  Number of reads in RO prefix  " + readsInROPrefix);
    }

    /**
     * Print the iteration statistics on the standard output
     */
    private void printIterationStats() {
        printLine('-');
        System.out.println("Iteration statistics");
        printLine('-');

        int n = parameters.iterations;
        System.out.println("  Iterations:                 \t" + n);
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += ((throughput[i] / 1024) / 1024);
        }
        System.out.println("  Total throughput (mebiops/s): " + sum);
        double mean = sum / n;
        System.out.println("  |--Mean:                    \t" + mean);
        double temp = 0;
        for (int i = 0; i < n; i++) {
            double diff = ((throughput[i] / 1024) / 1024) - mean;
            temp += diff * diff;
        }
        double var = temp / n;
        System.out.println("  |--Variance:                \t" + var);
        double stdevp = java.lang.Math.sqrt(var);
        System.out.println("  |--Standard deviation pop:  \t" + stdevp);
        double sterr = stdevp / java.lang.Math.sqrt(n);
        System.out.println("  |--Standard error:          \t" + sterr);
        System.out.println("  |--Margin of error (95% CL):\t" + (sterr * 1.96));
    }

    private static String formatDouble(double result) {
        Formatter formatter = new Formatter(Locale.US);
        return formatter.format("%.2f", result).out().toString();
    }
}
