package production;

import production.config.ModelConfig;
import production.net.FactoryNetBuilder;
import production.net.NetIndex;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriSim;

import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        for (int n : new int[]{3, 10, 30}) {
            ModelConfig cfg = ModelConfig.defaults().withQueuePlaces(n);
            NetIndex idx = FactoryNetBuilder.build(cfg);
            int places = idx.net.getListP().length;
            int trans = idx.net.getListT().length;
            System.out.printf("N = %2d: позицій %d (очікується %d), переходів %d (очікується %d), дуг %d + %d%n",
                    n, places, 34 + 17 * n, trans, 28 + 12 * n,
                    idx.net.getArcIn().length, idx.net.getArcOut().length);
        }

        ModelConfig cfg = ModelConfig.defaults().withQueuePlaces(30);
        NetIndex idx = FactoryNetBuilder.build(cfg);
        ArrayList<PetriSim> list = new ArrayList<>();
        list.add(new PetriSim(idx.net));
        PetriObjModel model = new PetriObjModel(list);
        model.setIsProtokol(false);
        model.go(cfg.timeModeling());

        System.out.printf("%nПрогін бібліотекою, N = %d, T_mod = %.0f год:%n", cfg.queuePlaces(), cfg.timeModeling());
        for (int k = 1; k <= ModelConfig.TYPES; k++) {
            int done = idx.pOut[k].getMark();
            System.out.printf("  Тип %d: завершено %d, пропускна здатність %.2f завд./год (очікується %.2f)%n",
                    k, done, done / cfg.timeModeling(), cfg.lambda(k));
        }
        for (int m = 1; m <= ModelConfig.WORKSTATIONS; m++) {
            double u = (cfg.machines(m) - idx.pRes[m].getMean()) / cfg.machines(m);
            System.out.printf("  РМ%d: завантаження %.3f (теоретичне %.3f)%n", m, u, cfg.theoreticalLoad(m));
        }
    }
}