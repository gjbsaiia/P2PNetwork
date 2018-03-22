//Griffin Saiia, Gjs64
//Networks
//Project 1, P2P Network

import java.nio.channels.Pipe;
import java.lang.Thread;
import java.nio.file.Paths;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class p2p{

  //port numbers for a given process
  private static final int transferPort = 51821;
  private static final int heartbeatPort = 51820;
  private static final int requestPort = 51823;
  private static final int queryPort = 51822;

  //directory definitions
  private static String obtained = "~/p2p/obtained/";
  private static File shared = new File("~/p2p/shared/");
  private static String peerFile = "~/p2p/config_sharing.txt";
  private static String queryIDPipe = "~/p2p/pipes/queryIDPipe.txt";
  private static String responsePipe = "~/p2p/pipes/responsePipe.txt";
  private static String myKey = "~/p2p/idKey/key.txt";

  //processes
  private static Handler welcomeHandler; //handles file transfer requests from peers
  private static Handler myRequests; //handles sending a file request, and accepting the transfer
  private static SimpleManager heartbeat; //handles heartbeat protocol
  private static Manager queryManager; //handles incoming queries/responses

  //Volatile variable that causes all threads to cease
  private static volatile boolean exit = false;
  
  //ensures p2p can only be called via main
  private p2p(){
    ;
  }

  //helper function that defines the BufferedReader and connects the output
  //to the BufferedReader for a given Pipe
  private static BufferedReader createBufferedReader(String filename) throws IOException{
    FileReader input = new FileReader(filename);
    BufferedReader newBufReader = new BufferedReader(input);
    return newBufReader;
  }

  //method to parse peers list into LinkedList of Node data type
  //LinkedList because allows for scalability if I decide to take this project further
  //Also allows for the addition of peers while P2P is running
  private static LinkedList<Node> parseToNodeList(String peers) throws IOException{
    LinkedList<Node> returnedList = new LinkedList<Node>();
    String line;
    BufferedReader reader = new BufferedReader(new FileReader(peers));
    String[] components;
    Node addition;
    while((line = reader.readLine()) != null){
      components = line.split(" ");
      addition = new Node(components[0], (new Integer(components[1])));
      returnedList.add(addition);
    }
    return returnedList;
  }

  //method to parse Directory files into a searchable list
  private static LinkedList<String> parseToFileList(File dir){
    File[] listedFiles = dir.listFiles();
    LinkedList<String> returnedList = new LinkedList<String>();
    int i = 0;
    while(i < listedFiles.length){
      returnedList.add(listedFiles[i].getName());
      i++;
    }
    return returnedList;
  }

  public static void main(String[] args){
    try{
      //necessary information from P2P files wrapped in LinkedList
      LinkedList<Node> peerList = parseToNodeList(peerFile);
      LinkedList<String> fileList = parseToFileList(shared);
      //pipe to send queryID from myRequests to queryManager so it knows what
      //responses are relevant
      FileWriter toQueryManager = new FileWriter(queryIDPipe);
      BufferedReader fromMyRequests = createBufferedReader(queryIDPipe);
      //pipe to send a relevant query response from queryManager to main so that it
      //call myRequests.recieveFile(response)
      FileWriter toMain = new FileWriter(responsePipe);
      BufferedReader fromQueryManager = createBufferedReader(responsePipe);
      //sets up processes
      File[] fileArray = shared.listFiles();
      //handles file transfer requests from peers
      welcomeHandler = new Handler(fileList, fileArray, requestPort);
      //handles sending a file request, and accepting the transfer
      myRequests = new Handler(queryPort, obtained, peerList, toQueryManager);
      //handles heartbeat protocol
      heartbeat = new SimpleManager(heartbeatPort, peerList);
      //handles incoming queries/responses
      queryManager = new Manager(peerList, fileList, queryPort, fromMyRequests, toMain);
      System.out.println("welcome to the EECS 325 P2P service");
      BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
      System.out.print("p2p> ");
      String command;
      String[] components;
      String response = null;
      while(true){
        if(fromQueryManager.readLine() != null && fromQueryManager.readLine() != response){
          response = fromQueryManager.readLine();
          fromQueryManager.close();
          myRequests.recieveFile(response);
        }
        if(exit == true){
          System.out.println("Error has occurred. I am terribly sorry.");
          break;
        }
        command = userInput.readLine();
        components = command.split(" ");
        if(components[0] == "Connect" || components[0] == "connect"){
          heartbeat.run();
          welcomeHandler.run();
          queryManager.run();
        }
        if(components[0] == "Get" || components[0] == "get"){
          myRequests.sendQuery(components[1]);
        }
        if(components[0] == "Exit" || components[0] == "exit"){
          exit = true;
          System.out.println("closing current connections...");
          try{
            TimeUnit.SECONDS.sleep(5);
          }
          catch(InterruptedException e){
            System.out.println("Timer error.");
          }
          System.out.println("thanks for using my service.");
          break;
        }
      }
    }
    catch (IOException v){
      System.out.println("Error creating necessary reading/writing tools");
      exit = true;
    }
  }


  //************************************new class***********************************

  //class for welcomeHandler and myRequests
  private static class Handler extends Thread{
    //variables used by welcomeHandler
    private LinkedList<String> fileList;
    private File[] shared;
    private Peer[] queryConnection;
    //variables used by myRequests
    private FileWriter toQueryManager;
    private int queryNo;
    private String obtained;
    //shared variable between the two
    private ServerSocket connection;

    //constructor for welcomeHandler
    private Handler(LinkedList<String> files, File[] dirList, int portNo) throws IOException{
      fileList = new LinkedList<String>();
      fileList.addAll(files);
      shared = dirList;
      connection = new ServerSocket(portNo);
    }

    //constructor for myRequests
    private Handler(int queryPort, String dir, LinkedList<Node> peers,
                                       FileWriter outToQuery) throws IOException{
      obtained = dir;
      BufferedReader idKey = new BufferedReader(new FileReader(myKey));
      String key = idKey.readLine();
      idKey.close();
      queryNo = generateID(key);
      toQueryManager = outToQuery;
      int i = 0;
      while(i < peers.size()){
        queryConnection[i] = new Peer(peers.peek().getHost(), queryPort);
        i++;
      }
    }

    //this method is only used by welcomeHandler
    public void run(){
      try{
        while(true){
          if(exit == true){
            connection.close();
            break;
          }
          Socket connectionSocket = connection.accept();
          BufferedReader requester = new BufferedReader(
                                                        new InputStreamReader(connectionSocket.getInputStream()));
          DataOutputStream writeTo = new DataOutputStream(connectionSocket.getOutputStream());
          String request = requester.readLine();
          String[] components = request.split(":");
          String file = components[1];
          String nameOfPeer = connectionSocket.getInetAddress().getCanonicalHostName();
          System.out.println("peer "+nameOfPeer+" is requesting "+components[1]+"...");
          boolean flag = false;
          File fileToTransfer = new File("null");
          int i = 0;
          while(i < shared.length){
            if(file.equals(shared[i].getName())){
              flag = true;
              fileToTransfer = shared[i];
              break;
            }
            i++;
          }
          if(flag){
            BufferedReader reader = new BufferedReader(new FileReader(fileToTransfer));
            String line;
            System.out.println("begining transfer to "+nameOfPeer+"...");
            while((line = reader.readLine()) != null){
              writeTo.writeBytes(line);
            }
            System.out.println("transfer to "+nameOfPeer+" complete.");
            reader.close();
          }
          else{
            writeTo.writeBytes("Error. File not found.");
            System.out.println("huh, we don't have this file for peer "+nameOfPeer+".");
          }
          requester.close(); writeTo.close();
          connectionSocket.close();
        }
      }
      catch (IOException e){
        System.out.println("Error: IOException thrown by welcomeHandler in run().");
        exit = true;
      }
    }

    //takes the peer's key as a string, converts it to an int
    //this is used to ID queries
    private int generateID(String key){
      char[] array = key.toCharArray();
      int ID = 0;
      for(int i = 0; i < array.length; i++){
        ID += array[0];
      }
      return ID;
    }

    //sends a query for a specific file - only used by myRequests
    private boolean sendQuery(String filename) throws IOException{
      System.out.println("sending out queries for "+filename+"...");
      for(int i = 0; i < queryConnection.length; i++){
        queryConnection[0].makeConnection();
        DataOutputStream out = new DataOutputStream(queryConnection[0].getOutput());
        out.writeBytes("Q:"+queryNo+";"+filename);
        out.close();
        queryConnection[0].closeConnection();
      }
      toQueryManager.flush();
      toQueryManager.write((new Integer(queryNo)).toString());
      toQueryManager.close();
      queryNo++;
      return true;
    }

    //requests and recieves file - only used by myRequests
    private boolean recieveFile(String addressAndFile) throws IOException{
      String[] breakDown = addressAndFile.split(";");
      File transferred = new File(obtained+breakDown[1]);
      FileWriter writer = new FileWriter(transferred);
      String[] address = breakDown[0].split(":");
      int port = (int)(new Integer(address[1]));
      Socket newConnection = new Socket(address[0], port);
      BufferedReader transfer = new BufferedReader(
                                new InputStreamReader(newConnection.getInputStream()));
      DataOutputStream request = new DataOutputStream(newConnection.getOutputStream());
      System.out.println("requesting "+breakDown[1]+" for transfer...");
      request.writeBytes("T:"+breakDown[1]);
      String line;
      while(transfer.ready()){
        line = transfer.readLine();
        writer.write(line);
      }
      System.out.println(""+breakDown[1]+" has been transferred.");
      request.close(); writer.close(); transfer.close();
      return true;
    }

  }

  //************************************new class***********************************

  //class for heartbeat - simpler version of the class used for queryManager
  private static class SimpleManager extends Thread{
    private LinkedList<Peer> totalPeerList; //total number of peers
    private LinkedList<Peer> peerList; //online peers
    private ServerSocket connection;

    private SimpleManager(int port, LinkedList<Node> addressList){
      peerList = new LinkedList<Peer>();
      String address;
      while(addressList.peek() != null){
        address = addressList.getFirst().getHost();
        peerList.add(new Peer(address, port));
      }
    }

    //recieves heartbeats and sends heartbeats every 2 minutes
    public void run(){
      while(true){
        if(exit == true){
          break;
        }
        try{
          Timer timing = new Timer(3);
          String line;
          connection = new ServerSocket(heartbeatPort);
          while(true){
            Socket connectionSocket = connection.accept();
            System.out.println("..^..^..^..receiving heartbeat..^..^..^..");
            BufferedReader heartbeating = new BufferedReader(
                                          new InputStreamReader(connectionSocket.getInputStream()));
            DataOutputStream yes = new DataOutputStream(connectionSocket.getOutputStream());
            line = heartbeating.readLine();
            yes.writeBytes("1");
            heartbeating.close(); yes.close(); connectionSocket.close();
          }
        }
        catch (TimingException e){
          try{
            System.out.println("..^..^..^..sending heartbeats..^..^..^..");
            Peer current;
            int i = 0;
            while(i < peerList.size()){
              current = peerList.getFirst();
              current.makeConnection();
              DataOutputStream send;
              if((send = current.getOutput()) == null){
                System.out.println(".......connection to peer "+current.getIP()+" has timed out.......");
                current.setOnline();
              }
              else{
                send.writeBytes("1");
                peerList.add(current);
                i++;
              }
              current.closeConnection();
            }
          }
          catch(IOException i){
            System.out.println("Error: IOException thrown by in heartbeat protocol.");
            exit = true;
          }
        }
        catch(IOException e){
          System.out.println("Error: IOException thrown when recieving heartbeats.");
          exit = true;
        }
      }
    }

  }

  //************************************new class***********************************

  //Timer I wrote that throws an exception after a specified amount of time
  private static class Timer {
    private int time;

    private Timer(int howLong) throws TimingException{
      time = howLong;
      try{
        TimeUnit.MINUTES.sleep(time);
      }
      catch (InterruptedException e){
        System.out.println("Error: InterruptedException thrown by timer");
      }
      throw new TimingException();
    }
    
  }
  
  //************************************new class***********************************
  
  //specific timing exception thrown by Timer
  private static class TimingException extends Exception{
      private TimingException(){
        ;
      }
    }

  //************************************new class***********************************
  
  //class for queryManager
  private static class Manager extends Thread {
    private ServerSocket connection;
    private LinkedList<Peer> peerList;
    private LinkedList<String> fileList;
    private BufferedReader fromMyRequests;
    private FileWriter toMain;
    private String myAddress;
    private int lastID;
    private int responsePort;

    private Manager(LinkedList<Node> addressList, LinkedList<String> files,
                            int queryPort, BufferedReader queryNumber, FileWriter output){
      LinkedList<String> fileList = new LinkedList<String>();
      fileList.addAll(files);
      peerList = new LinkedList<Peer>();
      String address;
      Peer addition;
      while(addressList.peek() != null){
        address = addressList.getFirst().getHost();
        addition = new Peer(address, queryPort);
        peerList.add(addition);
      }
      fromMyRequests = queryNumber;
      toMain = output;
      myAddress = getIP();
      lastID = -1;
      responsePort = 51821;
    }
    
    //method to get the IP of your machine
    private String getIP() {
      String ip = "fail";
      Enumeration<?> en;
      try {
        en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
          NetworkInterface ni = (NetworkInterface) en.nextElement();
          Enumeration<?> ee = ni.getInetAddresses();
          while (ee.hasMoreElements()) {
            InetAddress ia = (InetAddress) ee.nextElement();
            if (!ia.isLoopbackAddress() && !ia.toString().contains(":"))
              ip = (ia.getHostAddress());
          }
        }
      } catch (SocketException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return ip;
    }

    public void run(){
      try{
        int queryID = -1;
        connection = new ServerSocket(queryPort);
        while(true){
          if(exit  == true){
            connection.close();
            break;
          }
          if((fromMyRequests.readLine() != null) && (fromMyRequests.readLine() != (new Integer(lastID)).toString())){
            queryID = (int)(new Integer(fromMyRequests.readLine()));
          }
          fromMyRequests.close();
          Socket queryConnection  = connection.accept();
          BufferedReader incomingQuery = new BufferedReader(
                                                            new InputStreamReader(queryConnection.getInputStream()));
          DataOutputStream queryRespond = new DataOutputStream(queryConnection.getOutputStream());
          String query = incomingQuery.readLine();
          String[] breakDown = query.split(":");
          if(breakDown[0] == "R"){
            breakDown = query.split("R:");
          }
          String[] components = breakDown[1].split(";");
          if(breakDown[0] == "Q"){
            int i = 0;
            System.out.println("incoming query for "+components[1]+"...");
            boolean found = false;
            String currentFile;
            while(i < fileList.size()){
              currentFile = fileList.getFirst();
              if(currentFile == components[1]){
                found = true;
                fileList.add(currentFile);
                break;
              }
              else{
                fileList.add(currentFile);
                i++;
              }
            }
            DataOutputStream writeTo;
            Peer currentPeer;
            if(found){
              System.out.println(""+components[1]+" found, sending out query response.");
            }
            else{
              System.out.println(""+components[1]+" not found, forwarding query...");
            }
            i = 0;
            while(i < peerList.size()){
              currentPeer = peerList.getFirst();
              currentPeer.makeConnection();
              writeTo = currentPeer.getOutput();
              if(found){
                writeTo.writeBytes("R:"+components[0]+";"+myAddress+":"+responsePort+";"+components[1]);
              }
              else{
                writeTo.writeBytes(query);
              }
              currentPeer.closeConnection();
              peerList.add(currentPeer);
            }
          }
          else{
            int incomingQueryID = (int)(new Integer(components[0]));
            if(incomingQueryID == queryID){
              lastID = queryID;
              System.out.println("the file "+components[1]+" you requested has been found.");
              toMain.write(components[1]+";"+components[2]);
              toMain.close();
            }
            else{
              int i = 0;
              Peer currentPeer;
              DataOutputStream writeTo;
              while(i < peerList.size()){
                currentPeer = peerList.getFirst();
                currentPeer.makeConnection();
                writeTo = currentPeer.getOutput();
                writeTo.writeBytes(query);
                currentPeer.closeConnection();
              }
            }
          }
        }
      }
      catch(IOException e){
        System.out.println("Error: IOException thrown by queryManager.");
        exit = true;
      }
    }

  }

  //************************************new class***********************************

  //used to store a peer from peer list
  private static class Peer{
    private String ip;
    private int port;
    private boolean online;
    private DataOutputStream output;
    private Socket peerConnection;
    private BufferedReader incoming;
    private boolean busy;

    //constructor for Peer used by query
    private Peer(String address, int inport){
      setIP(address);
      setPort(inport);
      online = false;
    }

    private DataOutputStream getOutput(){
      if(isOnline()){
        return output;
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
        output = new DataOutputStream(peerConnection.getOutputStream());
        incoming = new BufferedReader(new InputStreamReader(peerConnection.getInputStream()));
        setOnline();
      }
      catch (Exception e){
        if(isOnline()){
          setOnline();
        }
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
        try{
          peerConnection.close();
          output.close();
          incoming.close();
          setOnline();
        }
        catch(IOException e){
          setOnline();
        }
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

  //************************************new class***********************************

  //data type
  private static class Node{
    private String hostname;
    private int port;
    private Node(String ip, int inport){
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

}
