package org.bukkit.craftbukkit.v1_20_R1.ban;

import com.mojang.authlib.GameProfile;
import java.time.Instant;
import java.util.Date;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import org.bukkit.craftbukkit.v1_20_R1.profile.CraftPlayerProfile;
import org.bukkit.profile.PlayerProfile;

public final class CraftProfileBanEntry implements org.bukkit.BanEntry<PlayerProfile> {
    private static final Date minorDate = Date.from(Instant.parse("1899-12-31T04:00:00Z"));
    private final UserBanList list;
    private final GameProfile profile;
    private Date created;
    private String source;
    private Date expiration;
    private String reason;

    public CraftProfileBanEntry(GameProfile profile, UserBanListEntry entry, UserBanList list) {
        this.list = list;
        this.profile = profile;
        this.created = entry.getCreated() != null ? new Date(entry.getCreated().getTime()) : null;
        this.source = entry.getSource();
        this.expiration = entry.getExpires() != null ? new Date(entry.getExpires().getTime()) : null;
        this.reason = entry.getReason();
    }

    @Override
    public String getTarget() {
        return this.profile.getName();
    }

    @Override
    public PlayerProfile getBanTarget() {
        return new CraftPlayerProfile(this.profile);
    }

    @Override
    public Date getCreated() {
        return this.created == null ? null : (Date) this.created.clone();
    }

    @Override
    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public String getSource() {
        return this.source;
    }

    @Override
    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public Date getExpiration() {
        return this.expiration == null ? null : (Date) this.expiration.clone();
    }

    @Override
    public void setExpiration(Date expiration) {
        if (expiration != null && expiration.getTime() == minorDate.getTime()) {
            expiration = null; // Forces "forever"
        }

        this.expiration = expiration;
    }

    @Override
    public String getReason() {
        return this.reason;
    }

    @Override
    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public void save() {
        UserBanListEntry entry = new UserBanListEntry(this.profile, this.created, this.source, this.expiration, this.reason);
        this.list.add(entry);
    }

    @Override
    public void remove() {
        this.list.remove(this.profile);
    }
}
