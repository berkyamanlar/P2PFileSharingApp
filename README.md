# P2PFileSharingApplication

**Course:** CSE471: Data Communications and Computer Networks

## Project Overview

The **P2PFileSharingApplication** is a decentralized file-sharing application that allows users to share and download files without relying on a central server. The application employs a peer-to-peer (P2P) architecture, enabling direct communication between nodes in a local network using both UDP and TCP protocols.

## Key Features

- **Decentralized Architecture:**  
  The application operates on a decentralized model where each node can communicate directly with others. This approach enhances network resilience and eliminates single points of failure.

- **P2P Nodes:**  
  Each participant in the network acts as a node, capable of both sharing and retrieving files. Nodes can locate each other through broadcast mechanisms while maintaining network stability.

- **Protocol Flexibility:**  
  The application uses **UDP** for fast discovery and file listing, while **TCP** ensures reliable file transfers, providing an optimal balance between speed and data integrity.

- **Multithreading Support:**  
  The application is designed with multithreading capabilities, allowing multiple downloads to occur simultaneously. This feature improves user experience by reducing wait times when retrieving files from multiple peers.

- **Chunked File Downloads:**  
  Files are divided into smaller chunks for efficient transfer. Users can download different pieces of the same file from various peers, which are then reassembled into a complete file, ensuring faster and more reliable downloads.

## User Interface

The application features an intuitive graphical user interface (GUI) that simplifies user interactions. Below are screenshots of the application:

- **Setup Screen:**  
  ![Setup Screen](https://github.com/berkyamanlar/P2PFileSharingApp/blob/main/assets/setupscreen.png)

- **Main Screen:**  
  ![Main Screen](https://github.com/berkyamanlar/P2PFileSharingApp/blob/main/assets/mainscreen.png)

## Conclusion

The **P2PFileSharingApplication** serves as an innovative implementation of data communications principles learned in the CSE471 course. It showcases the potential of decentralized networks for file sharing while ensuring user-friendly interactions and efficient data handling.

---
