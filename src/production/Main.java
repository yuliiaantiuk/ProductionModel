package production;

import production.config.ModelConfig;
import production.net.FactoryNetBuilder;
import production.net.NetIndex;
import production.sim.FactorySimulator;
import production.stats.SimulationResult;
import production.stats.StatisticsCollector;

public class Main {

    public static SimulationResult runOnce(ModelConfig cfg) throws Exception {
        NetIndex idx = FactoryNetBuilder.build(cfg);
        FactorySimulator sim = new FactorySimulator(cfg, idx);
        StatisticsCollector stats = new StatisticsCollector(cfg);
        sim.setListener(stats);
        sim.run();
        return stats.getResult();
    }

    public static void main(String[] args) throws Exception {
        ModelConfig base = ModelConfig.defaults().withQueuePlaces(100).withTime(1000, 0);
        SimulationResult r1 = runOnce(base);
        System.out.println(r1.toTable());

        ModelConfig stable = ModelConfig.defaults()
                .withMachines(1, 4).withMachines(2, 3).withMachines(4, 4)
                .withQueuePlaces(40).withTime(10000, 500);
        SimulationResult r2 = runOnce(stable);
        System.out.println(r2.toTable());

        long d = r2.completed[1] + r2.completed[2] + r2.completed[3];
        System.out.printf("Формула Літтла: N̄ = %.3f, λ·T_sys = %.3f%n",
                r2.avgInSystem, r2.avgTotalTime / stable.meanInterval());
    }
}