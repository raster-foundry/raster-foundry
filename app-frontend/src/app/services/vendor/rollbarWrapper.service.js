export default (app) => {
    class RollbarWrapperService {
        constructor($resource, APP_CONFIG, Rollbar) {
            'ngInject';
            this.Rollbar = Rollbar;
            this.accessToken = APP_CONFIG.rollbarClientToken;
            this.env = APP_CONFIG.clientEnvironment;
        }

        init(user = {}) {
            if (this.env !== 'development') {
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
    }

    app.service('rollbarWrapperService', RollbarWrapperService);
};
