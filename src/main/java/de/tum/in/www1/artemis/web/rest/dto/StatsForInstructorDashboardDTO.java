package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

public class StatsForInstructorDashboardDTO {

    private Long numberOfStudents;

    private DueDateStat numberOfSubmissions;

    private DueDateStat totalNumberOfAssessments;

    private Long totalNumberOfAssessmentLocks;

    private DueDateStat[] numberOfAssessmentsOfCorrectionRounds;

    private DueDateStat numberOfAutomaticAssistedAssessments;

    private Long numberOfComplaints;

    private Long numberOfOpenComplaints;

    private Long numberOfMoreFeedbackRequests;

    private Long numberOfOpenMoreFeedbackRequests;

    private Long numberOfAssessmentLocks;

    private List<TutorLeaderboardDTO> tutorLeaderboardEntries;

    public StatsForInstructorDashboardDTO() {
    }

    public Long getNumberOfStudents() {
        return numberOfStudents;
    }

    public void setNumberOfStudents(Long numberOfStudents) {
        this.numberOfStudents = numberOfStudents;
    }

    public DueDateStat getNumberOfSubmissions() {
        return numberOfSubmissions;
    }

    public void setNumberOfSubmissions(DueDateStat numberOfSubmissions) {
        this.numberOfSubmissions = numberOfSubmissions;
    }

    public DueDateStat getTotalNumberOfAssessments() {
        return totalNumberOfAssessments;
    }

    public void setTotalNumberOfAssessments(DueDateStat totalNumberOfAssessments) {
        this.totalNumberOfAssessments = totalNumberOfAssessments;
    }

    public DueDateStat[] getNumberOfAssessmentsOfCorrectionRounds() {
        return numberOfAssessmentsOfCorrectionRounds;
    }

    public void setNumberOfAssessmentsOfCorrectionRounds(DueDateStat[] numberOfAssessmentsOfCorrectionRounds) {
        this.numberOfAssessmentsOfCorrectionRounds = numberOfAssessmentsOfCorrectionRounds;
    }

    public DueDateStat getNumberOfAutomaticAssistedAssessments() {
        return numberOfAutomaticAssistedAssessments;
    }

    public void setNumberOfAutomaticAssistedAssessments(DueDateStat numberOfAutomaticAssistedAssessments) {
        this.numberOfAutomaticAssistedAssessments = numberOfAutomaticAssistedAssessments;
    }

    public Long getNumberOfComplaints() {
        return numberOfComplaints;
    }

    public void setNumberOfComplaints(Long numberOfComplaints) {
        this.numberOfComplaints = numberOfComplaints;
    }

    public Long getNumberOfOpenComplaints() {
        return numberOfOpenComplaints;
    }

    public void setNumberOfOpenComplaints(Long numberOfOpenComplaints) {
        this.numberOfOpenComplaints = numberOfOpenComplaints;
    }

    public Long getNumberOfMoreFeedbackRequests() {
        return numberOfMoreFeedbackRequests;
    }

    public void setNumberOfMoreFeedbackRequests(Long numberOfMoreFeedbackRequests) {
        this.numberOfMoreFeedbackRequests = numberOfMoreFeedbackRequests;
    }

    public Long getNumberOfOpenMoreFeedbackRequests() {
        return numberOfOpenMoreFeedbackRequests;
    }

    public void setNumberOfOpenMoreFeedbackRequests(Long numberOfOpenMoreFeedbackRequests) {
        this.numberOfOpenMoreFeedbackRequests = numberOfOpenMoreFeedbackRequests;
    }

    public Long getNumberOfAssessmentLocks() {
        return numberOfAssessmentLocks;
    }

    public void setNumberOfAssessmentLocks(long numberOfAssessmentLocks) {
        this.numberOfAssessmentLocks = numberOfAssessmentLocks;
    }

    public Long getTotalNumberOfAssessmentLocks() {
        return totalNumberOfAssessmentLocks;
    }

    public void setTotalNumberOfAssessmentLocks(long totalNumberOfAssessmentLocks) {
        this.totalNumberOfAssessmentLocks = totalNumberOfAssessmentLocks;
    }

    public List<TutorLeaderboardDTO> getTutorLeaderboardEntries() {
        return tutorLeaderboardEntries;
    }

    public void setTutorLeaderboardEntries(List<TutorLeaderboardDTO> tutorLeaderboardEntries) {
        this.tutorLeaderboardEntries = tutorLeaderboardEntries;
    }
}
