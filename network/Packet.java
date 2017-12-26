package network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

// Экземпляет сообщения
public class Packet {
    private static final Logger logger = LoggerFactory.getLogger(Packet.class.getName());

    public static final int LENGTH_SIZE = 4; // кол-во байт выделенные под длинну сообщения
    public static final int TYPE_SIZE = 4; // кол-во байт выделенные под длинну типа сообщения
    public static final int HEADER_SIZE = LENGTH_SIZE + TYPE_SIZE; // кол-во байт заголовка (длинна+ИД+тип)

    private final int MAX_PACKET_SIZE; // максимальная длинна сообщения

    // поля данных сообщения
    private int packetLength = 0; // длинна тела сообщения
    private int packetType = 0; // тип сообщения
    private byte[] packetBody = null; // тело сообщения

    public ByteBuffer readBuffer = null; // буфер для сбора сообщения по частям

    // Конструктор используется для копирования сообщения
    public Packet() {
        this.MAX_PACKET_SIZE = 0;
    }

    // Конструктор используется для создание нового сообщения и считывания из канала
    public Packet(int bufferSize) throws IOException {
        if(bufferSize > 0 && bufferSize <= Integer.MAX_VALUE) { // проверка валидности размера буфера
            this.MAX_PACKET_SIZE = bufferSize; // устанавливаем максимальную длинну сообщения
            readBuffer = ByteBuffer.allocate(bufferSize); // выделяем память
        }
        else
            throw new IOException("Invalid buffer size. Must be > 0");
    }

    // геттер длинны сообщения
    public int getPacketLength() {
        return packetLength;
    }

    // геттер ТИПа сообщения
    public int getPacketType() {
        return packetType;
    }

    // геттер ТЕЛА сообщения
    public byte[] getPacketBody() {
        return packetBody;
    }

    // Метод заполняет поля сообщения
    public void setPacket(int type, byte[] messageBody)
            throws IOException {
        if(type >= 0 && messageBody != null) {
            this.packetType = type;
            this.packetBody = messageBody;
            this.packetLength = this.packetBody.length + TYPE_SIZE;
        }
        else
            throw new IOException("Invalid ID or TYPE or Packet Body. Must be > 0");
    }

    public String getPacketBodyStr() {
        String result = null;
        try {
            result = new String(this.packetBody, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.debug("Exception: ", e);
        }
        return result;
    }

    // метод читает данные из readBuffer в соответствующие поля класса
    public void readBuffer(int messageLength) throws IOException {
        if(this.readBuffer == null) // проверяем валидность
            throw new IOException("Invalid readBuffer. Must be not null");
        if(messageLength <= 0 || messageLength > MAX_PACKET_SIZE) // проверяем валидность
            throw new IOException("Invalid packetLength. Must be: 0 < packetLength < " + MAX_PACKET_SIZE);

        this.packetLength = messageLength; // запонимаем длинну пакета
        this.readBuffer.position(LENGTH_SIZE); // устанавливаем позицию на 5й байт
        this.packetType = this.readBuffer.getInt(); // запоминаем тип пакета

        this.packetBody = new byte[this.packetLength - TYPE_SIZE]; // выделяем память под тело сообщения
        this.readBuffer.get(this.packetBody); // запоминаем тело сообщения

        // удаляем из буфера прочитанное сообщение из начала и сдвигаем весь массив
        int endBuffer = this.readBuffer.limit() - (messageLength + LENGTH_SIZE);
        byte[] cutBuffer = this.readBuffer.array();
        this.readBuffer.position(0);
        this.readBuffer.put(cutBuffer, messageLength + LENGTH_SIZE, endBuffer);

        this.readBuffer.clear(); // очищаем буфер
    }

    // преобразовываем Packet в ByteBuffer
    public ByteBuffer getByteBufferMessage() {
        ByteBuffer writeBuffer = ByteBuffer.allocate(HEADER_SIZE + this.packetLength);
        byte[] length = ByteBuffer.allocate(LENGTH_SIZE).putInt(this.packetLength).array(); // формирует byte[] из длинны сообщения
        byte[] type = ByteBuffer.allocate(TYPE_SIZE).putInt(this.packetType).array(); // формирует byte[] из типа сообщения

        writeBuffer.clear(); // очищаем буфер
        writeBuffer.put(length); // записываем длинну сообщения
        writeBuffer.put(type); // записываем тип сообщения
        writeBuffer.put(this.packetBody); // записываем сообщение
        writeBuffer.flip(); // выставляем размер буфера в соовествии с размером записанных данных

        return writeBuffer;
    }

    // преобразовываем Packet в byte[]
    public byte[] getByteArrayMessage() throws IOException {
        if(this.packetLength <= 0 || this.packetBody == null) {
            throw new IOException("Invalid message");
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(ByteBuffer.allocate(LENGTH_SIZE).putInt(this.packetLength).array());
        baos.write(ByteBuffer.allocate(TYPE_SIZE).putInt(this.packetType).array());
        baos.write(this.packetBody);
        return baos.toByteArray();
    }

    // очищаем поля класса
    public void clear() {
        this.packetLength = 0;
        this.packetType = 0;
        this.packetBody = null;
        this.readBuffer.clear();
    }

    public Packet clone() {
        Packet newPacket = new Packet();
        newPacket.packetLength = this.packetLength;
        newPacket.packetType = this.packetType;
        newPacket.packetBody = this.packetBody;
        return newPacket;
    }

    @Override
    public String toString() {
        String result = "";
        try {
            result = new String("[" + Integer.toString(this.packetLength) + ",");
            result += new String(Integer.toString(this.packetType) + "] ");
            result += new String(this.packetBody, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.debug("Exception: ", e);
        }
        return result;
    }
}
