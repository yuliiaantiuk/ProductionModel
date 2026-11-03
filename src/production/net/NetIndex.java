package production.net;

import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.util.HashMap;
import java.util.Map;

public class NetIndex {
    public PetriNet net;
    public int queuePlaces;

    public PetriP p0, pFork;
    public PetriT tGen;
    public PetriT[] tRoute;

    public PetriP[] pRes;
    public PetriP[][] pFree;

    public PetriP[][] pIn;
    public PetriP[][][] pQueue;
    public PetriP[][] pBusy;
    public PetriP[] pOut;
    public PetriT[][] tIn;
    public PetriT[][][] tMove;
    public PetriT[][] tStart;
    public PetriT[][] tComplete;

    public final Map<Integer, TransitionInfo> info = new HashMap<>();

    public TransitionInfo info(PetriT t) {
        return info.get(t.getNumber());
    }
}