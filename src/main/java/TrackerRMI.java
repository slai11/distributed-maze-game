import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TrackerRMI extends Remote {
    Bootstrap register(Player myName, String id) throws RemoteException;
    void unregister(String name) throws RemoteException;
    Bootstrap fetch() throws RemoteException;
}
