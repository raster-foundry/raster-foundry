/* global Intercom, BUILDCONFIG */
export default (app) => {
    class IntercomService {
        constructor($resource, $q, $http, APP_CONFIG, angularLoad) {
            'ngInject';

            this.$q = $q;
            this.$http = $http;
            this.angularLoad = angularLoad;
            this.scriptLoaded = false;
            this.appId = BUILDCONFIG.INTERCOM_APP_ID || APP_CONFIG.intercomAppId;
            this.srcUrl = `https://widget.intercom.io/widget/${this.appId}`;
        }

        load() {
            return this.angularLoad.loadScript(this.srcUrl).then(() => {
                this.scriptLoaded = true;
            });
        }

        bootWithUser(user) {
            if (!this.scriptLoaded && this.appId !== 'disabled') {
                this.load().then(() => {
                    this.doBoot(user);
                });
            } else if (this.appId !== 'disabled') {
                this.doBoot(user);
            }
        }

        doBoot(user) {
            const bootData = Object.assign(user, { 'app_id': this.appId });
            Intercom('boot', bootData);
        }

        shutdown() {
            if (this.scriptLoaded) {
                Intercom('shutdown');
            }
        }
    }

    app.service('intercomService', IntercomService);
};
