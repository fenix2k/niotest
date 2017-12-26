package network;

import network.packets.Packet100Message;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PacketFactory {
    //Ассоциативный массив: класс сообщения => идентификатор сообщения
    private static final Map<Class<? extends PacketBase>, Integer> idMap = new HashMap<>();

    static {
        idMap.put(Packet100Message.class, Packet100Message.type);
    }

    private PacketFactory() {
    }

    //Создает сообщение по идентификатору
    public static PacketBase createPacket(PacketBase packet) throws IOException {

        switch (packet.getPacketType()) {
            case Packet100Message.type:
                return new Packet100Message();

            default:
                throw new IOException("Unknown message type " + packet.getPacketType());
        }

    }

    public static int getPacketType(final PacketBase packetBase) {
        Integer type = idMap.get(packetBase.getClass());
        return type.intValue();
    }
}
