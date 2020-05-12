export interface OrientationConfiguration {
    /** Where the tour step will appear next to the selected element */
    orientationDirection: Orientation;
    /** When this orientation configuration starts in pixels */
    maximumSize?: number;
}

/**
 * Orientation of the tour step position next to the highlighted element.
 * @enum {string}
 */
export enum Orientation {
    BOTTOM = 'bottom',
    BOTTOMLEFT = 'bottom-left',
    BOTTOMRIGHT = 'bottom-right',
    LEFT = 'left',
    RIGHT = 'right',
    TOP = 'top',
    TOPLEFT = 'top-left',
    TOPRIGHT = 'top-right',
}

/**
 * State of the guided tour (started or finished)
 * @enum {string}
 */
export enum GuidedTourState {
    STARTED,
    FINISHED,
}

/**
 * Position of the overlay.
 * @enum {string}
 */
export enum OverlayPosition {
    TOP = 'top',
    LEFT = 'left',
    RIGHT = 'right',
    BOTTOM = 'bottom',
    ELEMENT = 'element',
}

/**
 * Type of the required user interaction.
 * @enum {string}
 */
export enum UserInteractionEvent {
    /** Listen for click events */
    CLICK = 'click',
    /** Observe added or removed lines in the code editor */
    ACE_EDITOR = 'ace-editor',
    /** Wait for the highlight element of the next tour step to be shown in the DOM */
    WAIT_FOR_SELECTOR = 'wait-for-selector',
    /** Observe and assess the given modeling task in the Apollon editor */
    MODELING = 'modeling',
    ASSESS_SUBMISSION = 'assess-submission',
}

/**
 * Direction.
 * @enum {string}
 */
export enum Direction {
    VERTICAL = 'vertical',
    HORIZONTAL = 'horizontal',
}

/**
 * Type that defines what should be reset if the tour is restarted.
 * @enum {string}
 */
export enum ResetParticipation {
    EXERCISE_PARTICIPATION = 'exercise-participation',
    TUTOR_ASSESSMENT = 'tutor-assessment',
    NONE = 'none',
}
