package contention.benchmark.ThreadLoops.VS;

import contention.abstractions.CompositionalMap;
import contention.abstractions.ThreadLoopAbstract;
import contention.benchmark.Parameters;

import java.lang.reflect.Method;

/**
 * The loop executed by each thread of the map
 * benchmark.
 *
 * @author Vincent Gramoli
 */
public class StairsThreadLoop extends ThreadLoopAbstract {

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

    /**
     * The counters of the thread successful operations
     */


    /**
     * The distribution of methods as an array of percentiles
     * <p>
     * 0%        cdf[0]        cdf[2]                     100%
     * |--writeAll--|--writeSome--|--readAll--|--readSome--|
     * |-----------write----------|--readAll--|--readSome--| cdf[1]
     */
    int[] cdf = new int[3];

    public StairsThreadLoop(short myThreadNum,
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

    public void run() {

        int currentKey = 0;
        boolean direction = true;

        while (!stop) {

            if (direction) { // writeSome
                if (bench.putIfAbsent(currentKey, currentKey) == null) {
                    numAdd++;
                } else {
                    failures++;
                }
                if (++currentKey >= Parameters.range) {
                    direction = false;
                    --currentKey;
                }
            } else { //removeSome
                if (bench.remove(currentKey) != null) {
                    numRemove++;
                } else
                    failures++;
                if (--currentKey <= 0) {
                    direction = true;
                    currentKey = 0;
                }
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


