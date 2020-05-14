import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/alert/alert.service';
import interact from 'interactjs';
import * as moment from 'moment';
import * as $ from 'jquery';
import { Interactable } from '@interactjs/core/Interactable';
import { Location } from '@angular/common';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { WindowRef } from 'app/core/websocket/window.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { filter, finalize } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileService } from 'app/shared/http/file.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';

@Component({
    providers: [FileUploadAssessmentsService, WindowRef],
    templateUrl: './file-upload-assessment.component.html',
    styleUrls: ['./file-upload-assessment.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class FileUploadAssessmentComponent implements OnInit, AfterViewInit, OnDestroy {
    text: string;
    participation: StudentParticipation;
    submission: FileUploadSubmission;
    unassessedSubmission: FileUploadSubmission;
    result: Result;
    generalFeedback: Feedback = new Feedback();
    referencedFeedback: Feedback[] = [];
    exercise: FileUploadExercise;
    totalScore = 0;
    assessmentsAreValid: boolean;
    invalidError: string | null;
    isAssessor = true;
    isAtLeastInstructor = false;
    busy = true;
    showResult = true;
    complaint: Complaint;
    ComplaintType = ComplaintType;
    notFound = false;
    userId: number;
    canOverride = false;
    isLoading = true;
    courseId: number;

    /** Resizable constants **/
    resizableMinWidth = 100;
    resizableMaxWidth = 1200;
    resizableMinHeight = 200;
    interactResizable: Interactable;
    interactResizableTop: Interactable;

    private cancelConfirmationText: string;

    constructor(
        private changeDetectorRef: ChangeDetectorRef,
        private jhiAlertService: AlertService,
        private modalService: NgbModal,
        private router: Router,
        private route: ActivatedRoute,
        private resultService: ResultService,
        private fileUploadAssessmentsService: FileUploadAssessmentsService,
        private accountService: AccountService,
        private location: Location,
        private $window: WindowRef,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private fileUploadSubmissionService: FileUploadSubmissionService,
        private complaintService: ComplaintService,
        private fileService: FileService,
    ) {
        this.assessmentsAreValid = false;
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    get assessments(): Feedback[] {
        return [this.generalFeedback, ...this.referencedFeedback];
    }

    /**
     * Angular lifecycle method, invoked when component is initialized
     */
    public ngOnInit(): void {
        this.busy = true;

        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);

        // extract params from route
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            const exerciseId = Number(params['exerciseId']);
            const submissionValue = params['submissionId'];
            const submissionId = Number(submissionValue);
            if (submissionValue === 'new') {
                this.loadOptimalSubmission(exerciseId);
            } else {
                this.loadSubmission(submissionId);
            }
        });
    }

    /**
     * extracts file extension from file path
     * @param filePath path of which file extension is extracted
     */
    attachmentExtension(filePath: string): string {
        if (!filePath) {
            return 'N/A';
        }

        return filePath.split('.').pop()!;
    }

    private loadOptimalSubmission(exerciseId: number): void {
        this.fileUploadSubmissionService.getFileUploadSubmissionForExerciseWithoutAssessment(exerciseId, true).subscribe(
            (submission: FileUploadSubmission) => {
                this.initializePropertiesFromSubmission(submission);
                // Update the url with the new id, without reloading the page, to make the history consistent
                const newUrl = window.location.hash.replace('#', '').replace('new', `${this.submission!.id}`);
                this.location.go(newUrl);
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there is no submission waiting for assessment at the moment
                    this.goToExerciseDashboard();
                    this.jhiAlertService.info('artemisApp.tutorExerciseDashboard.noSubmissions');
                } else if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                    this.goToExerciseDashboard();
                } else {
                    this.onError('artemisApp.assessment.messages.loadSubmissionFailed');
                }
            },
        );
    }

    private loadSubmission(submissionId: number): void {
        this.fileUploadSubmissionService
            .get(submissionId)
            .pipe(filter((res) => !!res))
            .subscribe(
                (res) => {
                    this.initializePropertiesFromSubmission(res.body!);
                },
                (error: HttpErrorResponse) => {
                    if (error.error && error.error.errorKey === 'lockedSubmissionsLimitReached') {
                        this.goToExerciseDashboard();
                    } else {
                        this.onError('');
                    }
                },
            );
    }

    private initializePropertiesFromSubmission(submission: FileUploadSubmission): void {
        this.submission = submission;
        this.participation = this.submission.participation as StudentParticipation;
        this.exercise = this.participation.exercise as FileUploadExercise;
        this.result = this.submission.result;
        if (this.result.hasComplaint) {
            this.getComplaint();
        }
        this.submission.participation.results = [this.result];
        this.result.participation = this.submission.participation;
        if (this.result.feedbacks) {
            this.loadFeedbacks(this.result.feedbacks);
        } else {
            this.result.feedbacks = [];
        }
        if ((this.result.assessor == null || this.result.assessor.id === this.userId) && !this.result.completionDate) {
            this.jhiAlertService.clear();
            this.jhiAlertService.info('artemisApp.fileUploadAssessment.messages.lock');
        }

        this.checkPermissions();
        this.validateAssessment();
        this.busy = false;
        this.isLoading = false;
    }

    /**
     * Angular lifecycle method, invoked after view was initialized. We create an interact.js resizable object,
     * designate the edges which can be used to resize the target element and set min and max values.
     * The 'resizemove' callback function processes the event values and sets new width and height values for the element.
     */
    ngAfterViewInit(): void {
        this.resizableMinWidth = this.$window.nativeWindow.screen.width / 6;
        this.resizableMinHeight = this.$window.nativeWindow.screen.height / 7;

        this.interactResizable = interact('.resizable-submission')
            .resizable({
                // Enable resize from left edge; triggered by class .resizing-bar
                edges: { left: '.resizing-bar', right: false, bottom: false, top: false },
                // Set min and max width
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: this.resizableMinWidth, height: 0 },
                        max: { width: this.resizableMaxWidth, height: 2000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element width
                target.style.width = event.rect.width + 'px';
                target.style.minWidth = event.rect.width + 'px';
            });

        this.interactResizableTop = interact('.resizable-horizontal')
            .resizable({
                // Enable resize from bottom edge; triggered by class .resizing-bar-bottom
                edges: { left: false, right: false, top: false, bottom: '.resizing-bar-bottom' },
                // Set min height
                modifiers: [
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: this.resizableMinHeight },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                // Update element height
                target.style.minHeight = event.rect.height + 'px';
                $('#submission-area').css('min-height', event.rect.height - 100 + 'px');
            });
    }

    /**
     * Angular lifecycle method, invoked when component is destroyed
     */
    public ngOnDestroy(): void {
        this.changeDetectorRef.detach();
    }

    /**
     * add a new feedback to referencedFeedback
     */
    public addReferencedFeedback(): void {
        const referencedFeedback = new Feedback();
        referencedFeedback.credits = 0;
        this.referencedFeedback.push(referencedFeedback);
        this.validateAssessment();
    }

    /**
     * deletes an Assessment
     * @param assessmentToDelete
     */
    public deleteAssessment(assessmentToDelete: Feedback): void {
        const indexToDelete = this.referencedFeedback.indexOf(assessmentToDelete);
        this.referencedFeedback.splice(indexToDelete, 1);
        this.validateAssessment();
    }

    /**
     * Load next assessment in the same page.
     * It calls the api to load the new unassessed submission in the same page.
     * For the new submission to appear on the same page, the url has to be reloaded.
     */
    assessNextOptimal() {
        this.generalFeedback = new Feedback();
        this.referencedFeedback = [];
        this.fileUploadSubmissionService.getFileUploadSubmissionForExerciseWithoutAssessment(this.exercise.id).subscribe(
            (response: FileUploadSubmission) => {
                this.unassessedSubmission = response;
                this.router.onSameUrlNavigation = 'reload';
                // navigate to the new assessment page to trigger re-initialization of the components
                this.router.navigateByUrl(
                    `/course-management/${this.courseId}/file-upload-exercises/${this.exercise.id}/submissions/${this.unassessedSubmission.id}/assessment`,
                    {},
                );
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    this.jhiAlertService.error('artemisApp.tutorExerciseDashboard.noSubmissions');
                } else {
                    this.onError(error.message);
                }
            },
        );
    }

    /**
     * Invoked when save button is clicked. Sends request to server to save the assessment for the submission
     */
    onSaveAssessment() {
        this.isLoading = true;
        this.fileUploadAssessmentsService
            .saveAssessment(this.assessments, this.submission!.id)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe(
                (result: Result) => {
                    this.result = result;
                    this.jhiAlertService.clear();
                    this.jhiAlertService.success('artemisApp.assessment.messages.saveSuccessful');
                },
                () => {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('artemisApp.assessment.messages.saveFailed');
                },
            );
    }

    /**
     * executed when submit is pressed
     */
    onSubmitAssessment() {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.fileUploadAssessment.error.invalidAssessments');
            return;
        }
        this.isLoading = true;
        this.fileUploadAssessmentsService
            .saveAssessment(this.assessments, this.submission.id, true)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe(
                (result) => {
                    this.result = result;
                    this.updateParticipationWithResult();
                    this.jhiAlertService.clear();
                    this.jhiAlertService.success('artemisApp.assessment.messages.submitSuccessful');
                },
                (error: HttpErrorResponse) => this.onError(`artemisApp.${error.error.entityName}.${error.error.message}`),
            );
    }

    /**
     * Cancel the current assessment and navigate back to the exercise dashboard.
     */
    onCancelAssessment() {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.isLoading = true;
            this.fileUploadAssessmentsService
                .cancelAssessment(this.submission.id)
                .pipe(finalize(() => (this.isLoading = false)))
                .subscribe(() => {
                    this.goToExerciseDashboard();
                });
        }
    }

    private updateParticipationWithResult(): void {
        this.showResult = false;
        this.changeDetectorRef.detectChanges();
        this.participation.results[0] = this.result;
        this.showResult = true;
        this.changeDetectorRef.detectChanges();
    }

    /**
     * gets complaint from server
     */
    getComplaint(): void {
        this.complaintService.findByResultId(this.result.id).subscribe(
            (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        );
    }

    /**
     * navigates to exercise Dashboard
     */
    goToExerciseDashboard() {
        if (this.exercise && this.exercise.course) {
            this.router.navigateByUrl(`/course-management/${this.exercise.course.id}/exercises/${this.exercise.id}/tutor-dashboard`);
        } else {
            this.location.back();
        }
    }

    /**
     * wrapper for validateAssessment
     */
    updateAssessment() {
        this.validateAssessment();
    }

    /**
     * Checks if the assessment is valid:
     *   - There must be at least one referenced feedback or a general feedback.
     *   - Each reference feedback must have either a score or a feedback text or both.
     *   - The score must be a valid number.
     *
     * Additionally, the total score is calculated for all numerical credits.
     */
    public validateAssessment() {
        this.assessmentsAreValid = true;
        this.invalidError = null;

        if ((this.generalFeedback.detailText == null || this.generalFeedback.detailText.length === 0) && this.referencedFeedback && this.referencedFeedback.length === 0) {
            this.totalScore = 0;
            this.assessmentsAreValid = false;
            return;
        }

        let credits = this.referencedFeedback.map((assessment) => assessment.credits);

        if (!this.invalidError && !credits.every((credit) => credit !== null && !isNaN(credit))) {
            this.invalidError = 'artemisApp.fileUploadAssessment.error.invalidScoreMustBeNumber';
            this.assessmentsAreValid = false;
            credits = credits.filter((credit) => credit !== null && !isNaN(credit));
        }

        if (!this.invalidError && !this.referencedFeedback.every((f) => f.credits !== 0)) {
            this.invalidError = 'artemisApp.fileUploadAssessment.error.invalidNeedScore';
            this.assessmentsAreValid = false;
        }

        this.totalScore = credits.reduce((a, b) => a + b, 0);
    }

    /**
     * wrapper for fileService.downloadFileWithAccessToken
     * @param filePath path of file to download
     */
    downloadFile(filePath: string) {
        this.fileService.downloadFileWithAccessToken(filePath);
    }

    private checkPermissions() {
        this.isAssessor = this.result && this.result.assessor && this.result.assessor.id === this.userId;
        const isBeforeAssessmentDueDate = this.exercise && this.exercise.assessmentDueDate && moment().isBefore(this.exercise.assessmentDueDate);
        // tutors are allowed to override one of their assessments before the assessment due date, instructors can override any assessment at any time
        this.canOverride = (this.isAssessor && isBeforeAssessmentDueDate) || this.isAtLeastInstructor;
    }

    /**
     * toggles collapse of closest element with id='instruction'
     * @param $event toggleEvent
     */
    toggleCollapse($event: any) {
        const target = $event.toElement || $event.relatedTarget || $event.target;
        target.blur();
        const $card = $(target).closest('#instructions');

        if ($card.hasClass('collapsed')) {
            $card.removeClass('collapsed');
            this.interactResizable.resizable({ enabled: true });
            $card.css({ width: this.resizableMinWidth + 'px', minWidth: this.resizableMinWidth + 'px' });
        } else {
            $card.addClass('collapsed');
            $card.css({ width: '55px', minWidth: '55px' });
            this.interactResizable.resizable({ enabled: false });
        }
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    onUpdateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.validateAssessment();
        if (!this.assessmentsAreValid) {
            this.jhiAlertService.error('artemisApp.fileUploadAssessment.error.invalidAssessments');
            return;
        }
        this.isLoading = true;
        this.fileUploadAssessmentsService
            .updateAssessmentAfterComplaint(this.assessments, complaintResponse, this.submission.id)
            .pipe(finalize(() => (this.isLoading = false)))
            .subscribe(
                (response) => {
                    this.result = response.body!;
                    this.updateParticipationWithResult();
                    this.jhiAlertService.clear();
                    this.jhiAlertService.success('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
                },
                () => {
                    this.jhiAlertService.clear();
                    this.jhiAlertService.error('artemisApp.assessment.messages.updateAfterComplaintFailed');
                },
            );
    }

    private loadFeedbacks(feedbacks: Feedback[]): void {
        const generalFeedbackIndex = feedbacks.findIndex((feedback) => feedback.credits === 0);
        if (generalFeedbackIndex !== -1) {
            this.generalFeedback = feedbacks[generalFeedbackIndex];
            feedbacks.splice(generalFeedbackIndex, 1);
        } else {
            this.generalFeedback = new Feedback();
        }
        this.referencedFeedback = feedbacks;
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
