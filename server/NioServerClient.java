package server;

import network.Message;
import network.MessageIO;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

// Класс экземпляра клиента на сервере
public class NioServerClient implements Runnable {
    private final SelectionKey clientKey; // ключ
    private final MessageIO messageIO; // класс обеспечивает приём и отправку сообщений Message

    public NioServerClient(SelectionKey clientKey) {
        this.clientKey = clientKey;
        messageIO = new MessageIO(this.clientKey);
    }

    @Override
    public void run() {
        Message message;

        try {
            message = messageIO.read();
            this.processingMessage(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void read() {
        Message message;

        try {
            message = messageIO.read();
            if(message != null)
                this.processingMessage(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write() {
        messageIO.write();
    }

    // Метод отвечающий за обработку входящих сообщений
    private void processingMessage(Message message) throws UnsupportedEncodingException {

        System.out.println("Server send: " + message);

        if("quit".equals(message.toString().toLowerCase())) {
            System.out.println("Client was disconnected");
            messageIO.closeChannel();
            return;
        }

        messageIO.outgoingBuffer.add(message); // кладем готовое сообщениев очередь исходящих сообщений
        this.clientKey.interestOps(SelectionKey.OP_WRITE); // выставляем флаг о том что необходимо отправить данные
    }

}
