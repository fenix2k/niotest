package client;

import network.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;

// Клиент
public class NioClient implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Packet.class.getName());

    private static final int DEFAULT_MESSAGE_SIZE = 60;

    private final String IP;
    private final int PORT;
    private final int MESSAGE_SIZE;

    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;

    private final BufferedReader consoleInput;
    private Thread receiver = new Thread(new Receiver());

    public NioClient(String IP, int PORT) throws IOException {
        this(IP, PORT, DEFAULT_MESSAGE_SIZE);
    }

    public NioClient(String IP, int PORT, int MESSAGE_SIZE) throws IOException {
        this.IP = IP;
        this.PORT = PORT;
        this.MESSAGE_SIZE = MESSAGE_SIZE;

        this.socket = new Socket(IP, PORT);
        this.dis = new DataInputStream(this.socket.getInputStream());
        this.dos = new DataOutputStream(this.socket.getOutputStream());

        this.consoleInput = new BufferedReader(new InputStreamReader(System.in));

        receiver.setDaemon(true);
        receiver.start();

    }

    public static void main(String[] args) {
        try {
            /*NioClient client = new NioClient("localhost", 8000);
            client.work();
            client.receiver.start();*/

            for (int i=0; i<1; i++) {
                NioClient client = new NioClient("localhost", 8000);
                Thread thread = new Thread(client);
                thread.start();
            }
        } catch (IOException e) {
            logger.error("Unable to connect to server: ", e);
        }
    }

    @Override
    public void run() {
        this.testWork();
        this.receiver.start();
    }

    public void testWork() {
        System.out.println("Client started (test mode)");
        ArrayList<String> testMsg = new ArrayList<>();

        testMsg.add("1234567890");
        testMsg.add("qwertyuiopfegewg");
        testMsg.add("asdfghjklgfdgbdzfb");
        testMsg.add("zxcvbnmbfdzbrdzbzdf");
        testMsg.add("qazxswedcvfrtgb");
        testMsg.add("lpokmmjijnbhuyg");
        testMsg.add("987632345678909876");
        testMsg.add("1qw3edr5tgy7uji9ol");
        testMsg.add("апуцпп4меу3523меицм");
        testMsg.add("432нркч4и6и7гк5т");
        testMsg.add("к32кемцуенуgegb4ebxп4ум");

        Random rand =  new Random();

        for(int i=0; i<20; i++) {
            String userMessage = null;
            userMessage = testMsg.get(rand.nextInt(testMsg.size()));

            try {

                if(this.socket.isClosed()) {
                    logger.error("Socket was unexpected closed");
                    logger.error("Disconnected from server");
                    System.out.println("Disconnected from server");
                    System.exit(0);
                    break;
                }

                byte[] messageBody = userMessage.getBytes("UTF-8");
                Packet packet = new Packet(this.MESSAGE_SIZE);
                packet.setPacket(0, messageBody);

                this.dos.write(packet.getByteArrayMessage());
                this.dos.flush();

                System.out.println(Thread.currentThread().getName() + " [" + i + "] send: " + packet);
            } catch (IOException e) {
                logger.error("Connection lost: ", e);
                System.exit(0);
            }
        }

        try {
            receiver.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(Thread.currentThread().getName() + " Client is closed");
    }

    public void work() {
        System.out.println("Client started");
        int counter = 0;

        while(true) {
            String userMessage = null;

            try {
                userMessage = this.consoleInput.readLine();

                if(this.socket.isClosed()) {
                    logger.info("Socket was unexpected closed");
                    logger.info("Disconnected from server");
                    System.out.println("Disconnected from server");
                    System.exit(0);
                    break;
                }
                if(userMessage == null || userMessage.length() == 0) {
                    logger.debug("Wrong packet. Try again");
                    System.out.println("Wrong packet. Try again");
                    continue;
                }

                byte[] messageBody = userMessage.getBytes("UTF-8");
                Packet packet = new Packet(this.MESSAGE_SIZE);
                packet.setPacket(0, messageBody);

                this.dos.write(packet.getByteArrayMessage());
                this.dos.flush();

                System.out.println("[" + counter++ + "] Packet was send: " + packet);

                if("quit".equals(packet.getPacketBodyStr().toLowerCase())) {
                    System.out.println("Shutdown client");
                    return;
                }
            } catch (IOException e) {
                //logger.log(Level.SEVERE,"Connection lost: ", e);
                logger.error("Disconnected from server");
                //System.exit(0);
                break;
            }
        }
        System.out.println("Client is closed");
    }

    // Класс обработки входящих сообщений
    private class Receiver implements Runnable {
        @Override
        public void run() {
            System.out.println("Receiver started");
            Packet packet = null;
            int hasBytes = -1; // хранит кол-во не обработанных байт в буфере

            try {
                // создаём экземпляр сообщения
                packet = new Packet(MESSAGE_SIZE);
            } catch (IOException e) {
                logger.error("Create Packet instance error");
                return;
            }

            // В цикле читаем из потока байты
            while (!socket.isClosed()) {
                try {
                    // ждем пока придёт хотя бы заголовок сообщения
                    if (dis.available() < Packet.HEADER_SIZE && hasBytes == 0) {
                        Thread.sleep(10);
                        continue;
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }

                int read = 0; // кол-во считаных байт

                try {
                    byte[] buffer;
                    // Создаём буффер для чтения сообщения нужной длинны
                    if((packet.readBuffer.position() + dis.available()) > MESSAGE_SIZE)
                        buffer = new byte[MESSAGE_SIZE - packet.readBuffer.position()];
                    else buffer = new byte[MESSAGE_SIZE];

                    read = dis.read(buffer); // читаем и потока в буфер

                    packet.readBuffer.put(buffer,0, read); // сохраняем в буфер Packet
                    hasBytes = 0;

                    do {
                        int position = packet.readBuffer.position(); // запоминаем текущее положение буфера
                        packet.readBuffer.position(0); // устанавливаем метку в 0 для чтения длинны сообщения
                        int messageLength = packet.readBuffer.getInt(); // читаем длинну сообщения

                        // Проверка валидности длинны сообщения (0 < messageLength < размер буфера)
                        if(messageLength <= 0 || messageLength > (MESSAGE_SIZE - Packet.LENGTH_SIZE)) {
                            // Длинна пакета не верная.  Прерываем цикл обработки буфера.
                            logger.error("Wrong packet size. May be packet is corrupt");
                            System.out.println("Wrong packet size. May be packet is corrupt");
                            break;
                        }

                        // Проеряем пришло ли сообщение полностью
                        if (messageLength <= (position - Packet.LENGTH_SIZE)) {
                            packet.readBuffer(messageLength); // читаем сообщение в Packet
                            System.out.println(Thread.currentThread().getName() + " [" + 0 + "] receive: " + packet);

                            // Определяем сколько ещё байтов в буфере
                            hasBytes = (position - Packet.LENGTH_SIZE) - messageLength;

                            // Если > 0, то нужно будет читать ещё
                            if (hasBytes > 0) {
                                packet.readBuffer.position(hasBytes);
                                if(hasBytes < Packet.LENGTH_SIZE)
                                    packet.readBuffer.limit(MESSAGE_SIZE);
                                else
                                    packet.readBuffer.limit(hasBytes);

                                System.out.println(Thread.currentThread().getName() + " [" + 0 + "] left bytes: "
                                        + ((position - Packet.LENGTH_SIZE) - messageLength));
                            }
                        } else {
                            // Сообщение пришло не полностью
                            packet.readBuffer.position(position);
                            packet.readBuffer.limit(MESSAGE_SIZE);
                            hasBytes = position;
                            System.out.println(Thread.currentThread().getName() + " [" + 0 + "] Packet is not full");
                            break;
                        }
                    } while (hasBytes >= Packet.LENGTH_SIZE);
                } catch (IOException e) {
                    logger.error("Exception: ", e);
                    break;
                }
            }
        }
    }
}
