package server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ClientThread extends Thread {
	private InputStream inputStream = null;
	private OutputStream outputStream = null;

	private Socket clientSocket = null;
	private byte[] bytesName;
	private final ClientThread[] threads;
	private int maxClientsCount;

	public ClientThread(Socket clientSocket, byte[] bytesName, ClientThread[] threads) {
		this.clientSocket = clientSocket;
		this.threads = threads;
		this.bytesName = bytesName;
		maxClientsCount = threads.length;

		try {
			inputStream = this.clientSocket.getInputStream();
			outputStream = this.clientSocket.getOutputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		String type;
		byte[] byteType = new byte[128];
		int realSize;
		byte[] byteRealSize = new byte[4];
		int blockSize;
		byte[] byteBlockSize = new byte[4];
		byte[] message;

		try {
			while (inputStream.read(byteType) > 0) {
				type = new String(byteType);
				send(type.getBytes());
				type = type.trim();

				send(bytesName);

				inputStream.read(byteRealSize);
				realSize = ByteBuffer.wrap(byteRealSize).asIntBuffer().get();
				send(ByteBuffer.allocate(4).putInt(realSize).array());

				inputStream.read(byteBlockSize);
				blockSize = ByteBuffer.wrap(byteBlockSize).asIntBuffer().get();
				send(ByteBuffer.allocate(4).putInt(blockSize).array());

				message = new byte[blockSize];
				int readed = 0;
				do {
					readed += inputStream.read(message, readed, blockSize - readed);
				} while (readed < blockSize);

				send(message);

				/*
				 * You can use this to save your files if (type.equals("ImageBMP")) { String
				 * tmpFilePath = "tmp/" + System.currentTimeMillis(); InputStream in = new
				 * ByteArrayInputStream(message); OutputStream out = new
				 * FileOutputStream(tmpFilePath);
				 * 
				 * // Transfer bytes from in to out byte[] buf = new byte[1024]; int len; while
				 * ((len = in.read(buf)) > 0) { out.write(buf, 0, len); } in.close();
				 * out.close();
				 * 
				 * System.out.println("Press enter to continue..."); System.in.read();
				 * 
				 * File f = new File(tmpFilePath); send(Files.readAllBytes(f.toPath()));
				 * f.delete(); } else { send(message); }
				 */

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void send(byte[] b) throws IOException {
		synchronized (this) {
			for (int i = 0; i < this.maxClientsCount; i++) {
				if (this.threads[i] != null) {
					this.threads[i].outputStream.write(b);
					this.threads[i].outputStream.flush();
				}
			}
		}
	}

}
