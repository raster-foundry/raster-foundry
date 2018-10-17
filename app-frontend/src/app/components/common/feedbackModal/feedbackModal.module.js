import angular from 'angular';
import FeedbackModalComponent from './feedbackModal.component.js';

const FeedbackModalModule = angular.module('components.common.feedbackModal', []);

FeedbackModalModule.component(
    'rfFeedbackModal', FeedbackModalComponent
);

export default FeedbackModalModule;
