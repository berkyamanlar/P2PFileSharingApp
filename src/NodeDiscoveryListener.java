public interface NodeDiscoveryListener {
    void onNodeDiscovered(String ipAddress);
	void onNodeDisconnected(String ipAddress);
}
