package cn.net.rms.syncmatica_r.extended_core;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.UUID;

public class PlayerIdentifier {
    public static final UUID MISSING_PLAYER_UUID = UUID.fromString("4c1b738f-56fa-4011-8273-498c972424ea");
    public static final PlayerIdentifier MISSING_PLAYER = new PlayerIdentifier(MISSING_PLAYER_UUID, "No Player");

    public final UUID uuid;
    private String bufferedPlayerName;

    public PlayerIdentifier(final UUID uuid, final String bufferedPlayerName) {
        this.uuid = uuid;
        this.bufferedPlayerName = bufferedPlayerName;
    }

    public String getName() {
        return bufferedPlayerName;
    }

    public void updatePlayerName(final String name) {
        bufferedPlayerName = name;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerIdentifier)) {
            return false;
        }
        final PlayerIdentifier player = (PlayerIdentifier) other;
        return uuid.equals(player.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    public JsonObject toJson() {
        final JsonObject jsonObject = new JsonObject();

        jsonObject.add("uuid", new JsonPrimitive(uuid.toString()));
        jsonObject.add("name", new JsonPrimitive(bufferedPlayerName));

        return jsonObject;
    }
}
