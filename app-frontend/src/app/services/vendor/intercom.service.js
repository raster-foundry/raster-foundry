/* global Intercom, BUILDCONFIG */
import _ from 'lodash';
export default app => {
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
            let cleanUser = _.clone(user);
            if (user.email === user.name) {
                delete cleanUser.name;
            }
            if (!this.scriptLoaded && this.appId !== 'disabled') {
                this.load().then(() => {
                    this.doBoot(cleanUser);
                });
            } else if (this.appId !== 'disabled') {
                this.doBoot(cleanUser);
            }
        }

        doBoot(user) {
            const bootData = Object.assign(user, { app_id: this.appId });
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
