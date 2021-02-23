package de.tum.in.www1.artemis.web.rest;

import java.util.*;

import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.service.OrganizationService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing the Organization entities
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class OrganizationResource {

    private final Logger log = LoggerFactory.getLogger(OrganizationResource.class);

    private static final String ENTITY_NAME = "organization";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    @Value("${artemis.user-management.organizations.enable-multiple-organizations:#{null}}")
    private Optional<Boolean> isMultiOrganizationEnabled;

    private final OrganizationService organizationService;

    private final OrganizationRepository organizationRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public OrganizationResource(OrganizationService organizationService, OrganizationRepository organizationRepository, UserRepository userRepository, CourseRepository courseRepository) {
        this.organizationService = organizationService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.courseRepository = courseRepository;
    }

    @PostMapping("/organizations/course/{courseId}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> addCourseToOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        organizationRepository.addCourseToOrganization(course, organizationId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, course.getTitle())).build();
    }

    @DeleteMapping("/organizations/course/{courseId}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> removeCourseToOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        organizationRepository.removeCourseFromOrganization(course, organizationId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, course.getTitle())).build();
    }

    @PostMapping("/organizations/user/{userLogin}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> addUserToOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        User user = userRepository.getUserByLoginElseThrow(userLogin);
        organizationRepository.addUserToOrganization(user, organizationId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, user.getLogin())).build();
    }

    @DeleteMapping("/organizations/user/{userLogin}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> removeUserFromOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        User user = userRepository.getUserByLoginElseThrow(userLogin);
        organizationRepository.removeUserFromOrganization(user, organizationId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, user.getLogin())).build();
    }

    @PostMapping("/organizations/add")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> addOrganization(@RequestBody Organization organization) {
        Organization created = organizationService.save(organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, created.getName())).body(created);
    }

    @PutMapping("/organizations/update")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> updateOrganization(@RequestBody Organization organization) {
        if (organization.getId() != null && organizationRepository.findOneOrElseThrow(organization.getId()) != null) {
            Organization updated = organizationService.update(organization);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updated.getName())).body(updated);
        }
        else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The organization to update doesn't have an ID.", "NoIdProvided")).body(null);
        }
    }

    @DeleteMapping("/organizations/delete/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long organizationId) {
        Organization organization = organizationRepository.findOneOrElseThrow(organizationId);
        organizationRepository.delete(organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, organization.getName())).build();
    }

    @GetMapping("/organizations/all")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        List<Organization> organizations = organizationRepository.findAll();
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }

    @GetMapping("/organizations/allCount")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<Long, Map<String, Long>>> getNumberOfUsersAndCoursesOfAllOrganizations() {
        Map<Long, Map<String, Long>> result = new HashMap<>();

        List<Organization> organizations = organizationRepository.findAll();
        for (Organization organization : organizations) {
            Map<String, Long> numberOfUsersAndCourses = new HashMap<>();
            numberOfUsersAndCourses.put("users", organizationRepository.getNumberOfUsersByOrganizationId(organization.getId()));
            numberOfUsersAndCourses.put("courses", organizationRepository.getNumberOfCoursesByOrganizationId(organization.getId()));
            result.put(organization.getId(), numberOfUsersAndCourses);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping("/organizations/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationById(@PathVariable long organizationId) {
        Organization organization = organizationRepository.findOneOrElseThrow(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    @GetMapping("/organizations/{organizationId}/full")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationByIdWithUsersAndCourses(@PathVariable long organizationId) {
        Organization organization = organizationRepository.findOneWithEagerUsersAndCoursesOrElseThrow(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    @GetMapping("/organizations/{organizationId}/count")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getNumberOfUsersAndCoursesByOrganization(@PathVariable long organizationId) {
        Map<String, Long> numberOfUsersAndCourses = new HashMap<>();
        numberOfUsersAndCourses.put("users", organizationRepository.getNumberOfUsersByOrganizationId(organizationId));
        numberOfUsersAndCourses.put("courses", organizationRepository.getNumberOfCoursesByOrganizationId(organizationId));
        return new ResponseEntity<>(numberOfUsersAndCourses, HttpStatus.OK);
    }

    @GetMapping("/organizations/course/{courseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Organization>> getAllOrganizationsByCourse(@PathVariable Long courseId) {
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByCourseId(courseId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }

    @GetMapping("/organizations/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Set<Organization>> getAllOrganizationsByUser(@PathVariable Long userId) {
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByUserId(userId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }
}