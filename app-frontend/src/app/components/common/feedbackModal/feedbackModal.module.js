import angular from 'angular';

import feedbackModalTpl from './feedbackModal.html';

const FeedbackModalComponent = {
    templateUrl: feedbackModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    replace: true
};

const FeedbackModalModule = angular.module('components.common.feedbackModal', []);

FeedbackModalModule.component(
    'rfFeedbackModal', FeedbackModalComponent
);

export default FeedbackModalModule;
