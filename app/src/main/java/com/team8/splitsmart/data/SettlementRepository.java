package com.team8.splitsmart.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SettlementRepository {
    private static final String TABLE = "settlements";

    private final SplitSmartDatabase db;

    public SettlementRepository(SplitSmartDatabase db) {
        this.db = db;
    }

    public Settlement save(Settlement settlement) {
        JsonObject row = new JsonObject();
        row.addProperty("group_id", settlement.groupId);
        row.addProperty("payer_id", settlement.payerId);
        row.addProperty("payee_id", settlement.payeeId);
        row.addProperty("amount_cents", settlement.amountCents);
        row.addProperty("date", settlement.date.toString());
        row.addProperty("note", settlement.note);
        row.addProperty("created_by", settlement.createdBy);
        return db.gson().fromJson(db.insert(TABLE, row).get(0), Settlement.class);
    }

    /** Updates the editable fields (amount and note); payer, payee and date stay fixed. */
    public Settlement update(Settlement settlement) {
        JsonObject changes = new JsonObject();
        changes.addProperty("amount_cents", settlement.amountCents);
        changes.addProperty("note", settlement.note);
        JsonArray rows = db.update(TABLE, changes, "settlement_id", "eq." + settlement.settlementId);
        return rows.size() == 0 ? null : db.gson().fromJson(rows.get(0), Settlement.class);
    }

    public void delete(long settlementId) {
        db.delete(TABLE, "settlement_id", "eq." + settlementId);
    }

    public Settlement findById(long settlementId) {
        JsonArray rows = db.select(TABLE, "settlement_id", "eq." + settlementId);
        return rows.size() == 0 ? null : db.gson().fromJson(rows.get(0), Settlement.class);
    }

    public List<Settlement> findByGroup(long groupId) {
        JsonArray rows = db.select(TABLE,
                "group_id", "eq." + groupId,
                "order", "date.desc,created_at.desc");
        List<Settlement> settlements = new ArrayList<>();
        for (JsonElement row : rows) {
            settlements.add(db.gson().fromJson(row, Settlement.class));
        }
        return settlements;
    }
}
