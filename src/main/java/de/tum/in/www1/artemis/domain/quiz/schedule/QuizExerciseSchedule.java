package de.tum.in.www1.artemis.domain.quiz.schedule;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.SubmittedAnswer;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.QuizStatisticService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.store.KeyValueStore;
import de.tum.in.www1.artemis.store.factories.KeyValueStoreFactory;
import de.tum.in.www1.artemis.web.websocket.distributed.ArtemisMessagingTemplate;

/**
 * This class is responsible for scheduling a specific quiz.
 * It is present on every server running the quiz
 */
public class QuizExerciseSchedule {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseSchedule.class);

    private static ThreadPoolTaskScheduler threadPoolTaskScheduler;

    static {
        threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setThreadNamePrefix("QuizScheduler");
        threadPoolTaskScheduler.setPoolSize(1);
        threadPoolTaskScheduler.initialize();
    }

    private QuizExercise quizExercise;

    private KeyValueStore<String, QuizSubmission> submissionKeyValueStore;

    private Set<Result> results = new HashSet<>(); // TODO: Simon Leiß: Check if this must be synchronized

    private final ArtemisMessagingTemplate messagingTemplate;

    private ScheduledFuture quizStartSchedule;

    private ScheduledFuture quizEndSchedule;

    private QuizExerciseService quizExerciseService;

    private UserService userService;

    private final QuizStatisticService quizStatisticService;

    private StudentParticipationRepository studentParticipationRepository;

    private ResultRepository resultRepository;

    private QuizSubmissionRepository quizSubmissionRepository;

    public QuizExerciseSchedule(QuizExercise quizExercise, KeyValueStoreFactory keyValueStoreFactory, ArtemisMessagingTemplate messagingTemplate,
            QuizExerciseService quizExerciseService, UserService userService, QuizStatisticService quizStatisticService,
            StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, QuizSubmissionRepository quizSubmissionRepository) {
        this.quizExercise = quizExercise;
        this.messagingTemplate = messagingTemplate;
        this.quizExerciseService = quizExerciseService;
        this.userService = userService;
        this.quizStatisticService = quizStatisticService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;

        submissionKeyValueStore = keyValueStoreFactory.createKeyValueStore("quiz-" + quizExercise.getId(), String.class, QuizSubmission.class);

        scheduleQuizStart();
    }

    /**
     * Save a QuizSubmission in the KV-store
     *
     * @param username the username of whom the submission should be saved
     * @param quizSubmission the quizSubmission which should be saved
     */
    public void updateSubmission(String username, QuizSubmission quizSubmission) {
        if (username != null && quizSubmission != null) {
            if (!submissionKeyValueStore.exists(username)) {
                submissionKeyValueStore.registerKey(username);
            }
            submissionKeyValueStore.put(username, quizSubmission);
        }
    }

    /**
     * Save a participation in the KV-store
     *
     * @param participation the participation that should be saved
     */
    public void addParticipation(StudentParticipation participation) {
        // TODO: Simon Leiß: Check if this is needed
        /*
         * if (participation != null) { participationKeyValueStore.put(participation.getParticipantIdentifier(), participation);
         * participationKeyValueStore.registerKey(participation.getParticipantIdentifier()); // Register key so that the iterator will return it }
         */
    }

    /**
     * Get the quiz submission from the KV-store for the given user.
     *
     * @param username the user for whom the submission should be loaded
     * @return the saved submission or a new submission if no saved submission was found
     */
    public QuizSubmission getQuizSubmission(String username) {
        if (username == null) {
            return null;
        }

        QuizSubmission quizSubmission = submissionKeyValueStore.get(username);
        if (quizSubmission != null) {
            return quizSubmission;
        }

        // return an empty quizSubmission if the KV-store contains no entry for the key
        return new QuizSubmission().submittedAnswers(new HashSet<>());
    }

    /**
     * Get the participation for a given username
     *
     * @param username the user for whom the participation should be loaded
     * @return the loaded participation, or null if no participation was found
     */
    public StudentParticipation getParticipation(String username) {
        if (username == null) {
            return null;
        }

        return null; // TODO: Simon Leiß: Check if this is needed
        // return participationKeyValueStore.get(username);
    }

    private void startQuiz() {
        scheduleQuizEnd();
        quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise);
    }

    public void scheduleQuizStart() {
        quizExercise = quizExerciseService.findOne(quizExercise.getId());
        // first remove and cancel old scheduledFuture if it exists
        cancelScheduledQuizStart();

        if (quizExercise.isIsPlannedToStart() && quizExercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            // schedule sending out filtered quiz over websocket
            this.quizStartSchedule = threadPoolTaskScheduler.schedule(this::startQuiz, Date.from(quizExercise.getReleaseDate().toInstant()));
        }
    }

    public void cancelScheduledQuizStart() {
        if (quizStartSchedule != null) {
            boolean cancelSuccess = quizStartSchedule.cancel(true);
            log.info("Stop scheduled quiz start for quiz " + quizExercise.getId() + " was successful: " + cancelSuccess);
        }
    }

    public void scheduleQuizEnd() {
        quizExercise = quizExerciseService.findOne(quizExercise.getId());
        // first remove and cancel old scheduledFuture if it exists
        cancelScheduledQuizEnd();

        if (quizExercise.getDueDate() != null && quizExercise.getDueDate().isAfter(ZonedDateTime.now())) {
            // schedule processing cached quiz submissions when quiz ends
            this.quizEndSchedule = threadPoolTaskScheduler.schedule(this::processCachedQuizSubmissions,
                    Date.from(quizExercise.getDueDate().toInstant().plusSeconds(Constants.QUIZ_GRACE_PERIOD_IN_SECONDS)));
        }
    }

    private void cancelScheduledQuizEnd() {
        if (quizEndSchedule != null) {
            boolean cancelSuccess = quizEndSchedule.cancel(true);
            log.info("Stop scheduled quiz start for quiz " + quizExercise.getId() + " was successful: " + cancelSuccess);
        }
    }

    public void clearQuizData() {
        submissionKeyValueStore.clear();
    }

    public void processCachedQuizSubmissions() {
        log.debug("Process cached quiz submissions for quiz " + quizExercise.getId());
        try {
            long start = System.currentTimeMillis();

            /*
             * SUBMISSIONS
             */
            // TODO: Check if this makes sense/works
            quizExercise = quizExerciseService.findOneWithQuestions(quizExercise.getId());
            // check if quiz has been deleted
            if (quizExercise == null) {
                // TODO: Delete submissions
                // continue
                return;
            }

            if (quizExercise.isEnded()) {
                int num = createParticipations();
                if (num > 0) {
                    log.info("Processed {} submissions after {} ms in quiz {}", num, System.currentTimeMillis() - start, quizExercise.getTitle());
                }

                quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExercise.getId());
                quizStatisticService.updateStatistics(results, quizExercise);
            }
        }
        catch (Exception e) {
            log.error("Exception in Quiz Schedule for quiz {}:\n{}", quizExercise.getId(), e.getMessage());
            e.printStackTrace();
        }

    }

    private void sendQuizResultToUser(StudentParticipation participation) {
        var user = participation.getParticipantIdentifier();
        removeUnnecessaryObjectsBeforeSendingToClient(participation);
        // TODO: use a proper result here
        messagingTemplate.convertAndSendToUser(user, "/topic/exercise/" + quizExercise.getId() + "/participation", participation);
    }

    private void removeUnnecessaryObjectsBeforeSendingToClient(StudentParticipation participation) {
        if (participation.getExercise() != null) {
            // we do not need the course and lectures
            participation.getExercise().setCourse(null);
        }
        // submissions are part of results, so we do not need them twice
        participation.setSubmissions(null);
        participation.setParticipant(null);
        if (participation.getResults() != null && participation.getResults().size() > 0) {
            QuizSubmission quizSubmission = (QuizSubmission) participation.getResults().iterator().next().getSubmission();
            if (quizSubmission != null && quizSubmission.getSubmittedAnswers() != null) {
                for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                    if (submittedAnswer.getQuizQuestion() != null) {
                        // we do not need all information of the questions again, they are already stored in the exercise
                        var question = submittedAnswer.getQuizQuestion();
                        submittedAnswer.setQuizQuestion(question.copyQuestionId());
                    }
                }
            }
        }
    }

    private int createParticipations() {
        int counter = 0;

        // This quiz has just ended
        Iterator<String> userIterator = submissionKeyValueStore.iterator();
        while (userIterator.hasNext()) {
            String username = userIterator.next();
            QuizSubmission quizSubmission = submissionKeyValueStore.get(username);
            try {
                quizSubmission.setSubmitted(true);
                quizSubmission.setType(SubmissionType.TIMEOUT);
                quizSubmission.setSubmissionDate(ZonedDateTime.now());

                // Create Participation and Result and save to Database (DB Write)
                // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
                createParticipationWithResultAndWriteItInHashMaps(username, quizSubmission);
                counter++;
            }
            catch (Exception e) {
                log.error("Exception in createParticipations() for {} in quiz {}: \n{}", username, quizExercise.getId(), e.getMessage());
                e.printStackTrace();
            }
        }

        return counter;
    }

    /**
     * create Participation and Result if the submission was submitted or if the quiz has ended and save them to Database (DB Write)
     *
     * @param username       the user, who submitted the quizSubmission
     * @param quizSubmission the quizSubmission, which is used to calculate the Result
     */
    private void createParticipationWithResultAndWriteItInHashMaps(String username, QuizSubmission quizSubmission) {

        if (quizExercise != null && username != null && quizSubmission != null) {

            // create and save new participation
            StudentParticipation participation = new StudentParticipation();
            // TODO: when this is set earlier for the individual quiz start of a student, we don't need to set this here anymore
            participation.setInitializationDate(quizSubmission.getSubmissionDate());
            Optional<User> user = userService.getUserByLogin(username);
            user.ifPresent(participation::setParticipant);
            // add the quizExercise to the participation
            participation.setExercise(quizExercise);

            // create new result
            Result result = new Result().participation(participation).submission(quizSubmission);
            result.setRated(true);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(quizSubmission.getSubmissionDate());
            result.setSubmission(quizSubmission);

            // calculate scores and update result and submission accordingly
            quizSubmission.calculateAndUpdateScores(quizExercise);
            result.evaluateSubmission();

            // add result to participation
            participation.addResult(result);

            // add submission to participation
            participation.addSubmissions(quizSubmission);
            participation.setInitializationState(InitializationState.FINISHED);
            participation.setExercise(quizExercise);

            // save participation, result and quizSubmission
            participation = studentParticipationRepository.save(participation);
            quizSubmissionRepository.save(quizSubmission);
            result = resultRepository.save(result);

            sendQuizResultToUser(participation);
            // add the participation to the participationHashMap for the send out at the end of the quiz
            // addParticipation(participation);
            // add the result of the participation resultHashMap for the statistic-Update
            addResultForStatisticUpdate(result);
        }
    }

    public void addResultForStatisticUpdate(Result result) {
        results.add(result);
    }

}