package com.team8.splitsmart.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private static final String TABLE = "users";

    private final SplitSmartDatabase db;

    public UserRepository(SplitSmartDatabase db) {
        this.db = db;
    }

    public User save(User user) {
        JsonObject row = new JsonObject();
        row.addProperty("name", user.name);
        row.addProperty("email", user.email);
        row.addProperty("usc_id", user.uscId);
        row.addProperty("password_hash", user.passwordHash);
        JsonArray inserted = db.insert(TABLE, row);
        return db.gson().fromJson(inserted.get(0), User.class);
    }

    public User findByEmail(String email) {
        return first(db.select(TABLE, "email", "eq." + email));
    }

    public User findById(long userId) {
        return first(db.select(TABLE, "user_id", "eq." + userId));
    }

    /** A group's members, ordered by join date and then user ID. */
    public List<User> findByGroup(long groupId) {
        JsonArray rows = db.select("group_memberships",
                "select", "users(*)",
                "group_id", "eq." + groupId,
                "order", "joined_date.asc,user_id.asc");
        List<User> users = new ArrayList<>();
        for (JsonElement row : rows) {
            users.add(db.gson().fromJson(row.getAsJsonObject().get("users"), User.class));
        }
        return users;
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email) != null;
    }

    public boolean existsByUscId(String uscId) {
        return first(db.select(TABLE, "usc_id", "eq." + uscId)) != null;
    }

    public User updateName(long userId, String name) {
        JsonObject changes = new JsonObject();
        changes.addProperty("name", name);
        return first(db.update(TABLE, changes, "user_id", "eq." + userId));
    }

    private User first(JsonArray rows) {
        return rows.size() == 0 ? null : db.gson().fromJson(rows.get(0), User.class);
    }
}
