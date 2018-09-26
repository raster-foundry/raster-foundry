// Component code
import feedbackModalTpl from './feedbackModal.html';

const rfFeedbackModal = {
    templateUrl: feedbackModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    replace: true
};

export default rfFeedbackModal;
