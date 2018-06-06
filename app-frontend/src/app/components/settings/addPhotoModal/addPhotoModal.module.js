import angular from 'angular';
import addPhotoModalTpl from './addPhotoModal.html';


const AddPhotoModalComponent = {
    templateUrl: addPhotoModalTpl,
    controller: 'AddPhotoModalController',
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

class AddPhotoModalController {
    constructor($scope, Upload, organizationService) {
        'ngInject';

        this.$scope = $scope;

        this.organizationService = organizationService;
        this.Upload = Upload;
    }

    $onInit() {
        this.initSteps();
        this.organizationId = this.resolve.organizationId;
    }

    initSteps() {
        this.steps = [];
        this.steps = this.steps.concat([{
            name: 'UPLOAD',
            previous: () => false,
            allowPrevious: () => false,
            next: () => 'PREVIEW',
            allowNext: () => this.selectedPhoto,
            allowClose: () => true
        }, {
            name: 'PREVIEW',
            previous: () => 'UPLOAD',
            allowPrevious: () => true,
            next: () => 'UPLOAD_PROGRESS',
            allowNext: () => true,
            allowClose: () => true
        }, {
            name: 'UPLOAD_PROGRESS',
            onEnter: () => this.startLogoUpload(),
            allowDone: () => !this.uploadInProgress && this.uploadDone,
            onExit: () => this.finishUpload()
        }]);

        this.setCurrentStep(this.steps[0]);
    }

    photoSelected(file, invalidFile) {
        if (file) {
            this.selectedPhoto = file;
            delete this.selectedPhotoErrorMsg;
            this.handleNext();
        } else if (invalidFile) {
            this.selectedPhotoErrorMsg = 'Picture size should be less than 512 x 512 pixels.';
        }
    }

    startLogoUpload() {
        this.preventInterruptions();
        this.uploadInProgress = true;

        let self = this;
        this.Upload.base64DataUrl(this.selectedPhoto).then((base64) => {
            self.organizationService.addOrganizationLogo(
                self.organizationId,
                base64.replace('data:image/png;base64,', '')
            ).then(resp => {
                self.uploadDone = true;
                self.uploadInProgress = false;
                self.uploadedOrganization = resp;
            }, (err) => {
                self.uploadDone = false;
                self.uploadInProgress = false;
                self.currentError = err.data;
            });
        });
    }

    currentStepIs(step) {
        if (step.constructor === Array) {
            return step.reduce((acc, cs) => {
                return acc || this.currentStepIs(cs);
            }, false);
        }
        return this.currentStep.name === step;
    }

    getStep(stepName) {
        return this.steps.find(s => s.name === stepName);
    }

    setCurrentStep(step) {
        if (this.currentStep && this.currentStep.onExit) {
            this.currentStep.onExit();
        }
        this.currentStep = step;
        if (this.currentStep.onEnter) {
            this.currentStep.onEnter();
        }
    }

    allowClose() {
        return this.currentStep.allowClose && this.currentStep.allowClose();
    }

    handleClose() {
        if (this.uploadDone) {
            this.finishUpload();
        } else {
            this.dismiss();
        }
    }

    hasPrevious() {
        return this.currentStep.previous && this.currentStep.previous();
    }

    handlePrevious() {
        this.setCurrentStep(this.getStep(this.currentStep.previous()));
    }

    allowPrevious() {
        return this.currentStep.allowPrevious && this.currentStep.allowPrevious();
    }

    hasNext() {
        return this.currentStep.next && this.currentStep.next();
    }

    handleNext() {
        this.setCurrentStep(this.getStep(this.currentStep.next()));
    }

    allowNext() {
        return this.currentStep.allowNext && this.currentStep.allowNext();
    }

    allowDone() {
        return this.currentStep.allowDone && this.currentStep.allowDone();
    }

    handleDone() {
        this.allowInterruptions();
        this.closeWithData(this.uploadedOrganization);
    }

    preventInterruptions() {
        if (!this.closeCanceller) {
            this.closeCanceller = this.$scope.$on('modal.closing', (e) => {
                e.preventDefault();
            });
            this.locationChangeCanceller = this.$scope.$on('$locationChangeStart', (event) => {
                event.preventDefault();
            });
        }
    }

    allowInterruptions() {
        if (this.closeCanceller) {
            this.closeCanceller();
            delete this.closeCanceller;
        }
        if (this.locationChangeCanceller) {
            this.locationChangeCanceller();
            delete this.locationChangeCanceller;
        }
    }

    finishUpload() {
        this.allowInterruptions();
        this.closeWithData(this.uploadedOrganization);
    }

    closeWithData(data) {
        this.close({$value: data});
    }
}

const AddPhotoModalModule = angular.module('components.settings.addPhotoModal',
    ['ngFileUpload']);

AddPhotoModalModule.component('rfAddPhotoModal', AddPhotoModalComponent);
AddPhotoModalModule.controller('AddPhotoModalController', AddPhotoModalController);

export default AddPhotoModalModule;
