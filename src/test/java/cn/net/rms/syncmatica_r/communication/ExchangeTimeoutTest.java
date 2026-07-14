package cn.net.rms.syncmatica_r.communication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.net.rms.syncmatica_r.communication.exchange.AbstractExchange;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

final class ExchangeTimeoutTest {
    @Test
    void activeExchangeExpiresAndFinishedExchangeDoesNot() {
        final StubExchange exchange = new StubExchange();
        final long now = System.currentTimeMillis();

        assertFalse(exchange.isTimedOut(now));
        assertTrue(exchange.isTimedOut(now + ProtocolLimits.EXCHANGE_TIMEOUT_MILLIS + 1L));

        exchange.close(false);

        assertFalse(exchange.isTimedOut(Long.MAX_VALUE));
    }

    private static final class StubExchange extends AbstractExchange {
        private StubExchange() {
            super(null, null);
        }

        @Override
        public boolean checkPacket(final Identifier id, final PacketByteBuf packetBuf) {
            return false;
        }

        @Override
        public void handle(final Identifier id, final PacketByteBuf packetBuf) {
        }

        @Override
        public void init() {
        }
    }
}
