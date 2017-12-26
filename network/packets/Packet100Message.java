package network.packets;

import network.PacketBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;

public class Packet100Message extends PacketBase {
    private static final Logger logger = LoggerFactory.getLogger(Packet100Message.class.getName());
    public static final int type = 100;
    public String message;

    public Packet100Message() {
    }

    @Override
    public void writePacketBody() {
        try {
            setPacketBody(this.message.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            logger.debug("Exception: ", e);
        }
    }

    @Override
    public void readPacketBody() {
        try {
            this.message = new String(getPacketBody(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.debug("Exception: ", e);
        }
    }
}
