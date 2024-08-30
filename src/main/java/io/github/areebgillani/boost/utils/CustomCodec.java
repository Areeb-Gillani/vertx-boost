package io.github.areebgillani.boost.utils;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public class CustomCodec<T> implements MessageCodec<T, T> {
    Logger logger = LoggerFactory.getLogger(CustomCodec.class);
    private final Class<T> type;
    public CustomCodec(Class<T> type) {
        super();
        this.type = type;
    }
    @Override
    public void encodeToWire(Buffer buffer, T s) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutput out = null;
            out = new ObjectOutputStream(bos);
            out.writeObject(s);
            out.flush();
            byte[] yourBytes = bos.toByteArray();
            buffer.appendInt(yourBytes.length);
            buffer.appendBytes(yourBytes);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T decodeFromWire(int pos, Buffer buffer) {
        int _pos = pos;
        int length = buffer.getInt(_pos);
        byte[] yourBytes = buffer.getBytes(_pos += 4, _pos += length);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(yourBytes)) {
            ObjectInputStream ois = new ObjectInputStream(bis);
            @SuppressWarnings("unchecked")
            T msg = (T) ois.readObject();
            ois.close();
            return msg;
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Listen failed " + e.getMessage());
        }
        return null;
    }

    @Override
    public T transform(T customMessage) {
        return customMessage;
    }

    @Override
    public String name() {
        return type.getSimpleName()+"Codec";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
