package com.team8.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team8.splitsmart.R;
import com.team8.splitsmart.SplitSmartApp;
import com.team8.splitsmart.data.Group;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.usergroup.AuthService;
import com.team8.splitsmart.usergroup.GroupService;

import java.util.List;
import java.util.Locale;

/**
 * Two modes: without {@link #EXTRA_GROUP_ID} it lists the user's groups (create group, profile, log out);
 * with it, it shows that group's members (owner can add members, edit or delete the group).
 */
public class GroupActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";

    private GroupService groupService;
    private AuthService authService;
    private User user;
    private long groupId;
    private Group group;

    private SwipeRefreshLayout swipe;
    private TextView emptyText;
    private ExtendedFloatingActionButton fab;
    private BindingAdapter<Group> groupAdapter;
    private BindingAdapter<User> memberAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = Screens.requireUser(this);
        if (user == null) {
            return;
        }
        setContentView(R.layout.activity_list);
        groupService = SplitSmartApp.get().groups();
        authService = SplitSmartApp.get().auth();
        groupId = getIntent().getLongExtra(EXTRA_GROUP_ID, 0);

        swipe = findViewById(R.id.swipe);
        emptyText = findViewById(R.id.emptyText);
        fab = findViewById(R.id.fab);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        swipe.setOnRefreshListener(this::load);

        if (isMembersMode()) {
            setTitle(R.string.members);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            memberAdapter = new BindingAdapter<>(R.layout.item_two_line, this::bindMember, null);
            list.setAdapter(memberAdapter);
            fab.setText(R.string.add_member);
            fab.setOnClickListener(v -> onAddMemberClicked());
            fab.setVisibility(View.GONE);
        } else {
            setTitle(R.string.my_groups);
            groupAdapter = new BindingAdapter<>(R.layout.item_two_line, this::bindGroup, this::openGroup);
            list.setAdapter(groupAdapter);
            fab.setText(R.string.new_group);
            fab.setOnClickListener(v -> onCreateGroupClicked());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (user != null) {
            load();
        }
    }

    private boolean isMembersMode() {
        return groupId != 0;
    }

    private boolean isOwner() {
        return group != null && group.ownerId == user.userId;
    }

    private void load() {
        swipe.setRefreshing(true);
        if (isMembersMode()) {
            UiAsync.run(this, () -> new GroupData(groupService.getGroup(groupId), groupService.getMembers(groupId)),
                    data -> {
                        swipe.setRefreshing(false);
                        group = data.group;
                        showMembers(data.members);
                    }, message -> {
                        swipe.setRefreshing(false);
                        UiAsync.showError(this, message);
                    });
        } else {
            UiAsync.run(this, () -> groupService.getGroupsFor(user), groups -> {
                swipe.setRefreshing(false);
                groupAdapter.setItems(groups);
                showEmpty(groups.isEmpty(), getString(R.string.no_groups));
            }, message -> {
                swipe.setRefreshing(false);
                UiAsync.showError(this, message);
            });
        }
    }

    // ----- Group list mode -----

    private void bindGroup(View row, Group item) {
        ((TextView) row.findViewById(R.id.avatar)).setText(initial(item.name));
        ((TextView) row.findViewById(R.id.primary)).setText(item.name);
        TextView secondary = row.findViewById(R.id.secondary);
        secondary.setText(item.description != null ? item.description : "Created " + item.createdDate);
        ((TextView) row.findViewById(R.id.trailing)).setText(item.ownerId == user.userId ? "Owner" : "");
    }

    private void openGroup(Group item) {
        Intent intent = new Intent(this, HistoryActivity.class);
        intent.putExtra(HistoryActivity.EXTRA_GROUP_ID, item.groupId);
        startActivity(intent);
    }

    public void onCreateGroupClicked() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_group, null);
        TextInputLayout nameLayout = view.findViewById(R.id.firstLayout);
        TextInputLayout descriptionLayout = view.findViewById(R.id.secondLayout);
        nameLayout.setHint(R.string.group_name);
        descriptionLayout.setHint(R.string.description_optional);
        TextInputEditText nameInput = view.findViewById(R.id.firstInput);
        TextInputEditText descriptionInput = view.findViewById(R.id.secondInput);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_group)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = AuthActivity.text(nameInput);
            String description = AuthActivity.text(descriptionInput);
            UiAsync.run(this, () -> groupService.createGroup(name, description, user), created -> {
                dialog.dismiss();
                openGroup(created);
            }, nameLayout::setError);
        });
    }

    private void onProfileClicked() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_group, null);
        TextInputLayout nameLayout = view.findViewById(R.id.firstLayout);
        nameLayout.setHint(R.string.name);
        view.findViewById(R.id.secondLayout).setVisibility(View.GONE);
        TextInputEditText nameInput = view.findViewById(R.id.firstInput);
        nameInput.setText(user.name);
        TextView details = view.findViewById(R.id.extraText);
        details.setVisibility(View.VISIBLE);
        details.setText(String.format(Locale.US, "Email: %s\nUSC ID: %s\n\nEmail and USC ID cannot be changed.",
                user.email, user.uscId));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = AuthActivity.text(nameInput);
            UiAsync.run(this, () -> authService.updateName(name), updated -> {
                user = updated;
                dialog.dismiss();
                UiAsync.showError(this, "Name updated");
            }, nameLayout::setError);
        });
    }

    private void onLogoutClicked() {
        authService.logout();
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ----- Members mode -----

    public void showMembers(List<User> members) {
        setTitle(group.name);
        View header = findViewById(R.id.header);
        header.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.headerTitle)).setText(
                String.format(Locale.US, "%d member%s", members.size(), members.size() == 1 ? "" : "s"));
        String ownerName = "";
        for (User member : members) {
            if (member.userId == group.ownerId) {
                ownerName = member.name;
            }
        }
        StringBuilder body = new StringBuilder();
        if (group.description != null) {
            body.append(group.description).append("\n");
        }
        body.append("Owner: ").append(ownerName).append(" · Created ").append(group.createdDate);
        ((TextView) findViewById(R.id.headerBody)).setText(body);

        memberAdapter.setItems(members);
        fab.setVisibility(isOwner() ? View.VISIBLE : View.GONE);
        invalidateOptionsMenu();
    }

    private void bindMember(View row, User member) {
        ((TextView) row.findViewById(R.id.avatar)).setText(initial(member.name));
        String name = member.userId == user.userId ? member.name + " (you)" : member.name;
        ((TextView) row.findViewById(R.id.primary)).setText(name);
        ((TextView) row.findViewById(R.id.secondary)).setText(member.email);
        ((TextView) row.findViewById(R.id.trailing)).setText(member.userId == group.ownerId ? "Owner" : "");
    }

    public void onAddMemberClicked() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_group, null);
        TextInputLayout emailLayout = view.findViewById(R.id.firstLayout);
        emailLayout.setHint(R.string.usc_email);
        view.findViewById(R.id.secondLayout).setVisibility(View.GONE);
        TextInputEditText emailInput = view.findViewById(R.id.firstInput);
        emailInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_member)
                .setView(view)
                .setPositiveButton(R.string.add_member, null)
                .setNegativeButton(R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = AuthActivity.text(emailInput);
            UiAsync.run(this, () -> groupService.addMember(groupId, user, email), members -> {
                dialog.dismiss();
                showMembers(members);
                UiAsync.showError(this, "Member added");
            }, emailLayout::setError);
        });
    }

    private void onEditGroupClicked() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_group, null);
        TextInputLayout nameLayout = view.findViewById(R.id.firstLayout);
        ((TextInputLayout) view.findViewById(R.id.secondLayout)).setHint(R.string.description_optional);
        nameLayout.setHint(R.string.group_name);
        TextInputEditText nameInput = view.findViewById(R.id.firstInput);
        TextInputEditText descriptionInput = view.findViewById(R.id.secondInput);
        nameInput.setText(group.name);
        descriptionInput.setText(group.description);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_group)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = AuthActivity.text(nameInput);
            String description = AuthActivity.text(descriptionInput);
            UiAsync.run(this, () -> groupService.editGroup(groupId, user, name, description), updated -> {
                dialog.dismiss();
                load();
            }, nameLayout::setError);
        });
    }

    private void onDeleteGroupClicked() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete \"" + group.name + "\"?")
                .setMessage("This permanently deletes the group and all of its expenses and settlements "
                        + "for every member.")
                .setPositiveButton(R.string.delete, (d, which) ->
                        UiAsync.run(this, () -> {
                            groupService.deleteGroup(groupId, user);
                            return true;
                        }, ok -> {
                            Intent intent = new Intent(this, GroupActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ----- Shared -----

    private void showEmpty(boolean empty, String message) {
        emptyText.setText(message);
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    static String initial(String name) {
        return name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.US);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isMembersMode()) {
            if (isOwner()) {
                getMenuInflater().inflate(R.menu.menu_members, menu);
            }
        } else {
            getMenuInflater().inflate(R.menu.menu_groups, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            onProfileClicked();
        } else if (id == R.id.action_logout) {
            onLogoutClicked();
        } else if (id == R.id.action_edit_group) {
            onEditGroupClicked();
        } else if (id == R.id.action_delete_group) {
            onDeleteGroupClicked();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static class GroupData {
        final Group group;
        final List<User> members;

        GroupData(Group group, List<User> members) {
            this.group = group;
            this.members = members;
        }
    }
}
