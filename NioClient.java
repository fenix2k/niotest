import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

// Клиент
public class NioClient implements Runnable {
    private static final int DEFAULT_MESSAGE_SIZE = 512;
    private static final int DEFAULT_BUFFER_SIZE = 512;

    private final String IP;
    private final int PORT;
    private final int MESSAGE_SIZE;
    private final int BUFFER_SIZE;

    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;

    private ByteBuffer readBuffer;
    private final BufferedReader consoleInput;

    public NioClient(String IP, int PORT) throws IOException {
        this(IP, PORT, DEFAULT_MESSAGE_SIZE, DEFAULT_BUFFER_SIZE);
    }

    public NioClient(String IP, int PORT, int MESSAGE_SIZE, int BUFFER_SIZE) throws IOException {
        this.IP = IP;
        this.PORT = PORT;
        this.MESSAGE_SIZE = MESSAGE_SIZE;
        this.BUFFER_SIZE = BUFFER_SIZE;

        this.socket = new Socket(IP, PORT);
        this.dis = new DataInputStream(this.socket.getInputStream());
        this.dos = new DataOutputStream(this.socket.getOutputStream());

        this.consoleInput = new BufferedReader(new InputStreamReader(System.in));

        new Thread(new Receiver()).start();
        this.run();
    }

    public static void main(String[] args) {
        try {
            new NioClient("localhost", 8000);
        } catch (IOException e) {
            System.out.println("Unable to connect to server.");
        }
    }

    @Override
    public void run() {
        System.out.println("Client started.");

        while(true) {
            String userMessage = null;

            try {
                userMessage = this.consoleInput.readLine();

                if(this.socket.isClosed()) {
                    System.out.println("Socket was unexpected closed.");
                    System.exit(0);
                    break;
                }
                if(userMessage == null || userMessage.length() == 0) {
                    System.out.println("Wrong message. Try again.");
                    continue;
                }

                int messageLength = userMessage.length();
                byte[] messageByteArray = userMessage.getBytes("UTF-8");
                this.dos.writeInt(messageLength);

                int middlePos = messageByteArray.length - (messageByteArray.length/2);
                this.dos.write(messageByteArray,0, middlePos);
                this.dos.write(messageByteArray, middlePos, messageByteArray.length - middlePos);
                this.dos.flush();

                if("quit".equals(userMessage.toLowerCase()))
                    break;
            } catch (IOException e) {
                System.out.println("Connection lost: " + e.getMessage());
                e.printStackTrace();
                System.exit(0);
            }
        }
    }

    private class Receiver implements Runnable {
        @Override
        public void run() {
            System.out.println("Receiver started.");
            while (!socket.isClosed()) {
                try {
                    if (dis.available() < 4) {
                        Thread.sleep(100);
                        continue;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }

                int read = 0;
                int messageLength = 0;
                byte[] messageByteArray = null;

                try {
                   messageLength =  dis.readInt();
                   if(messageLength > 0) {
                       messageByteArray = new byte[messageLength];
                       read = dis.read(messageByteArray);
                   }

                   System.out.println("Server send: " + new String(messageByteArray, 0, read, "UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
