import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TrackerRMI extends Remote {
    Bootstrap register(PlayerImpl myName) throws RemoteException;
    void unregister(String name) throws RemoteException;
}
