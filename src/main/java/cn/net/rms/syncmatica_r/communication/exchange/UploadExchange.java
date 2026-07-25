package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.PacketType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.*;

public class UploadExchange extends AbstractExchange {

    private static final int BUFFER_SIZE = 16384;

    private final ServerPlacement toUpload;
    private final InputStream inputStream;
    private final byte[] buffer = new byte[BUFFER_SIZE];

    public UploadExchange(final ServerPlacement syncmatic, final File uploadFile, final ExchangeTarget partner, final Context con) throws IOException {
        super(partner, con);
        if (uploadFile == null || !uploadFile.isFile()) {
            throw new FileNotFoundException("Litematic file is unavailable");
        }
        final long limit = con.getMaxTransferBytes();
        if (uploadFile.length() > limit) {
            throw new TransferLimitExceededException(uploadFile.length(), limit);
        }
        toUpload = syncmatic;
        inputStream = new FileInputStream(uploadFile);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.RECEIVED_LITEMATIC
                || type == PacketType.CANCEL_LITEMATIC) {
            return checkUUID(packetBuf, toUpload.getId());
        }
        return false;
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {

        packetBuf.readUuid();
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.RECEIVED_LITEMATIC) {
            send();
        }
        if (type == PacketType.CANCEL_LITEMATIC) {
            close(false);
        }
    }

    private void send() {

        final int bytesRead;
        try {
            bytesRead = inputStream.read(buffer);
        } catch (final IOException e) {
            close(true);
            e.printStackTrace();
            return;
        }
        if (bytesRead == -1) {
            sendFinish();
        } else {
            sendData(bytesRead);
        }
    }

    private void sendData(final int bytesRead) {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toUpload.getId());
        packetByteBuf.writeInt(bytesRead);
        packetByteBuf.writeBytes(buffer, 0, bytesRead);
        getPartner().sendPacket(PacketType.SEND_LITEMATIC.toIdentifier(getPartner().getProtocolFlavor()), packetByteBuf, getContext());
    }

    private void sendFinish() {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toUpload.getId());
        getPartner().sendPacket(PacketType.FINISHED_LITEMATIC.toIdentifier(getPartner().getProtocolFlavor()), packetByteBuf, getContext());
        succeed();
    }

    @Override
    public void init() {
        send();
    }

    @Override
    protected void onClose() {
        try {
            inputStream.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void sendCancelPacket() {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toUpload.getId());
        getPartner().sendPacket(PacketType.CANCEL_LITEMATIC.toIdentifier(getPartner().getProtocolFlavor()), packetByteBuf, getContext());
    }

    /**
     * Distinguishes a refused transfer from a missing or unreadable file so the
     * caller can tell the user which limit blocked the exchange.
     */
    public static final class TransferLimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;

        private final long fileBytes;
        private final long limitBytes;

        TransferLimitExceededException(final long fileBytes, final long limitBytes) {
            super("Litematic file exceeds the configured transfer limit: " + fileBytes + " > " + limitBytes);
            this.fileBytes = fileBytes;
            this.limitBytes = limitBytes;
        }

        public long getFileBytes() {
            return fileBytes;
        }

        public long getLimitBytes() {
            return limitBytes;
        }
    }

}
