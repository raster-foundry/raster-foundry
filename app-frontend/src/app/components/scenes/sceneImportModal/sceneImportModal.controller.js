/* global AWS */
/* global document */
/* global window */

export default class SceneImportModalController {
    constructor(
        $scope, $state,
        projectService, Upload, uploadService, authService,
        rollbarWrapperService, datasourceService
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$state = $state;
        this.projectService = projectService;
        this.Upload = Upload;
        this.uploadService = uploadService;
        this.authService = authService;
        this.rollbarWrapperService = rollbarWrapperService;
        this.datasourceService = datasourceService;
    }

    $onInit() {
        this.initSteps();
        this.importType = 'local';
        this.selectedFiles = [];
        this.uploadProgressPct = {};
        this.uploadProgressFlexString = {};
        this.setCurrentStepIndex(0);
        this.datasource = this.resolve.datasource || false;
        const onWindowUnload = (event) => {
            if (this.closeCanceller) {
                event.returnValue = 'Leaving this page will cancel your upload. Are you sure?';
            }
        };
        this.$scope.$on('$destroy', () => {
            window.removeEventListener('beforeunload', onWindowUnload);
        });
        window.addEventListener('beforeunload', onWindowUnload);
    }

    initSteps() {
        this.steps = [];
        if (!this.resolve.datasource) {
            this.steps.push('DATASOURCE_SELECT');
        }
        this.steps = this.steps.concat([
            'IMPORT',
            'LOCAL_UPLOAD',
            'UPLOAD_PROGRESS',
            'IMPORT_SUCCESS',
            'IMPORT_ERROR'
        ]);
    }

    shouldShowFileList() {
        return this.selectedFiles.length;
    }

    shouldShowList() {
        return !this.isLoadingDatasources &&
            this.datasources.count &&
            this.datasources.count > 0;
    }

    shouldShowPagination() {
        return !this.isLoadingDatasources &&
            !this.isErrorLoadingDatasources &&
            this.datasources.count &&
            this.datasources.count > this.pageSize;
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

    handleDatasourceSelect(datasource) {
        this.datasource = datasource;
        this.gotoNextStep();
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

    setCurrentStepIndex(index) {
        this.currentStep = this.steps[index];
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
        if (stepIndex >= 0) {
            this.setCurrentStep(this.steps[stepIndex]);
        }
    }

    gotoSceneBrowser() {
        if (this.project) {
            this.close();
            this.$state.go('project.scenes.browse', {projectid: this.project.id});
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
        } else if (this.currentStepIs('DATASOURCE_SELECT')) {
            this.loadDatasources();
        }
    }

    startUpload() {
        this.preventInterruptions();
        this.authService
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
        let uploadObject = {
            files: this.selectedFiles.map(f => f.name),
            datasource: this.datasource.id,
            fileType: 'GEOTIFF',
            uploadType: 'LOCAL',
            uploadStatus: 'UPLOADING',
            visibility: 'PRIVATE',
            organizationId: user.organizationId,
            metadata: {}
        };
        if (this.resolve.project) {
            uploadObject.projectId = this.resolve.project.id;
        }
        return this.uploadService.create(uploadObject);
    }

    getUploadCredentials(upload) {
        return this.uploadService.credentials(upload);
    }

    sendFiles(credentialData) {
        this.uploadedFileCount = 0;
        const parser = document.createElement('a');
        parser.href = credentialData.bucketPath;
        const bucket = decodeURI(parser.hostname.split('.')[0] + parser.pathname);
        this.upload.files = this.upload.files.map(f => `s3://${bucket}/${f}`);
        const config = new AWS.Config({
            accessKeyId: credentialData.credentials.AccessKeyId,
            secretAccessKey: credentialData.credentials.SecretAccessKey,
            sessionToken: credentialData.credentials.SessionToken
        });
        const s3 = new AWS.S3(config);
        this.selectedFiles.forEach(f => this.sendFile(s3, bucket, f));
    }

    sendFile(s3, bucket, file) {
        const managedUpload = new AWS.S3.ManagedUpload({
            params: {
                Bucket: bucket,
                Key: file.name,
                Body: file
            },
            service: s3
        });
        const uploadPromise = managedUpload.promise();

        managedUpload.on('httpUploadProgress', this.handleUploadProgress.bind(this));
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

        this.allowInterruptions();
    }

    uploadError(err) {
        this.rollbarWrapperService.error(err);

        this.allowInterruptions();
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

    loadDatasources(page = 1) {
        this.isLoadingDatasources = true;
        this.isErrorLoadingDatasources = false;
        this.datasourceService.query({
            sort: 'createdAt,desc',
            pageSize: this.pageSize,
            page: page - 1
        }).then(
            datasourceResponse => {
                this.datasources = datasourceResponse;
                this.currentPage = datasourceResponse.page + 1;
            },
            () => {
                this.isErrorLoadingDatasources = true;
            })
            .finally(() => {
                this.isLoadingDatasources = false;
            }
        );
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
        if (this.closeCanceller || this.locationChangeCanceller) {
            this.closeCanceller();
            delete this.closeCanceller;
            this.locationChangeCanceller();
            delete this.locationChangeCanceller();
        }
    }
}
