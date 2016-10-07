export default (app) => {
    class Config {
        constructor(env, $resource) {
            this.env = env;
            this.Config = $resource('/config/');
        }
    }

    class ConfigProvider {
        constructor() {
            this.env = null;
        }

        init(env) {
            this.env = env;
        }

        $get($resource) {
            'ngInject';
            return new Config(this.env, $resource);
        }
    }

    app.service('config', Config);
    app.provider('config', ConfigProvider);
};
