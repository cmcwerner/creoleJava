import java.util.ArrayList;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

class PeerToPeer {
}
@SuppressWarnings("unchecked")
class PeerNode extends CreoleObject implements Server {
  private DB db;
  PeerNode(DB db) { this.db = db;}
  public Set<String> enquire() {
    return (Set<String>)creoleAwait(db.invoke("listFiles"));
  }
  
  public int getLength(String fileId) {
    return (Integer)creoleAwait(db.invoke("getLength",fileId)); // length in units of packages
  }
  
  public Package getPack(String fileId, int packageNum) {
    ArrayList<Package> file;
    file = (ArrayList<Package>)creoleAwait(db.invoke("getFile",fileId)); // this seems horribly inefficient, refetching the entire file for each package
    return (Package)file.get(packageNum-1); // switch to 0 indexing just here
  }
  
  void reqFile(Server serverId, String fileId) {
    ArrayList<Package> file = new ArrayList<Package>();
    Package pack;
    int packageNum;
    
    packageNum = (Integer)creoleAwait(serverId.invoke("getLength",fileId));
    while (packageNum > 0) {
      pack = (Package)creoleAwait(serverId.invoke("getPack",fileId, packageNum));
      file.add(0, pack);
      packageNum--;
    }
    db.invokeVoid("store",fileId, file);
  }

  HashMap<Server,Set<String>> availFiles(ArrayList<Server> serverList) {
    Future futFileList;
    Future futServerFileMap;
    Set<String> fileList = new HashSet<String>();
    HashMap<Server,Set<String>> serverFileMap = new HashMap<Server,Set<String>>();
    if (serverList.size() == 0) {
      return serverFileMap; // an empty list
    }
    else {
      Server server = serverList.remove(0);
      futFileList = server.invoke("enquire");
      futServerFileMap = this.invoke("availFiles",serverList /* need to send a copy*/);
      fileList = (Set<String>)creoleAwait(futFileList);

      serverFileMap = (HashMap<Server,Set<String>>)creoleAwait(futServerFileMap);
      serverFileMap.put(server,fileList);
      return serverFileMap; // may need to return a copy
    }
  }
}

class DB extends CreoleObject {
  private HashMap<String,ArrayList<Package>> store = new HashMap<String,ArrayList<Package>>();
  
  ArrayList<Package> getFile(String fileId) {
    return store.get(fileId);
  }
  int getLength(String fileId) {
    if (store.containsKey(fileId)) {
      return store.get(fileId).size();
    }
    else return 0;
  }
  void storeFile(String fileId, ArrayList<Package> data) {
    store.put(fileId, data); // at some point I should probably copy the data to avoid modification
  }
  ArrayList<String> listFiles() {
    return new ArrayList<String>(store.keySet());    
  }
}

class Admin {
}

class Package {}

interface Server extends CreoleObjectI {
  Set<String> enquire() ;
  
  int getLength(String fileId) ;
  
  Package getPack(String fileId, int packageNum) ;
}
