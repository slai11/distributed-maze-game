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
    State register(Player p) throws RemoteException, Exception;

    State move(Move move, String caller) throws RemoteException, Exception;

    State get(String name) throws RemoteException, Exception;

    void leave(String leaver) throws RemoteException, Exception;

    String getName() throws RemoteException;
}
