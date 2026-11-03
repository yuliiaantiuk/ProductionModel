package production;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

public class Main {
    public static void main(String[] args) {
        PetriP p = new PetriP("P_test", 1);
        PetriT t = new PetriT("T_test", 0.85);
        t.setDistribution("erlang2", 0.85);
        System.out.println(p.getName() + ": " + p.getMark() + " маркер(и)");
        System.out.println("Випадкова затримка Erlang(2; 0.85): " + t.getTimeServ());
    }
}