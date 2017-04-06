/* global AWS */
/* global document */

export default class ImportModalController {
    constructor($scope, $state, projectService, Upload,
                uploadService, userService, rollbarWrapperService) {
        'ngInject';
        this.$scope = $scope;
        this.$state = $state;
        this.projectService = projectService;
        this.Upload = Upload;
        this.uploadService = uploadService;
        this.userService = userService;
        this.rollbarWrapperService = rollbarWrapperService;
    }

    $onInit() {
        this.steps = [
            'IMPORT',
            'LOCAL_UPLOAD',
            'UPLOAD_PROGRESS',
            'IMPORT_SUCCESS',
            'IMPORT_ERROR'
        ];
        this.importType = 'local';
        this.selectedFiles = [];
        this.uploadProgressPct = {};
        this.uploadProgressFlexString = {};
        this.setCurrentStep(this.steps[0]);
    }

    shouldShowFileList() {
        return this.selectedFiles.length;
    }

    projectAttributeIs(attr, value) {
        if (this.projectBuffer.hasOwnProperty(attr)) {
            return this.projectBuffer[attr] === value;
        }
        return false;
    }

    setProjectAttribute(attr, value) {
        this.projectBuffer[attr] = value;
    }

    currentStepIs(step) {
        if (step.constructor === Array) {
            return step.reduce((acc, cs) => {
                return acc || this.currentStepIs(cs);
            }, false);
        }
        return this.currentStep === step;
    }

    currentStepIsNot(step) {
        if (step.constructor === Array) {
            return step.reduce((acc, cs) => {
                return acc && this.currentStepIsNot(cs);
            }, true);
        }
        return this.currentStep !== step;
    }

    getCurrentStepIndex() {
        return this.steps.indexOf(this.currentStep);
    }

    hasNextStep() {
        const stepLimit = this.steps.length - 1;
        return this.getCurrentStepIndex() < stepLimit;
    }

    hasPreviousStep() {
        return this.getCurrentStepIndex() > 0;
    }

    setCurrentStep(step) {
        this.currentStep = step;
        this.onStepEnter();
    }

    gotoPreviousStep() {
        if (this.hasPreviousStep()) {
            this.setCurrentStep(this.steps[this.getCurrentStepIndex() - 1]);
        }
    }

    gotoNextStep() {
        if (this.hasNextStep()) {
            this.setCurrentStep(this.steps[this.getCurrentStepIndex() + 1]);
        }
    }

    gotoStep(step) {
        const stepIndex = this.steps.indexOf(step);
        if (stepIndex) {
            this.setCurrentStep(this.steps[stepIndex]);
        }
    }

    gotoSceneBrowser() {
        if (this.project) {
            this.close();
            this.$state.go('browse', {projectid: this.project.id});
        }
    }

    createProject() {
        return this.projectService.createProject(this.projectBuffer.name);
    }

    validateProjectName() {
        if (this.projectBuffer.name) {
            this.showProjectCreateError = false;
            return true;
        }
        this.projectCreateErrorText = 'A name is needed for your new project';
        this.showProjectCreateError = true;
        return false;
    }

    verifyFileCount() {
        if (this.selectedFiles.length) {
            this.allowNext = true;
        } else {
            this.allowNext = false;
        }
    }

    handleNext() {
        if (this.allowNext) {
            if (this.currentStepIs('LOCAL_UPLOAD')) {
                this.gotoNextStep();
                this.allowNext = false;
                this.startUpload();
            } else {
                this.gotoNextStep();
            }
        }
    }

    onStepEnter() {
        if (this.currentStepIs('LOCAL_UPLOAD')) {
            this.verifyFileCount();
        } else if (this.currentStepIs('IMPORT')) {
            this.allowNext = true;
        }
    }

    startUpload() {
        this.userService
            .getCurrentUser()
            .then(this.createUpload.bind(this))
            .then(upload => {
                this.upload = upload;
                return upload;
            })
            .then(this.getUploadCredentials.bind(this))
            .then(this.sendFiles.bind(this));
    }


    createUpload(user) {
        return this.uploadService.create({
            files: this.selectedFiles.map(f => f.name),
            datasource: this.resolve.datasource.id,
            fileType: 'GEOTIFF',
            uploadType: 'LOCAL',
            uploadStatus: 'UPLOADING',
            visibility: 'PRIVATE',
            organizationId: user.organizationId,
            metadata: {}
        });
    }

    getUploadCredentials(upload) {
        return this.uploadService.credentials(upload);
    }

    sendFiles(credentialData) {
        this.uploadedFileCount = 0;
        const parser = document.createElement('a');
        parser.href = credentialData.bucketPath;
        const bucket = decodeURI(parser.hostname.split('.')[0] + parser.pathname);
        const config = new AWS.Config({
            accessKeyId: credentialData.credentials.AccessKeyId,
            secretAccessKey: credentialData.credentials.SecretAccessKey,
            sessionToken: credentialData.credentials.SessionToken
        });
        const s3 = new AWS.S3(config);
        this.selectedFiles.forEach(f => this.sendFile(s3, bucket, f));
    }

    sendFile(s3, bucket, file) {
        const upload = new AWS.S3.ManagedUpload({
            params: {
                Bucket: bucket,
                Key: file.name,
                Body: file
            },
            service: s3
        });
        const uploadPromise = upload.promise();

        upload.on('httpUploadProgress', this.handleUploadProgress.bind(this));
        uploadPromise.then(() => {
            this.$scope.$evalAsync(() => {
                this.uploadDone();
            });
        }, err => {
            this.uploadError(err);
        });
    }

    uploadDone() {
        this.uploadedFileCount += 1;
        if (this.uploadedFileCount === this.selectedFiles.length) {
            this.uploadsDone();
        }
    }

    uploadsDone() {
        this.upload.uploadStatus = 'UPLOADED';
        this.uploadService.update(this.upload).then(() => {
            this.gotoStep('IMPORT_SUCCESS');
            this.allowNext = true;
        });
    }

    uploadError(err) {
        this.rollbarWrapperService.error(err);
    }

    handleUploadProgress(progress) {
        this.$scope.$evalAsync(() => {
            this.uploadProgressPct[progress.key] =
                `${(progress.loaded / progress.total * 100).toFixed(1)}%`;
            this.uploadProgressFlexString[progress.key] =
                `${(progress.loaded / progress.total).toFixed(3)} 0`;
        });
    }

    closeWithData(data) {
        this.close({ $value: data });
    }

    filesSelected(files) {
        this.selectedFiles = files;
        this.verifyFileCount();
    }

    getTotalFileSize() {
        if (this.selectedFiles.length) {
            return this.selectedFiles.reduce((acc, f) => {
                return acc + f.size;
            }, 0);
        }
        return 0;
    }

    removeFileAtIndex(index) {
        this.selectedFiles.splice(index, 1);
        this.verifyFileCount();
    }

    removeAllFiles() {
        this.selectedFiles = [];
        this.verifyFileCount();
    }
}
