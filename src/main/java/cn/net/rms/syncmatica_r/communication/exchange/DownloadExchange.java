package cn.net.rms.syncmatica_r.communication.exchange;

import cn.net.rms.syncmatica_r.Context;
import cn.net.rms.syncmatica_r.ServerPlacement;
import cn.net.rms.syncmatica_r.communication.ExchangeTarget;
import cn.net.rms.syncmatica_r.communication.MessageType;
import cn.net.rms.syncmatica_r.communication.PacketType;
import cn.net.rms.syncmatica_r.communication.ProtocolLimits;
import cn.net.rms.syncmatica_r.communication.ServerCommunicationManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class DownloadExchange extends AbstractExchange {
    private static final Logger LOGGER = LogManager.getLogger(DownloadExchange.class);

    private final ServerPlacement toDownload;
    private final OutputStream outputStream;
    private final MessageDigest md5;
    private final File downloadFile;
    private long bytesSent;

    public DownloadExchange(final ServerPlacement syncmatic, final File downloadFile, final ExchangeTarget partner, final Context context) throws IOException, NoSuchAlgorithmException {
        super(partner, context);
        this.downloadFile = downloadFile;
        final OutputStream os = new FileOutputStream(downloadFile);
        toDownload = syncmatic;
        md5 = MessageDigest.getInstance("MD5");
        outputStream = new DigestOutputStream(os, md5);
    }

    @Override
    public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.SEND_LITEMATIC
                || type == PacketType.FINISHED_LITEMATIC
                || type == PacketType.CANCEL_LITEMATIC) {
            return checkUUID(packetBuf, toDownload.getId());
        }
        return false;
    }

    @Override
    public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        packetBuf.readUuid();
        final PacketType type = PacketType.fromIdentifier(id);
        if (type == PacketType.SEND_LITEMATIC) {
            final int size = ProtocolLimits.requireTransferChunk(packetBuf.readInt(), packetBuf.readableBytes());
            final long transferLimit = getContext().getMaxTransferBytes();
            if (bytesSent > transferLimit - size) {
                close(true);
                sendServerError("syncmatica_r.error.cancelled_transmit_exceed_limit");
                return;
            }
            if (getContext().isServer() && !getContext().getQuotaService().tryConsume(getPartner(), size)) {
                close(true);
                sendServerError("syncmatica_r.error.cancelled_transmit_exceed_quota");
                return;
            }
            bytesSent += size;
            try {
                packetBuf.readBytes(outputStream, size);
            } catch (final IOException e) {
                close(true);
                e.printStackTrace();
                return;
            }
            final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
            packetByteBuf.writeUuid(toDownload.getId());
            getPartner().sendPacket(PacketType.RECEIVED_LITEMATIC.toIdentifier(getPartner().getProtocolFlavor()), packetByteBuf, getContext());
            return;
        }
        if (type == PacketType.FINISHED_LITEMATIC) {
            try {
                outputStream.flush();
            } catch (final IOException e) {
                close(false);
                e.printStackTrace();
                return;
            }
            final UUID downloadHash = UUID.nameUUIDFromBytes(md5.digest());
            if (downloadHash.equals(toDownload.getHash())) {
                succeed();
            } else {

                close(false);
            }
            return;
        }
        if (type == PacketType.CANCEL_LITEMATIC) {
            close(false);
        }
    }

    @Override
    public void init() {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toDownload.getId());
        getPartner().sendPacket(PacketType.REQUEST_LITEMATIC.toIdentifier(getPartner().getProtocolFlavor()), packetByteBuf, getContext());
    }

    @Override
    protected void onClose() {
        getManager().setDownloadState(toDownload, false);
        try {
            outputStream.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        if (!isSuccessful() && downloadFile.exists() && !downloadFile.delete()) {
            LOGGER.warn("Failed to delete incomplete litematic file {}", downloadFile.getAbsolutePath());
        }
    }

    @Override
    protected void sendCancelPacket() {
        final PacketByteBuf packetByteBuf = new PacketByteBuf(Unpooled.buffer());
        packetByteBuf.writeUuid(toDownload.getId());
        getPartner().sendPacket(PacketType.CANCEL_LITEMATIC.toIdentifier(getPartner().getProtocolFlavor()), packetByteBuf, getContext());
    }

    public ServerPlacement getPlacement() {
        return toDownload;
    }

    private void sendServerError(final String message) {
        if (getContext().isServer()) {
            ((ServerCommunicationManager) getContext().getCommunicationManager()).sendMessage(
                    getPartner(),
                    MessageType.ERROR,
                    message
            );
        }
    }

}
