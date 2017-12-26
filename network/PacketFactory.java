package network;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PacketFactory {
    //Ассоциативный массив: класс сообщения => идентификатор сообщения
    private static final Map<Class<? extends Packet>, Integer> idMap = new HashMap<>();

    static {
        //idMap.put(Packet00HandshakeRequest.class, Packet00HandshakeRequest.messageId);
    }

    private PacketFactory() {
    }

    //Создает сообщение по идентификатору
    public static Packet createMessage(int packetId) throws IOException {

        switch (packetId) {
            //case Packet00HandshakeRequest.messageId:
            //    return new Packet00HandshakeRequest();

            default:
                throw new IOException("Unknown message type " + packetId);
        }
    }

    public static int getMessageId(final Packet packet) {
        Integer id = idMap.get(packet.getClass());
        return id.intValue();
    }
}
