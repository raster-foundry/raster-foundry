/* globals BUILDCONFIG */

import angular from 'angular';
import _ from 'lodash';

class PlatformEmailController {
    constructor(
        $stateParams, $log, $timeout,
        platformService
    ) {
        'ngInject';
        this.$stateParams = $stateParams;
        this.$log = $log;
        this.$timeout = $timeout;

        this.platformService = platformService;

        this.appName = BUILDCONFIG.APP_NAME;
    }

    $onInit() {
        this.platformService
            .getPlatform(this.$stateParams.platformId)
            .then((platform) => {
                this.platform = platform;
                this.platformBuffer = _.cloneDeep(this.platform);
                this.platformBuffer.privateSettings = {emailPassword: ''};
                this.platformBuffer.publicSettings.platformHost =
                    this.platformBuffer.publicSettings.platformHost ||
                    'app.rasterfoundry.com';
                this.platformBuffer.publicSettings.emailSmtpEncryption =
                    this.platformBuffer.publicSettings.emailSmtpEncryption ||
                    'ssl';
            });
    }

    onToggleChange(type) {
        if (type === 'ingest') {
            this.platformBuffer.publicSettings.emailIngestNotification =
                !this.platformBuffer.publicSettings.emailIngestNotification;
        } else if (type === 'aoi') {
            this.platformBuffer.publicSettings.emailAoiNotification =
                !this.platformBuffer.publicSettings.emailAoiNotification;
        } else if (type === 'export') {
            this.platformBuffer.publicSettings.emailExportNotification =
                !this.platformBuffer.publicSettings.emailExportNotification;
        }
    }

    onSubmit() {
        this.saved = false;
        this.platformService.updatePlatform(this.platformBuffer).then(
            () => {
                this.saved = true;
                this.$timeout(() => {
                    this.saved = false;
                }, 500);
            },
            err => {
                this.error = err;
                this.$timeout(() => {
                    delete this.error;
                }, 1000);
            }
        );
    }

    getButtonText() {
        if (this.saved) {
            return 'Saved';
        }
        if (this.error) {
            return 'Failed';
        }
        return 'Save';
    }

    setEncryptionMethod(encryption) {
        this.platformBuffer.publicSettings.emailSmtpEncryption = encryption;
    }
}

const PlatformEmailModule = angular.module('pages.platform.settings.email', []);

PlatformEmailModule.controller(
    'PlatformEmailController',
    PlatformEmailController
);

export default PlatformEmailModule;
