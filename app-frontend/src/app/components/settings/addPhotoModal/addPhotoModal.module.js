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
    constructor($log, Upload, organizationService) {
        'ngInject';

        this.$log = $log;
        this.organizationService = organizationService;
        this.Upload = Upload;
        this.organizationId = this.resolve.organizationId;
    }

    $onInit() {
        this.initSteps();
    }

    initSteps() {
        this.steps = [];
        this.steps = this.steps.concat([{
            name: 'UPLOAD',
            previous: () => false,
            allowPrevious: () => false,
            next: () => 'PREVIEW',
            allowNext: () => this.selectedPhoto,
            allowClose: () => true,
            onExit: () => {
                this.currentError = null;
            }
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
            next: () => {},
            allowNext: () => {
            },
            onExit: () => this.finishUpload()
        }, {
            name: 'IMPORT_SUCCESS',
            allowDone: () => true
        }, {
            name: 'IMPORT_ERROR',
            allowDone: () => true
        }]);

        this.setCurrentStep(this.steps[0]);
    }

    photoSelected(file) {
        this.selectedPhoto = file;
    }

    startLogoUpload() {
        let self = this;
        let orgId = this.organizationId;
        this.Upload.base64DataUrl(this.selectedPhoto).then((base64) => {
            self.logoBase64 = base64.replace('data:image/png;base64,', '');
            self.organizationService.addOrganizationLogo(
                orgId, self.logoBase64)
                .then(resp => {
                    self.$log.log(resp);
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
        this.dismiss();
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
        this.close();
    }
}

const AddPhotoModalModule = angular.module('components.settings.addPhotoModal',
    ['ngFileUpload']);

AddPhotoModalModule.component('rfAddPhotoModal', AddPhotoModalComponent);
AddPhotoModalModule.controller('AddPhotoModalController', AddPhotoModalController);

export default AddPhotoModalModule;
