package production.net;

public record TransitionInfo(TransitionRole role, int type, int workstation, int place, boolean lastOperation) {
}