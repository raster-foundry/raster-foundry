/* global AWS, document, window, BUILDCONFIG */

import planetLogo from '../../../../assets/images/planet-logo-light.png';
import awsS3Logo from '../../../../assets/images/aws-s3.png';
import dropboxIcon from '../../../../assets/images/dropbox-icon.svg';

const availableImportTypes = ['local', 'S3', 'Planet'];

export default class SceneImportModalController {
    constructor(
        $scope, $state,
        projectService, Upload, uploadService, authService,
        rollbarWrapperService, datasourceService, userService
    ) {
        'ngInject';
        this.BUILDCONFIG = BUILDCONFIG;
        this.$scope = $scope;
        this.$state = $state;
        this.projectService = projectService;
        this.Upload = Upload;
        this.uploadService = uploadService;
        this.authService = authService;
        this.rollbarWrapperService = rollbarWrapperService;
        this.datasourceService = datasourceService;
        this.availableImportTypes = availableImportTypes;
        this.userService = userService;

        this.planetLogo = planetLogo;
        this.awsS3Logo = awsS3Logo;
        this.dropboxIcon = dropboxIcon;
    }

    $onInit() {
        this.initSteps();
        this.importType = 'local';
        this.s3Config = {
            bucket: '',
            prefix: ''
        };
        this.planetSceneIds = '';
        this.selectedFiles = [];
        this.sceneData = {};
        this.uploadProgressPct = {};
        this.uploadProgressFlexString = {};
        this.abortedUploadCount = 0;
        this.uploadedFileCount = 0;

        this.datasource = this.resolve.datasource || false;
        const onWindowUnload = (event) => {
            if (this.closeCanceller) {
                event.returnValue = 'Leaving this page will cancel your upload. Are you sure?';
            }
        };
        this.$scope.$on('$destroy', () => {
            window.removeEventListener('beforeunload', onWindowUnload);
        });

        this.authService
            .getCurrentUser()
            .then(user => {
                this.hasPlanetCredential = Boolean(user.planetCredential);
                this.planetCredential = user.planetCredential;
            });

        window.addEventListener('beforeunload', onWindowUnload);
    }

    initSteps() {
        this.steps = [];
        if (!this.resolve.datasource) {
            this.steps.push({
                name: 'DATASOURCE_SELECT',
                allowClose: () => true,
                onEnter: () => {
                    this.loadDatasources();
                },
                previous: () => false
            });
        }
        this.steps = this.steps.concat([{
            name: 'IMPORT',
            previous: () => this.resolve.datasource ? false : 'DATASOURCE_SELECT',
            allowPrevious: () => true,
            next: () => {
                if (this.importType === 'S3') {
                    return 'METADATA';
                } else if (this.importType === 'Planet') {
                    return 'IMPORT_PLANET';
                }
                return 'LOCAL_UPLOAD';
            },
            allowNext: () => {
                if (this.importType === 'local') {
                    return true;
                } else if (this.importType === 'S3') {
                    return this.validateS3Config();
                } else if (this.importType === 'Planet') {
                    return this.validatePlanetConfig();
                }
                return true;
            },
            allowClose: () => true,
            onExit: () => {
                this.currentError = null;
            }
        }, {
            name: 'LOCAL_UPLOAD',
            onEnter: () => {
                this.verifyFileCount();
            },
            previous: () => 'IMPORT',
            allowPrevious: () => true,
            next: () => 'METADATA',
            allowNext: () => this.verifyFileCount(),
            allowClose: () => true
        }, {
            name: 'METADATA',
            next: () => {
                if (this.importType === 'S3') {
                    return 'S3_UPLOAD';
                }
                return 'UPLOAD_PROGRESS';
            },
            previous: () => 'IMPORT',
            allowNext: () => true,
            allowPrevious: () => true,
            allowClose: () => true
        }, {
            name: 'UPLOAD_PROGRESS',
            onEnter: () => this.startLocalUpload(),
            next: () => 'IMPORT_SUCCESS',
            allowNext: () => {
                return this.fileUploads &&
                    this.uploadedFileCount + this.abortedUploadCount === this.fileUploads.length;
            },
            onExit: () => this.finishUpload()
        }, {
            name: 'S3_UPLOAD',
            previous: () => 'IMPORT',
            next: () => 'IMPORT_SUCCESS',
            onEnter: () => this.startS3Upload(),
            onExit: () => this.finishUpload()
        }, {
            name: 'IMPORT_SUCCESS',
            allowDone: () => true
        }, {
            name: 'IMPORT_PLANET',
            previous: () => 'IMPORT',
            onEnter: () => this.startPlanetUpload(),
            onExit: () => this.finishUpload(),
            next: () => 'IMPORT_SUCCESS'
        }, {
            name: 'IMPORT_ERROR',
            allowDone: () => true
        }]);

        this.setCurrentStep(this.steps[0]);
    }

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

    validateS3Config() {
        return Boolean(this.s3Config.bucket);
    }

    validatePlanetConfig() {
        return Boolean(this.planetCredential) && Boolean(this.planetSceneIds);
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

    setImportType(type) {
        if (this.availableImportTypes.indexOf(type) >= 0) {
            this.importType = type;
        }
    }

    handleDatasourceSelect(datasource) {
        this.datasource = datasource;
        this.setCurrentStep(this.getStep('IMPORT'));
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
        return Boolean(this.selectedFiles.length);
    }

    startLocalUpload() {
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

    startS3Upload() {
        this.preventInterruptions();
        this.authService
            .getCurrentUser()
            .then(this.createUpload.bind(this))
            .then(upload => {
                this.upload = upload;
                this.uploadsDone();
                return upload;
            }, err => {
                this.uploadError(err);
                this.handlePrevious();
            });
    }

    finishUpload() {
        this.upload.uploadStatus = 'UPLOADED';
        this.uploadService.update(this.upload).then(() => {
            this.allowInterruptions();
        });
    }

    startPlanetUpload() {
        this.preventInterruptions();
        this.authService
            .getCurrentUser()
            .then(this.createUpload.bind(this))
            .then(upload => {
                this.upload = upload;
                this.uploadsDone();
                return upload;
            }, err => {
                this.uploadError(err);
                this.handlePrevious();
            });
    }

    createUpload(user) {
        let uploadObject = {
            files: [],
            datasource: this.datasource.id,
            fileType: 'GEOTIFF',
            uploadStatus: 'UPLOADING',
            visibility: 'PRIVATE',
            organizationId: user.organizationId,
            metadata: {acquisitionDate: this.sceneData.acquisitionDate,
                       cloudCover: this.sceneData.cloudCover}
        };

        if (this.importType === 'local') {
            uploadObject.files = this.selectedFiles.map(f => f.name);
            uploadObject.uploadType = 'LOCAL';
        } else if (this.importType === 'S3') {
            uploadObject.uploadType = 'S3';
            uploadObject.source = encodeURI(this.s3Config.bucket);
        } else if (this.importType === 'Planet') {
            uploadObject.uploadType = 'PLANET';
            uploadObject.metadata.planetKey = this.planetCredential;
            uploadObject.files = this.planetSceneIds.split(',').map(s => s.trim());
            if (!this.hasPlanetCredential && this.planetCredential) {
                this.userService.updatePlanetToken(this.planetCredential);
            }
        }

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
        this.cachedUploadConfig = {s3, bucket};
        this.fileUploads = this.selectedFiles.map(file => this.sendFile(s3, bucket, file));
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
        let upload = {
            file: file,
            api: managedUpload,
            promise: managedUpload.promise(),
            finished: false,
            aborted: false
        };

        let filePath = `s3://${bucket}/${file.name}`;
        if (!this.upload.files.includes(filePath)) {
            this.upload.files.push(filePath);
        }

        managedUpload.on('httpUploadProgress', (progress) => {
            this.handleUploadProgress(upload, progress);
        });

        upload.promise.then(() => {
            this.$scope.$evalAsync(() => {
                this.uploadDone(upload);
            });
        }, (err) => {
            this.uploadError(err, upload);
        });

        this.$scope.$evalAsync(() => {
            this.uploadProgressPct[upload.file.name] = '0%';
            this.uploadProgressFlexString[upload.file.name] = '0 0';
        });
        return upload;
    }

    retryUpload(upload) {
        this.preventInterruptions();

        let uploadIndex = this.fileUploads.findIndex((u) => u === upload);
        this.fileUploads[uploadIndex] = this.sendFile(
            this.cachedUploadConfig.s3, this.cachedUploadConfig.bucket, upload.file
        );
        this.abortedUploadCount = this.fileUploads.filter(u => u.aborted).length;
    }

    abortUpload(upload) {
        upload.api.abort();
        upload.aborted = true;

        this.upload.files = this.upload.files.filter(path => !path.includes(upload.file.name));

        this.$scope.$evalAsync(() => {
            this.uploadProgressPct[upload.file.name] = 'Aborted';
            this.uploadProgressFlexString[upload.file.name] = '1 0';
        });

        this.abortedUploadCount = this.fileUploads.filter(u => u.aborted).length;
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

    uploadError(err, upload) {
        if (!upload.aborted) {
            Object.assign(upload, {error: err});
            this.$scope.$evalAsync(() => {
                this.uploadProgressPct[upload.file.name] = 'Errored';
                this.uploadProgressFlexString[upload.file.name] = '1 0';
            });
            this.$scope.$evalAsync();
        }
    }

    handleUploadProgress(upload, progress) {
        upload.progress = progress;
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
    }

    removeAllFiles() {
        this.selectedFiles = [];
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
        if (this.closeCanceller) {
            this.closeCanceller();
            delete this.closeCanceller;
        }
        if (this.locationChangeCanceller) {
            this.locationChangeCanceller();
            delete this.locationChangeCanceller;
        }
    }
}
