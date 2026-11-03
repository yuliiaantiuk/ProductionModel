package production.config;

import java.util.Arrays;

public class ModelConfig {

    public static final int TYPES = 3;
    public static final int WORKSTATIONS = 5;
    private final int[][] routes;
    private final double[] typeProb;

    private double meanInterval;
    private final double[][] tau;
    private final int[] machines;

    private int queuePlaces;
    private double timeModeling;
    private double warmUp;

    private ModelConfig(int[][] routes, double[] typeProb, double meanInterval, double[][] tau,
                        int[] machines, int queuePlaces, double timeModeling, double warmUp) {
        this.routes = routes;
        this.typeProb = typeProb;
        this.meanInterval = meanInterval;
        this.tau = tau;
        this.machines = machines;
        this.queuePlaces = queuePlaces;
        this.timeModeling = timeModeling;
        this.warmUp = warmUp;
    }

    public static ModelConfig defaults() {
        return new ModelConfig(
                new int[][]{{3, 1, 2, 5}, {4, 1, 3}, {2, 5, 1, 4, 3}},
                new double[]{0.3, 0.5, 0.2},
                0.25,
                new double[][]{{0.5, 0.6, 0.85, 0.5}, {1.1, 0.8, 0.75}, {1.24, 0.25, 0.7, 0.9, 1.0}},
                new int[]{3, 2, 4, 3, 1},
                3,
                1000.0,
                0.0
        );
    }

    public ModelConfig copy() {
        int[][] r = new int[routes.length][];
        for (int k = 0; k < routes.length; k++) r[k] = routes[k].clone();
        double[][] t = new double[tau.length][];
        for (int k = 0; k < tau.length; k++) t[k] = tau[k].clone();
        return new ModelConfig(r, typeProb.clone(), meanInterval, t, machines.clone(),
                queuePlaces, timeModeling, warmUp);
    }

    public ModelConfig withMachines(int m, int count) {
        ModelConfig c = copy();
        c.machines[m - 1] = count;
        return c;
    }

    public ModelConfig withQueuePlaces(int n) {
        ModelConfig c = copy();
        c.queuePlaces = n;
        return c;
    }

    public ModelConfig withTime(double timeModeling, double warmUp) {
        ModelConfig c = copy();
        c.timeModeling = timeModeling;
        c.warmUp = warmUp;
        return c;
    }

    public ModelConfig withMeanInterval(double meanInterval) {
        ModelConfig c = copy();
        c.meanInterval = meanInterval;
        return c;
    }

    public ModelConfig withServiceTimesScaled(double factor) {
        ModelConfig c = copy();
        for (double[] row : c.tau)
            for (int s = 0; s < row.length; s++) row[s] *= factor;
        return c;
    }

    public int[] route(int k) { return routes[k - 1].clone(); }
    public double tau(int k, int step) { return tau[k - 1][step]; }
    public double tauAt(int k, int m) {
        int[] r = routes[k - 1];
        for (int s = 0; s < r.length; s++) if (r[s] == m) return tau[k - 1][s];
        return 0.0;
    }

    public double typeProb(int k) { return typeProb[k - 1]; }
    public int machines(int m) { return machines[m - 1]; }
    public double meanInterval() { return meanInterval; }
    public int queuePlaces() { return queuePlaces; }
    public double timeModeling() { return timeModeling; }
    public double warmUp() { return warmUp; }

    public double lambda(int k) { return typeProb(k) / meanInterval; }

    public double theoreticalLoad(int m) {
        double work = 0;
        for (int k = 1; k <= TYPES; k++) work += lambda(k) * tauAt(k, m);
        return work / machines(m);
    }

    public void validate() {
        double sum = 0;
        for (double p : typeProb) {
            if (p < 0) throw new IllegalArgumentException("Від'ємна ймовірність типу");
            sum += p;
        }
        if (Math.abs(sum - 1.0) > 1e-9)
            throw new IllegalArgumentException("Сума ймовірностей типів має дорівнювати 1, а не " + sum);
        if (meanInterval <= 0) throw new IllegalArgumentException("Інтервал надходження має бути > 0");
        for (int k = 0; k < TYPES; k++) {
            if (routes[k].length != tau[k].length)
                throw new IllegalArgumentException("Тип " + (k + 1) + ": довжина маршруту не збігається з кількістю часів обробки");
            for (int s = 0; s < routes[k].length; s++) {
                int m = routes[k][s];
                if (m < 1 || m > WORKSTATIONS)
                    throw new IllegalArgumentException("Тип " + (k + 1) + ": неіснуюче РМ " + m);
                if (tau[k][s] <= 0)
                    throw new IllegalArgumentException("Тип " + (k + 1) + ": час обробки має бути > 0");
            }
        }
        for (int m = 1; m <= WORKSTATIONS; m++)
            if (machines(m) < 1) throw new IllegalArgumentException("РМ" + m + ": має бути хоча б 1 верстат");
        if (queuePlaces < 1) throw new IllegalArgumentException("N має бути >= 1");
        if (timeModeling <= 0 || warmUp < 0 || warmUp >= timeModeling)
            throw new IllegalArgumentException("Потрібно 0 <= T_w < T_mod");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Конфігурація моделі\n");
        sb.append(String.format("  Середній інтервал надходження: %.3f год%n", meanInterval));
        sb.append("  Ймовірності типів: ").append(Arrays.toString(typeProb)).append('\n');
        for (int k = 1; k <= TYPES; k++)
            sb.append(String.format("  Тип %d: маршрут %s, час обробки %s год%n",
                    k, Arrays.toString(routes[k - 1]), Arrays.toString(tau[k - 1])));
        sb.append("  Кількість верстатів РМ1..РМ5: ").append(Arrays.toString(machines)).append('\n');
        sb.append(String.format("  N = %d, T_mod = %.1f год, T_w = %.1f год%n", queuePlaces, timeModeling, warmUp));
        return sb.toString();
    }
}