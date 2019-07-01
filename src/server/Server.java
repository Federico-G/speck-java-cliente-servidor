package server;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
	private static ServerSocket serverSocket = null;
	private static Socket clientSocket = null;

	private static final int maxClientsCount = 10;
	private static final ClientThread[] threads = new ClientThread[maxClientsCount];

	private static final int portNumber = 22222;

	public static void main(String args[]) {

		try {
			serverSocket = new ServerSocket(portNumber);
		} catch (IOException e) {
			System.out.println(e);
		}

		while (true) {
			try {
				clientSocket = serverSocket.accept();
				byte[] bytesName = new byte[128];
				clientSocket.getInputStream().read(bytesName);
				int i = 0;
				for (i = 0; i < maxClientsCount; i++) {
					if (threads[i] == null) {
						(threads[i] = new ClientThread(clientSocket, bytesName, threads)).start();
						break;
					}
				}
				if (i == maxClientsCount) {
					PrintStream printStream = new PrintStream(clientSocket.getOutputStream());
					printStream.println("Server too busy. Try later.");
					printStream.close();
					clientSocket.close();
				}
			} catch (IOException e) {
				System.out.println(e);
			}
		}
	}
}
