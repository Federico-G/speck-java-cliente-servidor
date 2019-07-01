package client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

public class Client {
	private static String server;
	private static int port;
	private static String name;
	private static byte[] key = new byte[16];
	private static boolean debug;

	/** Chat client access */
	static class ChatAccess extends Observable {
		private Socket socket;
		private OutputStream outputStream;
		private InputStream inputStream;

		@Override
		public void notifyObservers(Object arg) {
			super.setChanged();
			super.notifyObservers(arg);
		}

		/** Create socket, and receiving thread */
		public void InitSocket(String server, int port) throws IOException {
			socket = new Socket(server, port);
			outputStream = socket.getOutputStream();
			inputStream = socket.getInputStream();

			sendName();

			Thread receivingThread = new Thread() {
				@Override
				public void run() {
					String type;
					byte[] bytesType = new byte[128];
					String name;
					byte[] bytesName = new byte[128];
					int realSize;
					byte[] bytesRealSize = new byte[4];
					int blockSize;
					byte[] bytesBlockSize = new byte[4];
					byte[] blockMessage;
					byte[] message;

					try {
						while (inputStream.read(bytesType) > 0) {
							type = new String(bytesType).trim();

							inputStream.read(bytesName);
							name = new String(bytesName).trim();

							inputStream.read(bytesRealSize);
							realSize = ByteBuffer.wrap(bytesRealSize).asIntBuffer().get();

							inputStream.read(bytesBlockSize);
							blockSize = ByteBuffer.wrap(bytesBlockSize).asIntBuffer().get();

							blockMessage = new byte[blockSize];
							message = new byte[realSize];
							int readed = 0;
							do {
								readed += inputStream.read(blockMessage, readed, blockSize - readed);
							} while (readed < blockSize);
							blockMessage = crypt.Speck.decrypt(blockMessage, key, debug);
							System.arraycopy(blockMessage, 0, message, 0, realSize);

							if (type.equals("Name")) {
								String text = new String(message);
								notifyObservers(new Message(name, text));
							} else if (type.equals("String")) {
								String text = new String(message);
								notifyObservers(new Message(name, text));
							} else if (type.startsWith("Image")) {
								BufferedImage image = ImageIO.read(new ByteArrayInputStream(message));
								notifyObservers(new Message(name, type, image));
							} else {
								notifyObservers(new Message("Not valid type: " + type));
							}
						}
					} catch (IOException e) {
						notifyObservers(new Message(e));
					}
				}
			};
			receivingThread.start();
		}

		public void sendName() {
			byte[] bytesName = new byte[128];
			System.arraycopy(name.getBytes(), 0, bytesName, 0, name.getBytes().length);
			try {
				outputStream.write(bytesName);
				outputStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void sendText(String text) {
			text += "\n";
			send(text.getBytes(), "String");
		}

		public void sendImage(File file) {
			String extension = file.getName().substring(file.getName().lastIndexOf('.') + 1);
			try {
				byte[] b = Files.readAllBytes(file.toPath());
				Thread.sleep(500); // XXX
				send(b, "Image" + extension.toUpperCase());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void send(byte[] b, String type) {
			byte[] bytesType = new byte[128];
			System.arraycopy(type.getBytes(), 0, bytesType, 0, type.getBytes().length);

			int realSize = b.length;
			byte[] bytesRealSize = ByteBuffer.allocate(4).putInt(realSize).array();

			int blockSize = realSize + (8 - realSize % 8);
			byte[] bytesBlockSize = ByteBuffer.allocate(4).putInt(blockSize).array();

			byte[] bBlock = new byte[blockSize];
			System.arraycopy(b, 0, bBlock, 0, b.length);

			try {
				outputStream.write(bytesType); // 128
				outputStream.write(bytesRealSize); // 4
				outputStream.write(bytesBlockSize); // 4
				outputStream.write(crypt.Speck.encrypt(bBlock, key, debug)); // blockSize
				// outputStream.write(bBlock);
				outputStream.flush();
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		/** Close the socket */
		public void close() {
			try {
				socket.close();
			} catch (IOException e) {
				notifyObservers(e);
			}
		}
	}

	/** Chat client UI */
	static class ChatFrame extends JFrame implements Observer {
		private static final long serialVersionUID = 1L;

		private JTextPane textArea;
		private JTextField inputTextField;
		private JButton sendButton;
		private JButton attachButton;
		private ChatAccess chatAccess;

		public ChatFrame(ChatAccess chatAccess) {
			this.chatAccess = chatAccess;
			chatAccess.addObserver(this);
			buildGUI();
		}

		/** Builds the user interface */
		private void buildGUI() {
			textArea = new JTextPane();
			textArea.setMinimumSize(new Dimension(250, 150));
			textArea.setPreferredSize(new Dimension(500, 300));
			textArea.setEditable(false);
			textArea.setContentType("text/html");
			DefaultCaret caret = (DefaultCaret) textArea.getCaret();
			caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
			// textArea.setLineWrap(true);
			add(new JScrollPane(textArea), BorderLayout.CENTER);

			Box box = Box.createHorizontalBox();
			add(box, BorderLayout.SOUTH);
			inputTextField = new JTextField();
			attachButton = new JButton("Attach");
			sendButton = new JButton("Send");
			box.add(inputTextField);
			box.add(attachButton);
			box.add(sendButton);

			// Action for the inputTextField and the goButton
			ActionListener sendListener = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					String str = inputTextField.getText();
					if (str != null && str.trim().length() > 0) {
						chatAccess.sendText(str);
					}
					inputTextField.selectAll();
					inputTextField.requestFocus();
					inputTextField.setText("");
				}
			};
			inputTextField.addActionListener(sendListener);
			sendButton.addActionListener(sendListener);

			// Action for the inputTextField and the goButton
			ActionListener attachListener = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					JFileChooser fc = new JFileChooser();
					fc.setDialogTitle("Please choose an image...");
					FileNameExtensionFilter filter = new FileNameExtensionFilter("png", "bmp", "jpg");
					fc.addChoosableFileFilter(filter);

					if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
						chatAccess.sendImage(fc.getSelectedFile());
					}
				}
			};
			attachButton.addActionListener(attachListener);

			this.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					chatAccess.close();
				}
			});
		}

		/** Updates the UI depending on the Object argument */
		public void update(Observable o, Object arg) {
			final Message message = (Message) arg;
			HTMLDocument doc = (HTMLDocument) textArea.getDocument();
			HTMLEditorKit kit = new HTMLEditorKit();
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					if (message.getType().equals("String")) {
						try {
							doc.insertString(doc.getLength(), message.getName() + ": " + message.get(), null);
						} catch (BadLocationException e) {
							e.printStackTrace();
						}
					} else if (message.getType().startsWith("Image")) {
						try {
							String imageType = message.getType().substring(5).toLowerCase();
							if(imageType.equals("bmp")) {
								imageType = "png";
							}
							BufferedImage image = (BufferedImage) message.get();
							File imageFile = new File("img/" + System.currentTimeMillis() + "." + imageType);
							ImageIO.write(image, imageType, imageFile);

							doc.insertString(doc.getLength(), message.getName() + ": \n", null);
							kit.insertHTML(doc, doc.getLength(), "<img src='file:" + imageFile.getAbsolutePath() + "'>",
									0, 0, null);

							doc.insertString(doc.getLength(), "\n", null);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			});
		}
	}

	private static void loadConfig() {
		try {
			InputStream input = new FileInputStream("config.properties");
			Properties prop = System.getProperties();
			prop.load(input);
			server = prop.getProperty("SERVER");
			port = Integer.parseInt(prop.getProperty("PORT"));
			name = prop.getProperty("NAME");
			key =  javax.xml.bind.DatatypeConverter.parseHexBinary(prop.getProperty("KEY"));
			debug = prop.getProperty("DEBUG").toUpperCase().equals("TRUE");
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		loadConfig();
		ChatAccess access = new ChatAccess();

		JFrame frame = new ChatFrame(access);
		frame.setTitle("SpeckChat! - connected to " + server + ":" + port);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setResizable(true);
		frame.setVisible(true);

		try {
			access.InitSocket(server, port);
		} catch (IOException e) {
			System.out.println("Cannot connect to " + server + ":" + port);
			e.printStackTrace();
			System.exit(0);
		}
	}
}
