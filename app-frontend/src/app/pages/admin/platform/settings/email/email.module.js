/* globals _ */

import angular from 'angular';

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
    }

    $onInit() {
        this.platformService
            .getPlatform(this.$stateParams.platformId)
            .then((platform) => {
                this.platform = platform;
                this.platformBuffer = _.cloneDeep(this.platform);
                this.platformBuffer.privateSettings = {emailPassword: ''};
            });
    }

    onToggleChange(type) {
        if (type === 'ingest') {
            this.platformBuffer.publicSettings.emailIngestNotification =
                !this.platformBuffer.publicSettings.emailIngestNotification;
        } else if (type === 'aoi') {
            this.platformBuffer.publicSettings.emailAoiNotification =
                !this.platformBuffer.publicSettings.emailAoiNotification;
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
                this.$log.error(err);
            }
        );
    }
}

const PlatformEmailModule = angular.module('pages.platform.settings.email', []);

PlatformEmailModule.controller(
    'PlatformEmailController',
    PlatformEmailController
);

export default PlatformEmailModule;
