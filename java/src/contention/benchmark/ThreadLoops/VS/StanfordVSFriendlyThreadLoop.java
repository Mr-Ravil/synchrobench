package contention.benchmark.ThreadLoops.VS;

import contention.abstractions.CompositionalMap;
import contention.abstractions.ThreadLoopAbstract;
import contention.benchmark.Parameters;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The loop executed by each thread of the map
 * benchmark.
 *
 * @author Vincent Gramoli
 */
public class StanfordVSFriendlyThreadLoop extends ThreadLoopAbstract {

    /**
     * The instance of the running benchmark
     */
    public CompositionalMap<Integer, Integer> bench;
    /**
     * The pool of methods that can run
     */
    protected Method[] methods;
    /**
     * The number of the current thread
     */
    protected final short myThreadNum;

    /** The counters of the thread successful operations */


    /**
     * The distribution of methods as an array of percentiles
     * <p>
     * 0%        cdf[0]        cdf[2]                     100%
     * |--writeAll--|--writeSome--|--readAll--|--readSome--|
     * |-----------write----------|--readAll--|--readSome--| cdf[1]
     */
    int[] cdf = new int[3];

    public StanfordVSFriendlyThreadLoop(short myThreadNum,
                                        CompositionalMap<Integer, Integer> bench, Method[] methods) {
        this.myThreadNum = myThreadNum;
        this.bench = bench;
        this.methods = methods;
        /* initialize the method boundaries */
        assert (Parameters.numWrites >= Parameters.numWriteAlls);
        cdf[0] = 10 * Parameters.numWriteAlls;
        cdf[1] = 10 * Parameters.numWrites;
        cdf[2] = cdf[1] + 10 * Parameters.numSnapshots;
    }

    public void printDataStructure() {
        System.out.println(bench.toString());
    }

    private static List<List<Integer>> nodes;
    private int curDepth;
    /** The random number */
    Random rand = new Random();

    private void createNodes(int l, int r, int depth) {
        if (l >= r)
            return;

        int m = l + (r - l) / 2;

        while (nodes.size() <= depth) {
            nodes.add(new ArrayList<>());
        }

        nodes.get(depth).add(m);

        createNodes(l, m, depth + 1);
        createNodes(m + 1, r, depth + 1);
    }

    public void fill(final long size) {
        nodes = new ArrayList<>();
        createNodes(0, Parameters.range, 0);

        for (int counter = 0, i = 0; i < nodes.size() && counter < size; i++) {
            boolean layerIsFull = false;

            for (int j = 0; j < nodes.get(i).size() && counter < size; j++, layerIsFull = j == nodes.get(i).size()) {
                int v = nodes.get(i).get(j);
                bench.putIfAbsent(v, v);
                counter++;
            }

            if (i != 0 && layerIsFull) {
                for (int j = 0; j < nodes.get(i - 1).size(); j++) {
                    int v = nodes.get(i - 1).get(j);
                    bench.remove(v);
                }
                curDepth = i;
            }
        }
    }

    public void run() {

        while (!stop) {
            int coin = rand.nextInt(1000);

            if (coin < cdf[1]) { // 2. should we run a writeSome

                int key = nodes.get(curDepth).get(rand.nextInt(nodes.get(curDepth).size()));

                if (bench.putIfAbsent(key, key) == null) {
                    numAdd++;
                } else {
                    failures++;
                }

            } else { // 4. then we should run a readSome operation

                int key = rand.nextInt(Parameters.range);

                if (bench.get(key) != null)
                    numContains++;
                else
                    failures++;
            }

            total++;

            assert total == failures + numContains + numSize + numRemove
                    + numAdd + numRemoveAll + numAddAll;
        }
        // System.out.println(numAdd + " " + numRemove + " " + failures);
        this.getCount = CompositionalMap.counts.get().getCount;
        this.nodesTraversed = CompositionalMap.counts.get().nodesTraversed;
        this.structMods = CompositionalMap.counts.get().structMods;
        System.out.println("Thread #" + myThreadNum + " finished.");
    }
}

