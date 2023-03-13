package contention.benchmark.ThreadLoops.parameters;

import contention.abstractions.ParseArgument;
import contention.abstractions.ThreadLoopParameters;

public class TemporaryOperationsThreadLoopParameters implements ThreadLoopParameters {
    public int tempOperCount = 0;
    public int[] opTimes;
    public double[] numInserts;
    public double[] numErases;


    public void setTempOperCount(final int tempOperCount) {
        this.tempOperCount = tempOperCount;
        opTimes = new int[tempOperCount];
        numErases = new double[tempOperCount];
        numInserts = new double[tempOperCount];
    }

    @Override
    public void build() {

    }

    @Override
    public boolean parseArg(ParseArgument args) {
        switch (args.getCurrent()) {
            case "-temp-oper-count" -> setTempOperCount(Integer.parseInt(args.getNext()));
            case "-ot" -> opTimes[Integer.parseInt(args.getNext())] = Integer.parseInt(args.getNext());
            case "-uii" -> numInserts[Integer.parseInt(args.getNext())] = Double.parseDouble(args.getNext());
            case "-uei" -> numErases[Integer.parseInt(args.getNext())] = Double.parseDouble(args.getNext());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public StringBuilder toStringBuilder() {
        StringBuilder result = new StringBuilder();
        result
                .append("  Thread loop:             \t")
                .append("Temporary Operations")
                .append("\n")
                .append("  Number of temps:         \t")
                .append(this.tempOperCount)
                .append("\n")
                .append("  Writes ratios:           \t");

        for (int i = 0; i < tempOperCount; i++) {
            result
                .append("\n")
                .append("    Time of ").append(i).append(":             \t")
                .append(this.opTimes[i])
                .append(" op.\n")
                .append("    Write ratio of ").append(i).append(":      \t")
                .append(this.numInserts[i] + this.numErases[i])
                .append("\n")
                .append("    Insert ratio of ").append(i).append(":     \t")
                .append(this.numInserts[i])
                .append("\n")
                .append("    Erase ratio of ").append(i).append(":      \t")
                .append(this.numErases[i])
                .append("\n");
        }
        return result;
    }

}