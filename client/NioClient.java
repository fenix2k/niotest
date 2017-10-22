package client;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

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

        Thread receiver = new Thread(new Receiver());
        receiver.setDaemon(true);
        receiver.start();
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

                byte[] messageBody = userMessage.getBytes("UTF-8");
                int messageLength = messageBody.length + 4 + 4;
                this.dos.writeInt(messageLength); // отправляем длинну пакета
                this.dos.writeInt(0); // отправляем идентификатор пакета
                this.dos.writeInt(0); // отправляем тип пакета

                /*int middlePos = messageBody.length - (messageBody.length/2);
                this.dos.write(messageBody,0, middlePos);
                this.dos.write(messageBody, middlePos, messageBody.length - middlePos);*/

                this.dos.write(messageBody);
                this.dos.flush();

                if("quit".equals(userMessage.toLowerCase()))
                    break;
            } catch (IOException e) {
                System.out.println("Connection lost: " + e.getMessage());
                e.printStackTrace();
                System.exit(0);
            }
        }
        System.out.println("Client is closed.");
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
                int messageId = 0;
                int messageType = 0;
                byte[] messageBody = null;

                try {
                   messageLength =  dis.readInt();
                   if(messageLength > 0) {
                       messageId = dis.readInt();
                       messageType = dis.readInt();
                       messageBody = new byte[messageLength];
                       read = dis.read(messageBody);
                   }

                   System.out.println("Server send: id=" + messageId
                           + " type=" + messageType
                           + " msg=" + new String(messageBody, 0, read, "UTF-8"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
