package com.team8.splitsmart.usergroup;

import com.team8.splitsmart.ValidationException;
import com.team8.splitsmart.data.Group;
import com.team8.splitsmart.data.GroupRepository;
import com.team8.splitsmart.data.User;
import com.team8.splitsmart.data.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Creates groups, adds members, edits and deletes groups; enforces owner-only actions. */
public class GroupService {
    private final GroupRepository groupRepo;
    private final UserRepository userRepo;

    public GroupService(GroupRepository groupRepo, UserRepository userRepo) {
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
    }

    public Group createGroup(String name, String description, User owner) {
        Group group = new Group();
        group.name = requireName(name);
        group.description = blankToNull(description);
        group.ownerId = owner.userId;
        group.createdDate = LocalDate.now();
        return groupRepo.save(group);
    }

    public List<User> addMember(long groupId, User requester, String email) {
        requireOwner(groupId, requester);
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.US);
        if (cleanEmail.isEmpty()) {
            throw new ValidationException("Enter the member's USC email");
        }
        User user = userRepo.findByEmail(cleanEmail);
        if (user == null) {
            throw new ValidationException("No registered user has the email " + cleanEmail);
        }
        if (groupRepo.isMember(groupId, user.userId)) {
            throw new ValidationException(user.name + " is already in this group");
        }
        groupRepo.addMember(groupId, user.userId);
        return groupRepo.getMembers(groupId);
    }

    public Group editGroup(long groupId, User requester, String name, String description) {
        Group group = requireOwner(groupId, requester);
        group.name = requireName(name);
        group.description = blankToNull(description);
        return groupRepo.update(group);
    }

    /** Deletes the group together with all of its expenses and settlements. */
    public void deleteGroup(long groupId, User requester) {
        requireOwner(groupId, requester);
        groupRepo.delete(groupId);
    }

    public Group getGroup(long groupId) {
        Group group = groupRepo.findById(groupId);
        if (group == null) {
            throw new ValidationException("This group no longer exists");
        }
        return group;
    }

    public List<User> getMembers(long groupId) {
        return groupRepo.getMembers(groupId);
    }

    public List<Group> getGroupsFor(User user) {
        return groupRepo.findGroupsForUser(user.userId);
    }

    private Group requireOwner(long groupId, User requester) {
        Group group = getGroup(groupId);
        if (group.ownerId != requester.userId) {
            throw new ValidationException("Only the group owner can do this");
        }
        return group;
    }

    private static String requireName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new ValidationException("Group name cannot be empty");
        }
        return clean;
    }

    private static String blankToNull(String text) {
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }
}
