import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Utilities;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

public class P2PGUI extends JFrame implements NodeDiscoveryListener {

    private JTextField folderLocationField;
    private JTextField sharedSecretField;
    private JTextArea textAreaComputers;
    private JTextArea textAreaTransfers;
    private JTextArea textAreaFiles;
    private NodeThread node;
    private Set<String> selectedSharedFolders = new HashSet<>();

    public P2PGUI() {
        createGUI();
    }

    private void createGUI() {
        // Menu Items
        JMenu fileMenu = new JMenu("File");
        JMenu helpMenu = new JMenu("Help");

        JMenuItem devButton = new JMenuItem("Dev");
        helpMenu.add(devButton);
        devButton.addActionListener(e -> showAboutInfo());

        JMenuItem connectButton = new JMenuItem("Connect");
        fileMenu.add(connectButton);
        connectButton.setEnabled(false);

        JMenuItem disconnectButton = new JMenuItem("Disconnect");
        fileMenu.add(disconnectButton);
        disconnectButton.setEnabled(false);

        JMenuItem exitButton = new JMenuItem("Exit");
        fileMenu.add(exitButton);
        exitButton.addActionListener(e -> System.exit(0));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);

        // Layout
        setLayout(new FlowLayout());

        JLabel folderLocationLabel = new JLabel("Shared Folder Location:");
        folderLocationField = new JTextField(20);  

        JLabel sharedSecretLabel = new JLabel("Shared Secret:");
        sharedSecretField = new JTextField(20);

        // Start Button
        JButton startButton = new JButton("Start");
        startButton.addActionListener(e -> startNode());

        // Adding Components to Frame
        add(folderLocationLabel);
        folderLocationLabel.setBounds(20, 30, 200, 30);
        add(folderLocationField);
        folderLocationField.setBounds(170, 30, 300, 30);
        add(sharedSecretLabel);
        sharedSecretLabel.setBounds(20, 100, 100, 30);
        add(sharedSecretField);
        sharedSecretField.setBounds(170, 100, 300, 30);
        add(startButton);
        startButton.setBounds(185, 185, 100, 50);
        
        //adjust size and set layout
        setPreferredSize (new Dimension (535, 396));
        setLayout (null);
       
        add (menuBar);
        menuBar.setBounds (0, 0, 635, 25);
    }

    private void showAboutInfo() {
        JOptionPane.showMessageDialog(this, "P2P Application\nDeveloped by: Your Name", "About", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startNode() {
        String folderLocation = folderLocationField.getText().trim();
        String sharedSecret = sharedSecretField.getText().trim();

        // Validation checks
        if (folderLocation.isEmpty() || sharedSecret.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter all required fields.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Check if the folder location is valid
        File sharedFolder = new File(folderLocation);
        if (!sharedFolder.exists() || !sharedFolder.isDirectory()) {
            JOptionPane.showMessageDialog(this, "The shared folder location is not valid or does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        openSharedFoldersFrame(() -> {
            // Start Node Thread with the specified folder location, shared secret key, and selected folders
            node = new NodeThread(folderLocation, sharedSecret, selectedSharedFolders, this);
            node.start();

            // After selecting folders, open the main frame and close the current frame
            openSecondFrame();
            P2PGUI.this.dispose(); // Use P2PGUI.this to refer to the outer class instance
        });
    }

    
    private void openSecondFrame() {
    	JFrame mainScreenFrame = new JFrame("P2P File Sharing App");
        
        JMenu fileMenu = new JMenu("File");
        JMenu helpMenu = new JMenu("Help");
        
        JMenuItem devButton = new JMenuItem("Dev");
        helpMenu.add(devButton);
        devButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAboutInfo();
            }
        });
        
        JMenuItem connectButton = new JMenuItem("Connect");
        connectButton.addActionListener(e -> {
            if (node != null) {
                node.startBroadcasting();
            }
        });
        fileMenu.add(connectButton);
        JMenuItem disconnectButton = new JMenuItem("Disconnect");
        disconnectButton.addActionListener(e -> {
            if (node != null) {
            	node.stopBroadcasting();
            	node.sendDisconnectionMessage();
            }
            textAreaComputers.setText("");
            textAreaFiles.setText("");
            textAreaTransfers.setText("");
        });
        fileMenu.add(disconnectButton);
        JMenuItem exitButton = new JMenuItem("Exit");
        fileMenu.add(exitButton);
        exitButton.addActionListener(e -> {
            if (node != null) {
            	node.stopBroadcasting();
            	node.sendDisconnectionMessage();
            }
            mainScreenFrame.dispose();
        });
       
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        menuBar.add(helpMenu); 
        
        mainScreenFrame.setJMenuBar(menuBar);
        
        // Create labels
	    JLabel computersInNetworkLabel = new JLabel("Computers in Network:");
	    JLabel filesFoundLabel = new JLabel("Files Found:");
	    
	 // Get computer hostname and external IP address
	    String hostname = "Unknown Hostname";
	    String ipAddress = "Unknown IP Address";
	    try {
	        InetAddress localHost = InetAddress.getLocalHost();
	        hostname = localHost.getHostName();

	        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
	        while (interfaces.hasMoreElements()) {
	            NetworkInterface iface = interfaces.nextElement();
	            // Filters out 127.0.0.1 and inactive interfaces
	            if (iface.isLoopback() || !iface.isUp())
	                continue;

	            Enumeration<InetAddress> addresses = iface.getInetAddresses();
	            while(addresses.hasMoreElements()) {
	                InetAddress addr = addresses.nextElement();

	                // Filters out non-IPv4 addresses
	                if (addr instanceof Inet4Address) {
	                    ipAddress = addr.getHostAddress();
	                    break;
	                }
	            }

	            if (!ipAddress.equals("Unknown IP Address")) {
	                break;
	            }
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    // Create labels with computer's hostname and IP address
	    JLabel computerHostnameLabel = new JLabel("Computer Hostname: " + hostname);
	    JLabel computerIPLabel = new JLabel("Computer IP: " + ipAddress);

	    // Set bounds for labels
	    computersInNetworkLabel.setBounds(10, 20, 150, 20);
	    filesFoundLabel.setBounds(250, 20, 150, 20);
	    computerHostnameLabel.setBounds(10, 410, 260, 20);
	    computerIPLabel.setBounds(280, 410, 240, 20);
	    
	    // Create text areas
	    textAreaTransfers = new JTextArea();
	    textAreaComputers = new JTextArea();
	    textAreaFiles = new JTextArea();
	    textAreaFiles.addMouseListener(new MouseAdapter() {
	        @Override
	        public void mouseClicked(MouseEvent e) {
	            if (e.getClickCount() == 2) {
	                try {
	                    int index = textAreaFiles.viewToModel2D(e.getPoint());
	                    int rowStart = Utilities.getRowStart(textAreaFiles, index);
	                    int rowEnd = Utilities.getRowEnd(textAreaFiles, index);
	                    String selectedFile = textAreaFiles.getText().substring(rowStart, rowEnd).trim();
	                    onFileDoubleClick(selectedFile);
	                } catch (BadLocationException ex) {
	                    ex.printStackTrace();
	                }
	            }
	        }
	    });

	    
	    // Make text areas non-editable if desired
	    textAreaComputers.setEditable(false);
	    textAreaFiles.setEditable(false);
	    textAreaTransfers.setEditable(false);

	    // Create scroll panes for each text area
	    JScrollPane scrollPaneComputers = new JScrollPane(textAreaComputers);
	    JScrollPane scrollPaneFiles = new JScrollPane(textAreaFiles);
	    JScrollPane scrollPaneTransfers = new JScrollPane(textAreaTransfers);

	    // Set vertical and horizontal scroll bar policies
	    scrollPaneComputers.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
	    scrollPaneComputers.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	    scrollPaneFiles.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
	    scrollPaneFiles.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	    scrollPaneTransfers.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
	    scrollPaneTransfers.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

	    // Set bounds for scroll panes
	    scrollPaneComputers.setBounds(10, 40, 230, 200);
	    scrollPaneFiles.setBounds(250, 40, 230, 200);
	    scrollPaneTransfers.setBounds(10, 250, 470, 150);
	    
        mainScreenFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainScreenFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (node != null) {
                	node.stopBroadcasting();
                	node.sendDisconnectionMessage();
                }
                mainScreenFrame.dispose();
            }
        });

	    // Add labels to the frame
	    mainScreenFrame.add(computersInNetworkLabel);
	    mainScreenFrame.add(filesFoundLabel);
	    mainScreenFrame.add(computerHostnameLabel);
	    mainScreenFrame.add(computerIPLabel);

	    // Add scroll panes to the frame
	    mainScreenFrame.add(scrollPaneComputers);
	    mainScreenFrame.add(scrollPaneFiles);
	    mainScreenFrame.add(scrollPaneTransfers);
        
        mainScreenFrame.setLayout(null);
	    mainScreenFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	    mainScreenFrame.setSize(500, 500);
	    mainScreenFrame.setLocationRelativeTo(null);
	    mainScreenFrame.setVisible(true);
    }
    
    @Override
    public void onNodeDiscovered(String ipAddress) {
        SwingUtilities.invokeLater(() -> {
        	
            if (!textAreaComputers.getText().contains(ipAddress) ) {
                textAreaComputers.append(ipAddress + "\n");
            }
        });
        
        updateFileListDisplay();
    }
    
    @Override
    public void onNodeDisconnected(String ipAddress) {
        SwingUtilities.invokeLater(() -> {
            String currentText = textAreaComputers.getText();
            String updatedText = currentText.replaceAll(ipAddress + "\n", "");
            textAreaComputers.setText(updatedText);
        });
        removeDisconnectedNodeFiles(ipAddress);
    }

    private void removeDisconnectedNodeFiles(String disconnectedNodeIP) {
        List<String> disconnectedNodeFiles = node.filesByNode.getOrDefault(disconnectedNodeIP, Collections.emptyList());

        String currentText = textAreaFiles.getText();
        
        for (String file : disconnectedNodeFiles) {
            if (!isFileSharedByActiveNodes(file, disconnectedNodeIP)) {
                String fileToRemove = file.trim() + "\n";
                currentText = currentText.replaceAll(fileToRemove, "");
            }
        }
        
        final String updatedText = currentText; // Make it final for use inside SwingUtilities.invokeLater

        SwingUtilities.invokeLater(() -> {
            textAreaFiles.setText(updatedText);
        });
    }


    private boolean isFileSharedByActiveNodes(String file, String disconnectedNodeIP) {
        for (Map.Entry<String, Boolean> entry : node.nodeConnectionStatus.entrySet()) {
            String nodeIP = entry.getKey();
            Boolean isActive = entry.getValue();

            if (!nodeIP.equals(disconnectedNodeIP) && isActive && node.filesByNode.getOrDefault(nodeIP, Collections.emptyList()).contains(file)) {
                return true;
            }
        }
        return false;
    }
      
    private void updateFileListDisplay() {
        // Retrieve local files list for comparison
        List<String> localFiles = node.findAllFilesInSharedFolder();

        textAreaFiles.setText(""); // Clear existing text
        for (Map.Entry<String, List<String>> entry : node.filesByNode.entrySet()) {
        	String nodeIP = entry.getKey();
        	if (node.nodeConnectionStatus.getOrDefault(nodeIP, false)) {
            	for (String file : entry.getValue()) {
                    String trimmedFile = file.trim(); // Remove any leading/trailing whitespace, including newline characters
                    boolean isLocal = localFiles.contains(trimmedFile);
                    
                    if (!isLocal && !textAreaFiles.getText().contains(trimmedFile + "\n")) {
                        textAreaFiles.append(trimmedFile + "\n");
                    }
                }	
        	}
        }
    }
    
    private void onFileDoubleClick(String fileName) {
        List<String> nodesWithFile = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : node.filesByNode.entrySet()) {
            String nodeIP = entry.getKey();
            List<String> nodeFiles = entry.getValue();
            Boolean isActive = node.nodeConnectionStatus.getOrDefault(nodeIP, false);
            if (isActive && nodeFiles.stream().anyMatch(file -> file.trim().equals(fileName.trim()))) {
                nodesWithFile.add(nodeIP);
            }
        }

        if (!nodesWithFile.isEmpty()) {
            int fileSize = node.requestFileSize(fileName, nodesWithFile.get(0)); // Request file size from the first node
            int chunkCount = (int) Math.ceil((double) fileSize / 512000.0D);
            byte[][] fileChunks = new byte[chunkCount][];
            AtomicLong totalBytesReceived = new AtomicLong(0); // Thread-safe long for counting bytes

            ExecutorService executor = Executors.newFixedThreadPool(nodesWithFile.size());

            for (int i = 0; i < chunkCount; i++) {
                int chunkID = i;
                String nodeIP = nodesWithFile.get(i % nodesWithFile.size()); // Round-robin selection of nodes

                executor.submit(() -> {
                    byte[] chunkData = node.requestFileChunk(fileName, nodeIP, chunkID);
                    synchronized (fileChunks) {
                        fileChunks[chunkID] = chunkData; // Store chunk in the correct order
                        long received = totalBytesReceived.addAndGet(chunkData.length); // Update total bytes received

                        // Calculate percentage and convert to KB
                        double percentage = 100.0 * received / fileSize;
                        long receivedKB = received / 1024;
                        long totalKB = fileSize / 1024;

                        SwingUtilities.invokeLater(() -> textAreaTransfers.append(
                            "Chunk #" + (chunkID + 1) + " received from " + nodeIP + 
                            ". Received: " + receivedKB + " KB / " + totalKB + " KB " +
                            "(" + String.format("%.2f", percentage) + "%)\n"
                        ));
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                try (FileOutputStream fileOutputStream = new FileOutputStream(node.sharedFolderPath + File.separator + fileName)) {
                    for (byte[] chunk : fileChunks) {
                        if (chunk != null) {
                            fileOutputStream.write(chunk);
                        }
                    }
                    SwingUtilities.invokeLater(() -> textAreaTransfers.append("File download completed: " + fileName + "\n"));
                } catch (IOException e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> textAreaTransfers.append("Error writing file: " + e.getMessage() + "\n"));
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private void openSharedFoldersFrame(Runnable onFoldersSelected) {
        JFrame sharedFoldersFrame = new JFrame("Select Shared Folders");
        sharedFoldersFrame.setLayout(new BoxLayout(sharedFoldersFrame.getContentPane(), BoxLayout.Y_AXIS));
        sharedFoldersFrame.setSize(300, 400);

        Path sharedFolderPath = Paths.get(folderLocationField.getText().trim());
        List<JCheckBox> checkBoxes = new ArrayList<>();

        try {
            Files.walk(sharedFolderPath, 1)
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(sharedFolderPath)) // Exclude the root shared folder
                    .forEach(path -> {
                        JCheckBox checkBox = new JCheckBox(path.getFileName().toString());
                        checkBox.addActionListener(e -> {
                            if (checkBox.isSelected()) {
                                selectedSharedFolders.add(path.toAbsolutePath().toString());
                            } else {
                                selectedSharedFolders.remove(path.toAbsolutePath().toString());
                            }
                        });
                        checkBoxes.add(checkBox);
                        sharedFoldersFrame.add(checkBox);
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Add Select All button
        JButton selectAllButton = new JButton("Select All");
        selectAllButton.addActionListener(e -> {
            boolean allSelected = checkBoxes.stream().allMatch(JCheckBox::isSelected);
            checkBoxes.forEach(checkbox -> checkbox.setSelected(!allSelected));
            if (!allSelected) {
                checkBoxes.forEach(checkbox -> selectedSharedFolders.add(sharedFolderPath.resolve(checkbox.getText()).toString()));
            } else {
                selectedSharedFolders.clear();
            }
        });
        sharedFoldersFrame.add(selectAllButton);

        // Add a "Done" button to finalize folder selection
        JButton doneButton = new JButton("Done");
        doneButton.addActionListener(e -> {
            sharedFoldersFrame.dispose();
            onFoldersSelected.run(); // Execute the callback
        });
        sharedFoldersFrame.add(doneButton);

        sharedFoldersFrame.setVisible(true);
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            P2PGUI frame = new P2PGUI();
            frame.setTitle("P2P Network Application");
            frame.setSize(500, 300);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}
