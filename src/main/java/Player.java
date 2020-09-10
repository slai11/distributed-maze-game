import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Player is the remote object that encapsulates
 * 1. server behaviours
 * 2. standard player behaviours
 */
public interface Player extends Remote, Serializable {
    // server behaviour
    void push(State latest) throws RemoteException, Exception;

    void ping() throws RemoteException;

    // standard player behaviour
    State register(PlayerImpl p) throws RemoteException, Exception;

    State move(Move move, String caller) throws RemoteException, Exception;

    State get() throws RemoteException, Exception;

    void leave(String leaver) throws RemoteException, Exception;
}
