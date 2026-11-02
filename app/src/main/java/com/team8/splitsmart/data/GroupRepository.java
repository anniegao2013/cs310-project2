package com.team8.splitsmart.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class GroupRepository {
    private static final String GROUPS = "expense_groups";
    private static final String MEMBERSHIPS = "group_memberships";

    private final SplitSmartDatabase db;

    public GroupRepository(SplitSmartDatabase db) {
        this.db = db;
    }

    /** Saves a new group and adds its owner as the first member in one transaction. */
    public Group save(Group group) {
        JsonObject args = new JsonObject();
        args.addProperty("p_name", group.name);
        args.addProperty("p_description", group.description);
        args.addProperty("p_owner_id", group.ownerId);
        args.addProperty("p_created_date", group.createdDate.toString());
        JsonArray rows = db.rpc("create_group_with_owner", args).getAsJsonArray();
        return db.gson().fromJson(rows.get(0), Group.class);
    }

    public Group update(Group group) {
        JsonObject changes = new JsonObject();
        changes.addProperty("name", group.name);
        changes.addProperty("description", group.description);
        JsonArray rows = db.update(GROUPS, changes, "group_id", "eq." + group.groupId);
        return rows.size() == 0 ? null : db.gson().fromJson(rows.get(0), Group.class);
    }

    public Group findById(long groupId) {
        JsonArray rows = db.select(GROUPS, "group_id", "eq." + groupId);
        return rows.size() == 0 ? null : db.gson().fromJson(rows.get(0), Group.class);
    }

    public void addMember(long groupId, long userId) {
        GroupMembership membership = new GroupMembership();
        membership.groupId = groupId;
        membership.userId = userId;
        membership.joinedDate = LocalDate.now();
        db.insert(MEMBERSHIPS, db.gson().toJsonTree(membership));
    }

    /** Members, ordered by join date and then user ID. */
    public List<User> getMembers(long groupId) {
        JsonArray rows = db.select(MEMBERSHIPS,
                "select", "users(*)",
                "group_id", "eq." + groupId,
                "order", "joined_date.asc,user_id.asc");
        List<User> members = new ArrayList<>();
        for (JsonElement row : rows) {
            members.add(db.gson().fromJson(row.getAsJsonObject().get("users"), User.class));
        }
        return members;
    }

    public boolean isMember(long groupId, long userId) {
        return db.select(MEMBERSHIPS, "group_id", "eq." + groupId, "user_id", "eq." + userId).size() > 0;
    }

    public List<Group> findGroupsForUser(long userId) {
        JsonArray rows = db.select(MEMBERSHIPS,
                "select", "expense_groups(*)",
                "user_id", "eq." + userId,
                "order", "group_id.desc");
        List<Group> groups = new ArrayList<>();
        for (JsonElement row : rows) {
            groups.add(db.gson().fromJson(row.getAsJsonObject().get("expense_groups"), Group.class));
        }
        return groups;
    }

    /** Deletes the group; its memberships, expenses, splits and settlements cascade in the database. */
    public void delete(long groupId) {
        db.delete(GROUPS, "group_id", "eq." + groupId);
    }
}
