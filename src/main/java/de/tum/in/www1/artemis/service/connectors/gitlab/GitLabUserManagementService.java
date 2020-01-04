package de.tum.in.www1.artemis.service.connectors.gitlab;

import static org.gitlab4j.api.models.AccessLevel.GUEST;
import static org.gitlab4j.api.models.AccessLevel.MAINTAINER;

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;

@Service
@Profile("gitlab")
public class GitLabUserManagementService implements VcsUserManagementService {

    private final Logger log = LoggerFactory.getLogger(GitLabUserManagementService.class);

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

    @Value("${artemis.version-control.secret}")
    private String GITLAB_PRIVATE_TOKEN;

    private final UserService userService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private GitLabApi gitlab;

    public GitLabUserManagementService(UserService userService, ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository) {
        this.userService = userService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        this.gitlab = new GitLabApi(GITLAB_SERVER_URL.toString(), GITLAB_PRIVATE_TOKEN);
    }

    @Override
    public void createUser(User user) {
        final var userId = getUserIdCreateIfNotExists(user);

        // Add user to existing exercises
        final var instructorExercises = programmingExerciseRepository.findAllByCourse_InstructorGroupNameIn(user.getGroups());
        final var teachingAssistantExercises = programmingExerciseRepository.findAllByCourse_TeachingAssistantGroupNameIn(user.getGroups()).stream()
                .filter(programmingExercise -> !instructorExercises.contains(programmingExercise)).collect(Collectors.toList());
        addUserToGroups(userId, instructorExercises, MAINTAINER);
        addUserToGroups(userId, teachingAssistantExercises, GUEST);
    }

    @Override
    public void updateUser(User user, Set<String> removedGroups, Set<String> addedGroups) {
        if (removedGroups.isEmpty() && addedGroups.isEmpty()) {
            return;
        }
        try {
            final var gitlabUser = gitlab.getUserApi().getUser(user.getLogin());

            // Add as member to new groups
            if (!addedGroups.isEmpty()) {
                final var exercisesWithAddedGroups = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(addedGroups);
                for (final var exercise : exercisesWithAddedGroups) {
                    final var accessLevel = addedGroups.contains(exercise.getCourse().getInstructorGroupName()) ? MAINTAINER : GUEST;
                    gitlab.getGroupApi().addMember(exercise.getProjectKey(), gitlabUser.getId(), accessLevel);
                }
            }

            // Update/remove old groups
            if (!removedGroups.isEmpty()) {
                final var exercisesWithOutdatedGroups = programmingExerciseRepository.findAllByInstructorOrTAGroupNameIn(removedGroups);
                for (final var exercise : exercisesWithOutdatedGroups) {
                    // If the the user is still in another group for the exercise (TA -> INSTRUCTOR or INSTRUCTOR -> TA),
                    // then we have to add him as a member with the new access level
                    final var course = exercise.getCourse();
                    if (user.getGroups().contains(course.getInstructorGroupName())) {
                        gitlab.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUser.getId(), MAINTAINER);
                    }
                    else if (user.getGroups().contains(course.getTeachingAssistantGroupName())) {
                        gitlab.getGroupApi().updateMember(exercise.getProjectKey(), gitlabUser.getId(), GUEST);
                    }
                    else {
                        // If the user is not a member of any relevant group any more, we can remove him from the exercise
                        gitlab.getGroupApi().removeMember(exercise.getProjectKey(), gitlabUser.getId());
                    }
                }
                gitlab.getUserApi().updateUser(gitlabUser, String.valueOf(userService.decryptPasswordByLogin(user.getLogin())));
            }
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Error while trying to update user in GitLab: " + user, e);
        }
    }

    @Override
    public void updateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldTeachingAssistantGroup) {
        if (oldInstructorGroup.equals(updatedCourse.getInstructorGroupName()) && oldTeachingAssistantGroup.equals(updatedCourse.getTeachingAssistantGroupName())) {
            // Do nothing if the group names didn't change
            return;
        }

        final var exercises = programmingExerciseRepository.findAllByCourse(updatedCourse);
        final var processedUsers = new HashSet<User>();

        final var oldInstructors = userRepository.findAllByGroups(oldInstructorGroup);
        updateOldGroupMembers(exercises, oldInstructors, updatedCourse.getInstructorGroupName(), updatedCourse.getTeachingAssistantGroupName(), GUEST, false);
        processedUsers.addAll(oldInstructors);

        final var oldTeachingAssistants = userRepository.findAllByGroupsContainingAndNotIn(oldTeachingAssistantGroup, new HashSet<>(oldInstructors));
        updateOldGroupMembers(exercises, oldTeachingAssistants, updatedCourse.getTeachingAssistantGroupName(), updatedCourse.getInstructorGroupName(), MAINTAINER, true);
        processedUsers.addAll(oldTeachingAssistants);

        // Now, we only have to add all users that have not been updated yet AND that are part of one of the new groups
        final var remainingInstructors = userRepository.findAllByGroupsContainingAndNotIn(updatedCourse.getInstructorGroupName(), processedUsers);
        remainingInstructors.forEach(user -> {
            final var userId = getUserId(user.getLogin());
            addUserToGroups(userId, exercises, MAINTAINER);
        });
        processedUsers.addAll(remainingInstructors);
        final var remainingTeachingAssistants = userRepository.findAllByGroupsContainingAndNotIn(updatedCourse.getTeachingAssistantGroupName(), processedUsers);
        remainingTeachingAssistants.forEach(user -> {
            final var userId = getUserId(user.getLogin());
            addUserToGroups(userId, exercises, GUEST);
        });
    }

    /**
     * Updates all exercise groups in GitLab for the new course instructor/TA group names. Removes users that are no longer
     * in any group and moves users to the new group, if they still have a valid group (e.g. instructor to TA).
     * If a user still belongs to a group that is valid for the same access level, he will stay on this level. If a user
     * can be upgraded, i.e. from instructor to TA, this will also be done.
     * All cases:
     * <ul>
     *     <li>DOWNGRADE from instructor to TA</li>
     *     <li>STAY instructor, because other group name is valid for newInstructorGroup</li>
     *     <li>UPGRADE from TA to instructor</li>
     *     <li>REMOVAL from GitLab group, because no valid active group is present</li>
     * </ul>
     *
     * @param exercises All exercises for the updated course
     * @param users All user in the old group
     * @param newGroupName The name of the new group, e.g. "newInstructors"
     * @param alternativeGroupName The name of the other group (instructor or TA), e.g. "newTeachingAssistant"
     * @param alternativeAccessLevel The access level for the alternative group, e.g. GUEST for TAs
     * @param doUpgrade True, if the alternative group would be an upgrade. This is the case if the old group was TA, so the new instructor group would be better (if applicable)
     */
    private void updateOldGroupMembers(List<ProgrammingExercise> exercises, List<User> users, String newGroupName, String alternativeGroupName, AccessLevel alternativeAccessLevel,
            boolean doUpgrade) {
        for (final var user : users) {
            final var userId = getUserId(user.getLogin());
            final Optional<AccessLevel> newAccessLevel;
            if (user.getGroups().contains(alternativeGroupName)) {
                newAccessLevel = Optional.of(alternativeAccessLevel);
            }
            else {
                newAccessLevel = Optional.empty();
            }
            if (user.getGroups().contains(newGroupName) && (!doUpgrade || newAccessLevel.isEmpty())) {
                // One of the user's groups is still valid for the current level AND an upgrade is not possible or the
                // other group is just below the current one, e.g. if the other group is TA and the current group is instructor
                continue;
            }
            exercises.forEach(exercise -> {
                try {
                    if (newAccessLevel.isPresent()) {
                        gitlab.getGroupApi().updateMember(exercise.getProjectKey(), userId, newAccessLevel.get());
                    }
                    else {
                        gitlab.getGroupApi().removeMember(exercise.getProjectKey(), userId);
                    }
                }
                catch (GitLabApiException e) {
                    throw new GitLabException("Error while updating GitLab group " + exercise.getProjectKey(), e);
                }
            });
        }
    }

    @Override
    public void deleteUser(String login) {
        try {
            // Delete by login String doesn't work, so we need to get the actual userId first.
            final var userId = getUserId(login);
            gitlab.getUserApi().deleteUser(userId, true);
        }
        catch (NullPointerException e) {
            log.warn("Cannot delete user in GitLab. User does not exist! " + login);
        }
        catch (GitLabApiException e) {
            throw new GitLabException(String.format("Cannot delete user %s from GitLab!", login), e);
        }
    }

    private int getUserIdCreateIfNotExists(User user) {
        try {
            var gitlabUser = gitlab.getUserApi().getUser(user.getLogin());
            if (gitlabUser == null) {
                gitlabUser = importUser(user);
            }

            return gitlabUser.getId();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to get ID for user " + user.getLogin(), e);
        }
    }

    void addUserToGroups(int userId, List<ProgrammingExercise> exercises, AccessLevel accessLevel) {
        for (final var exercise : exercises) {
            try {
                gitlab.getGroupApi().addMember(exercise.getProjectKey(), userId, accessLevel);
            }
            catch (GitLabApiException e) {
                if (e.getMessage().equals("Member already exists")) {
                    log.debug("Member already exists for group " + exercise.getProjectKey());
                    return;
                }
                throw new GitLabException(String.format("Error adding new user [%d] to group [%s]", userId, exercise.toString()), e);
            }
        }
    }

    org.gitlab4j.api.models.User importUser(User user) {
        final var gitlabUser = new org.gitlab4j.api.models.User().withEmail(user.getEmail()).withUsername(user.getLogin()).withName(user.getName()).withCanCreateGroup(false)
                .withCanCreateProject(false).withSkipConfirmation(true);
        try {
            return gitlab.getUserApi().createUser(gitlabUser, String.valueOf(userService.decryptPasswordByLogin(user.getLogin())), false);
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to create new user in GitLab " + user.getLogin(), e);
        }
    }

    int getUserId(String username) {
        try {
            return gitlab.getUserApi().getUser(username).getId();
        }
        catch (GitLabApiException e) {
            throw new GitLabException("Unable to get ID for user " + username, e);
        }
    }
}
