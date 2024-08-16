import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class NodeThread extends Thread {
    private static final int UDP_PORT = 5000; // Port for listening/sending
    private static final int CHUNK_SIZE = 512 * 1024; // 512KB
    private static final int TCP_PORT = 12345; // TCP port for data transfer
    private static final int BUFFER_SIZE = 1024; // Buffer size for packet
    private static final String BROADCAST_ADDRESS = "255.255.255.255"; // Broadcast address
    public String sharedFolderPath; // Path to the shared folder
    private String sharedSecretKey;
    private NodeDiscoveryListener listener;
    private volatile boolean running = true;
    private Thread broadcastingThread;
    public Map<String, List<String>> filesByNode = new HashMap<>();
    public Map<String, Boolean> nodeConnectionStatus = new HashMap<>();
    private Set<String> selectedSharedFolders;

    public NodeThread(String sharedFolderPath, String sharedSecretKey, Set<String> selectedSharedFolders, NodeDiscoveryListener listener) {
        this.sharedFolderPath = sharedFolderPath;
        this.selectedSharedFolders = selectedSharedFolders;
        this.sharedSecretKey = sharedSecretKey;
        this.listener = listener;
    }

    @Override
    public void run() {
        new Thread(this::listenForEcho).start();
        new Thread(this::broadcastEcho).start();
        new Thread(this::startTCPServer).start();
    }

    private void listenForEcho() {
        try (DatagramSocket socket = new DatagramSocket(5000)) {
            byte[] receiveData = new byte[BUFFER_SIZE];
            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();

                String senderIP = receivePacket.getAddress().getHostAddress();
                
                if (receivedMessage.contains("Discovery:") && receivedMessage.contains(sharedSecretKey) && running) {
                	String[] parts = receivedMessage.split(":", 3);
                	if (parts.length >= 3) {
                        List<String> fileList = Arrays.asList(parts[2].split(","));
                        filesByNode.put(senderIP, fileList);
                	}
                	if (listener != null) {
                        listener.onNodeDiscovered(senderIP);
                    }
                	nodeConnectionStatus.put(senderIP, true);
                }
                else if (receivedMessage.contains("Goodbye:") && receivedMessage.contains(sharedSecretKey) && running) {
                    // Notify the listener about the node disconnection
                    if (listener != null) {
                        listener.onNodeDisconnected(senderIP);
                    }
                    nodeConnectionStatus.put(senderIP, false);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcastEcho() {
        try (DatagramSocket socket = new DatagramSocket()) {
            while (!Thread.currentThread().isInterrupted() && running) {
                socket.setBroadcast(true);
                List<String> fileList = findAllFilesInSharedFolder();
                String fileString = String.join(",", fileList);
                String message = "Discovery:" + this.sharedSecretKey + ":" + fileString;
                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(BROADCAST_ADDRESS), UDP_PORT);
                socket.send(sendPacket);
                try {
                    Thread.sleep(3000); 
                } catch (InterruptedException e) {
                    if (!running) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }

    
    public void stopBroadcasting() {
        this.running = false;
        if (broadcastingThread != null) {
            broadcastingThread.interrupt();
        }
    }
    
    public void startBroadcasting() {
        if (!running) {
            running = true;
            broadcastingThread = new Thread(this::broadcastEcho);
            broadcastingThread.start();
        }
    }
    
    public void sendDisconnectionMessage() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String message = "Goodbye:" + this.sharedSecretKey;
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(BROADCAST_ADDRESS), UDP_PORT);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public List<String> findAllFilesInSharedFolder() {
        List<String> fileList = new ArrayList<>();
        Path sharedFolderPath = Paths.get(this.sharedFolderPath);

        try {
            // Add files directly in the shared folder
            Files.walk(sharedFolderPath, 1)
                 .filter(Files::isRegularFile)
                 .map(Path::getFileName)
                 .map(Path::toString)
                 .forEach(fileList::add);

            // Add files from selected subfolders
            if (selectedSharedFolders != null) {
                for (String folderPath : selectedSharedFolders) {
                    Path subFolderPath = Paths.get(folderPath);
                    Files.walk(subFolderPath, 1)
                         .filter(Files::isRegularFile)
                         .map(path -> subFolderPath.relativize(path).toString())
                         .forEach(fileList::add);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileList;
    }
    
    public long getFileSize(String filename) {
        Path fullPath = findFileInSharedFolder(filename);
        if (fullPath == null) {
            return -1;
        }
        try {
            long fileSize = Files.size(fullPath);
            return fileSize;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int requestFileSize(String filename, String requestedIP) {
        int fileSize = -1;

        try (Socket socket = new Socket(requestedIP, TCP_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // Send the file size request with the prefix "FileSizeRequest:"
            String requestMessage = "FileSizeRequest:" + filename;
            dos.writeUTF(requestMessage);

            // Wait for the server to send back the file size
            fileSize = dis.readInt();

            // You can add additional logic here if needed

        } catch (IOException e) {
            e.printStackTrace();
            return -1; // Indicate an error if the connection fails
        }

        return fileSize;
    }
    
    public byte[] requestFileChunk(String filename, String requestedIP, int chunkID) {
        try (Socket socket = new Socket(requestedIP, TCP_PORT); 
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // Send request message
            String requestMessage = "FileChunkRequest:" + filename + ":" + chunkID;
            out.writeUTF(requestMessage);

            // Wait for response (This is simplified, actual implementation will depend on your protocol)
            int chunkSize = in.readInt();
            byte[] chunkData = new byte[chunkSize];
            in.readFully(chunkData);

            return chunkData;
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0]; // Return empty array on error
        }
    }

    
    private void startTCPServer() {
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                     DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

                    // Read the request message from the client
                    String requestMessage = dis.readUTF();

                    // Process only "FileSizeRequest" messages
                    if (requestMessage.startsWith("FileSizeRequest:")) {
                        // Extract filename from the request message
                        String filename = requestMessage.substring("FileSizeRequest:".length());
                        
                        // Get the file size
                        long fileSize = getFileSize(filename);

                        // Send the file size back to the client
                        dos.writeInt((int)fileSize);
                    }
                    else if (requestMessage.startsWith("FileChunkRequest")) {
                    	String[] parts = requestMessage.split(":", 3);
                        if (parts.length >= 3) {
                            String filename = parts[1];
                            int chunkID = Integer.parseInt(parts[2]);
                            sendFileChunk(dos, filename, chunkID);
                        }
                    }

                    // The client socket will be closed here due to try-with-resources

                } catch (IOException e) {
                    e.printStackTrace(); // Handle exceptions as needed
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // Handle exceptions as needed
        }
    }
    
    private void sendFileChunk(DataOutputStream dos, String filename, int chunkID) throws IOException {
        Path fullPath = findFileInSharedFolder(filename);
        if (fullPath == null) {
            dos.writeInt(0); // Indicates that the file was not found
            return;
        }
        File file = fullPath.toFile();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long chunkStart = (long) chunkID * CHUNK_SIZE;
            raf.seek(chunkStart);

            long remainingSize = raf.length() - chunkStart;
            int bufferSize = (int) Math.min(CHUNK_SIZE, remainingSize);
            byte[] buffer = new byte[bufferSize];

            int bytesRead = raf.read(buffer);
            if (bytesRead > 0) {
                dos.writeInt(bytesRead);
                dos.write(buffer, 0, bytesRead);
            } else {
                dos.writeInt(0);
            }
        }
    }
    
    private Path findFileInSharedFolder(String filename) {
        try {
            return Files.walk(Paths.get(sharedFolderPath))
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals(filename))
                        .findFirst()
                        .orElse(null);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    
}
