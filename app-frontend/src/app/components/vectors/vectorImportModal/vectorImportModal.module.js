/* global BUILDCONFIG */
import angular from 'angular';
import _ from 'lodash';
import vectorImportModalTpl from './vectorImportModal.html';

const VectorImportModalComponent = {
    templateUrl: vectorImportModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'VectorImportModalController'
};

class VectorImportModalController {
    constructor(authService, Upload, $scope) {
        this.Upload = Upload;
        this.authService = authService;
        this.$scope = $scope;
        this.BUILDCONFIG = BUILDCONFIG;

        this.initSteps();
    }

    initSteps() {
        this.steps = [
            {
                name: 'SELECT_FILE',
                previous: () => false,
                next: () => 'UPLOADING_FILE',
                onEnter: () => {
                    this.verifyFileCount();
                },
                allowNext: () => this.verifyFileCount(),
                allowClose: () => true
            },
            {
                name: 'UPLOADING_FILE',
                onEnter: () => this.startUpload(),
                next: () => this.uploadError ? 'FAILED' : 'FINISH',
                allowNext: () => {
                    return this.fileUploads &&
                        this.uploadedFileCount + this.abortedUploadCount
                        === this.fileUploads.length;
                },
                onExit: () => this.finishedUpload(),
                allowClose: () => false
            },
            {
                name: 'FINISH',
                previous: () => false,
                allowDone: () => true,
                allowClose: () => true
            },
            {
                name: 'FAILED',
                previous: () => false,
                allowDone: () => false,
                allowClose: () => true
            }
        ];
        this.setCurrentStep(this.steps[0]);
    }

    // common
    getStep(stepName) {
        return this.steps.find(s => s.name === stepName);
    }

    allowClose() {
        const step = this.currentStep;
        return step.allowClose && step.allowClose();
    }

    handleClose() {
        this.dismiss();
    }

    hasPrevious() {
        return this.currentStep.previous && this.currentStep.previous();
    }

    allowPrevious() {
        return this.currentStep.allowPrevious && this.currentStep.allowPrevious();
    }

    handlePrevious() {
        this.setCurrentStep(this.getStep(this.currentStep.previous()));
    }

    hasNext() {
        return this.currentStep.next && this.currentStep.next();
    }

    allowNext() {
        return this.currentStep.allowNext && this.currentStep.allowNext();
    }

    handleNext() {
        this.setCurrentStep(this.getStep(this.currentStep.next()));
    }

    allowDone() {
        return this.currentStep.allowDone && this.currentStep.allowDone();
    }

    handleDone() {
        this.close();
    }

    currentStepIs(step) {
        if (step.constructor === Array) {
            return step.reduce((acc, cs) => {
                return acc || this.currentStepIs(cs);
            }, false);
        }
        return this.currentStep.name === step;
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

    // SELECT_FILE

    verifyFileCount() {
        return Boolean(this.selectedFile);
    }

    fileSelected(file) {
        this.selectedFile = file;
    }

    getFileSize() {
        if (this.selectedFile.length) {
            return this.selectedFile.reduce((acc, f) => {
                return acc + f.size;
            }, 0);
        }
        return 0;
    }

    removeFile() {
        delete this.selectedFile;
        delete this.shapeName;
    }

    // UPLOADING_FILE

    startUpload() {
        this.preventInterruptions();
        if (this.shapeName && this.shapeName.length) {
            this.selectedFile = this.Upload.rename(this.selectedFile, this.shapeName);
        }
        this.upload = this.Upload.upload({
            url: `${BUILDCONFIG.API_HOST}/api/shapes/upload`,
            data: {
                name: this.selectedFile
            },
            headers: {
                Authorization: `Bearer ${this.authService.token()}`
            }
        });
        this.upload.then((resp) => {
            this.shape = _.first(resp.data);
            this.handleNext();
        }, (err) => {
            if (err.status === 400) {
                this.uploadError = err.data;
            } else {
                this.uploadError = `Error: ${err.status} ${err.statusText}`;
            }
            this.handleNext();
        }, (evt) => {
            this.uploadProgress = evt;
            this.progressKB = parseInt(
                100.0 * this.uploadProgress.loaded / this.uploadProgress.total, 10
            );
        });
    }

    abortUpload() {
        this.upload.abort();
        this.aborted = true;
    }

    finishedUpload() {
        this.allowInterruptions();
    }

    uploadDone(upload) {
        upload.finished = true;
        this.uploadedFileCount = this.fileUploads.filter(u => u.finished).length;
        if (this.abortedUploadCount + this.uploadedFileCount === this.fileUploads.length) {
            this.uploadsDone();
        }
        this.$scope.$evalAsync();
    }

    uploadsDone() {
        if (!this.abortedUploadCount) {
            this.handleNext();
        }
    }
}

const VectorImportModalModule = angular.module(
    'components.vectors.vectorImportModal',
    ['ngFileUpload']
);

VectorImportModalModule.controller('VectorImportModalController', VectorImportModalController);
VectorImportModalModule.component('rfVectorImportModal', VectorImportModalComponent);

export default VectorImportModalModule;
