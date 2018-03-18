//Griffin Saiia, Gjs64
//Networks
//Project 1, P2P Network
//Mother File: P2P Class

import java.nio.channels.Pipe;
import java.lang.Thread;
import java.nio.file.Paths;
import java.io.*;
import java.net.*;
import java.util.*;

/*All processes running at a given time:
 * 1. user requests + inputs at command line --> P2P.main()
 * 2. periodically checking connections (heartbeats) 
 *            --> Manager heartbeat = (new Manager(peers, -1)).run();
 * 3. listening to connections to respond/forward queries accross connections 
 *            ---> QManager queries = (new QManager(peers, 1)).run();
 *                 ---> each peer will run at least 2 processes here (1 for each peer)
 * 4. listen for and respond to file requests from peers
 *            ---> ServerSocket
 */


//responsible for launching and maintaining all processes, has main that takes user input
/*USER COMMANDS:
 * Get <filename> - peer queries peers to find and download file
 * Leave - peer closes connections with its neighbors
 * Connect - launches connections with each peer this peer knows
 * Exit - closes connections, and terminates
 */
public class P2P extends Pipe, Thread{
  //dedicating this port to all heartbeats accross peers
  private static final int HeartBeatPort = 51820;
  //dedicating this port to all requests from other peers for file
  private static final int FileRequestPort = 51821;
  //dedicating this port to all requests from user to other peers for a file
  private static final int UserFileRequestPort = 51822;
  
  //Processes controlled by P2P
  private Manager query;
  private QManager hearbeat;
  private ServerSocket listener;
  private ServerSocket myRequests;
  
  //Paths and Files
  private String shared;
  private String obtained; 
  private File peers;
  //mode of communication accross processes
  private Pipe processCom;
  
  
  private void P2P(){
    shared = "~/P2P/shared/";
    obtained = "~/P2P/obtained/";
  }
  
  
  private class Manager extends Thread{
    private LinkedList<PeerHandler> peerList;
    
    //constructor for hearbeat
    private void Manager(LinkedList<Node> peers){
      peerList = new LinkedList<PeerHandler>();
      int i = 0;
      while(i < peers.size()){
        peerList.add(new PeerHandler(peers.getHost()));
      }
      
    }
    
    private void run(){
      try{
        int i;
        while(1){
          TimeUnit.SECONDS.sleep(10);
          i = 0;
          while(i < peerList.size()){
            peerList.peek().heartbeat();
            peerList.add(peerList.removeFirst());
            i++;
          }
        }
      }
      catch (InterruptedException e){
        int i = 0;
        while(i < peerList.size()){
          peerList.peek().close();
          i++;
        }
      }
    }
    
  }
  
  private class QManager extends Thread{
    private LinkedList<PeerHandler> peerList;
    
    private void QManager(LinkedList<Node> Addressing, LinkedList<String> fileData){
      //create matchedQuery
      int i = 0;
      peerList = new LinkedList<PeerHandler>();
      while(i < Addressing.size()){
        peerList.add(new PeerHandler(Addressing.peek().hostname, Addressing.getFirst().port, fileData));
        i++;
      }
      while(i < Addressing.size()){
        
      }
    }
    
    private void run(){
      try{
        int i = 0;
        while(i < peerList.size()){
          peerList.peek().start();
          i++;
        }
        while(1){
          TimeUnit.SECONDS.sleep(5);
        }
      }
      catch (ManagerException e){
        
      }
      catch (InterruptedException e){
        int i = 0;
        while(i < peerList.size()){
          peerList.peek().interrupt();
          i++;
        }
      }
    }
    
    private class ManagerException extends Exception{
      public ManagerException(String address, PeerHandler me){
        ;
      }
    }
  }
  
  private class PeerHandler extends Thread{
    private final boolean type; //true - PeerHandler for QManager, false - PeerHandler for Manager
    private Peer myPeer;
    private DataOutputStream input;
    private BufferedReader output;
    private LinkedList<PeerHandler> peers;
    private LinkedList<String> files;
    private String myAddress;
    private int transferResponsePort;
    
    //peer handler constructor for Manager
    private void PeerHandler(String address){
      myPeer = new Peer(address);
      type = false;
    }
    
    private void PeerHandler(String address, int port, LinkedList<String> fileData){
      myPeer = new Peer(address, port);
      files = new LinkedList<String>();
      files.addAll(fileData);
      peers = new LinkedList<PeerHandler>();
      myAddress = InetAddress.getLocalHost();
      type = true;
    }
    
    private void run(){
      if(type){
        try{
          TimeUnit.MINUTES.sleep(5);
          connect();
          if(isConnect()){
            String query = input.readLine();
            String removeHead[] = query.split(":");
            String components[] = removeHead[1].split(";");
            int i = 0;
            while(i < files.size()){
              if(files.peek() == components[1]){
                String response = "R:"+components[0]+myAddress+":"+
                  Integer.toString(transferResponsePort)+";"components[1]+"\n";
                output.writeBytes(response);
                break;
              }
              else{
                int j = 0;
                while(j < peers.size()){
                  peers.peek().forward(query);
                  peers.add(peers.removeFirst());
                  j++;
                }
              }
              files.add(files.removeFirst());
              i++;
            }
          }
        }
        catch (InterruptedException e){
          close();
        }
      } 
    }
    
    private void addPeer(PeerHandler newpeer){
      peers.add(newpeer);
    }
    
    private void forward(String query){
      if(isConnected()){
        output.writeBytes(query);
      }
    }
    
    private String getAddress(){
      return myPeer.getIP();
    }

    
    private boolean isConnected(){
      return myPeer.isOnline();
    }
    
    private void heartbeat(){
      if(!type){
        if(isConnected()){
          if(!isBusy){
            try{
              input.writeByte(-1);
              return isConnected();
            }
            catch(Exception e){
              myPeer.setOnline();
              return isConnected();
            }
          }
        }
      }
    }
    
    private void connect(){
      if(!isConnected()){
        myPeer.makeConnection();
        if(isConnected()){
          input = myPeer.getInputBuffer();
          output = myPeer.getOutputBuffer();
        }
      }
    }
    
    private void close(){
      myPeer.closeConnection();
    }
    
    private class QueryException extends ManagerException{
      public QueryException(String address, PeerHandler me){
        super(address, me);
      }
    }
    
  }
  
  
  //used to store a peer from peer list
  private class Peer{
    private String ip;
    private int port;
    private boolean online;
    private DataOutputStream input;
    private Socket peerConnection;
    private BufferedReader incoming;
    private boolean busy;
    
    //constructor for Peer used by query
    private void Peer(String address, int inport){
      setIP(address);
      setPort(inport);
      online = false;
    }
    
    //constructor for Peer used by heartbeat
    private void Peer(String address){
      setIP(address);
      setPort("51820");
      online = false;
    }
    
    private BufferedReader getInputBuffer(){
      if(isOnline()){
        return input;
      }
      else{
        return null;
      }
    }
    
    private void setIP(String address){
      ip = address;
    }
    
    private String getIP(){
      return ip;
    }
    
    private void setPort(int inport){
      port = inport;
    }
    
    private int getPort(){
      return port;
    }
    
    private void makeConnection(){
      try{
        peerConnection = new Socket(getIP(), getPort());
        setOnline();
      }
      catch (Exception e){
        if(isOnline()){
          setOnline();
        }
      }
      if(isOnline()){
        input = new DataOutputStream(peerConnection.getOutputStream());
        incoming = new BufferedReader(new InputStreamReader(peerConnection.getInputStream()));
      }
    }
    
    private void setOnline(){
      online = !online;
    }
    
    private boolean isOnline(){
      return online;
    }
    
    private Socket getConnection(){
      if(isOnline()){
        return peerConnection;
      }
      else{
        return null;
      }
    }
    
    private void closeConnection(){
      if(isOnline()){
        peerConnection.close();
        setOnline();
      }
    }
    
    private BufferedReader getIncomingBuffer(){
      if(isOnline()){
        return incoming;
      }
      else{
        return null;
      }
    }
  }
  
  //data type
  private class Node{
    private final String hostname
    private final int port;
    private void Node(String ip, int inport){
      hostname = ip;
      port = inport;
    }
    private String getHost(){
      return hostname;
    }
    private int getPort(){
      return port;
    }
  }
  //essentially a data type
  private class Query{
    private boolean read;
    private final PipedInputStream pipe;
    private String lastQuery;
    private String recieved;
    private void Query(PipedInputStream readpipe, String reciever){
      pipe = readpipe;
      recieved = reciever;
      read = false;
    }
    private boolean isRead(){
      if(lastQuery == null){
        return false;
      }
      if(lastQuery == getFile()){
        return true;
      }
      else{
        return false;
      }
    }
    private void setRead(){
      lastQuery = getFile();
    }
    private String getFile(){
      return (String file = pipe.read();)
    }
    private PipedInputStream getPipe(){
      return pipe;
    }
    private String getAddress(){
      reutrn recieved;
    }
  }
}
