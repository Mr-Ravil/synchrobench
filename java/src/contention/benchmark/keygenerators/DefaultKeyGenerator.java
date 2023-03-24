package contention.benchmark.keygenerators;

import contention.abstractions.Distribution;
import contention.abstractions.KeyGenerator;
import contention.benchmark.keygenerators.data.KeyGeneratorData;

public class DefaultKeyGenerator implements KeyGenerator {
    private final KeyGeneratorData data;
    private final Distribution distribution;
    private final Distribution prefillDistribution;

    public DefaultKeyGenerator(KeyGeneratorData data, Distribution distribution, Distribution prefillDistribution) {
        this.data = data;
        this.distribution = distribution;
        this.prefillDistribution = prefillDistribution;
    }

    private int next() {
    }

    @Override
    public int nextGet() {
        return next();
    }

    @Override
    public int nextInsert() {
        return next();
    }

    @Override
    public int nextRemove() {
        return next();
    }

    @Override
    public int nextPrefill() {
        int index = prefillDistribution.next();
        return data.get(index);
    }
}
