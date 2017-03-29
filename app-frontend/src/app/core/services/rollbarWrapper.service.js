/* global process */

export default (app) => {
    class RollbarWrapperService {
        constructor($resource, APP_CONFIG, Rollbar) {
            'ngInject';
            this.Rollbar = Rollbar;
            this.accessToken = APP_CONFIG.rollbarClientToken;
            this.env = process.env.NODE_ENV || 'production';
        }

        init(user = {}) {
            this.Rollbar.configure({
                accessToken: this.accessToken,
                captureUncaught: true,
                payload: {
                    environment: this.env,
                    person: user
                }
            });
        }
    }

    app.service('rollbarWrapperService', RollbarWrapperService);
};
