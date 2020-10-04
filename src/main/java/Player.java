import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Player is the remote object that encapsulates
 * 1. server behaviours
 * 2. standard player behaviours
 */
public interface Player extends Remote {
    // server behaviour
    void push(State latest) throws RemoteException, Exception;

    void ping() throws RemoteException;

    // standard player behaviour

    /**
     * register is processed only by the primary server. It adds the player reference to its own vector
     * of players.
     * It also generates a player on the grid with the name as reference point.
     */
    State register(Player p, String caller, int idx) throws RemoteException, Exception;

    State move(Move move, String caller) throws RemoteException, Exception;

    State get(String name) throws RemoteException, Exception;

    State leave(String leaver) throws RemoteException, Exception;

    void setPrimary(String name) throws Exception;

    State shadowMove(Move move, String caller) throws RemoteException, Exception;
}
