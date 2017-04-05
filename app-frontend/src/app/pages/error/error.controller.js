class ErrorController {
    constructor( // eslint-disable-line max-params
        $window, $state, APP_CONFIG, $interval, config
    ) {
        'ngInject';

        this.config = config;
        this.$state = $state;
        this.$window = $window;

        if (!APP_CONFIG.error) {
            $state.go('login');
        } else {
            $interval(this.getConfig.bind(this), 5000);
        }
    }

    getConfig() {
        this.config.Config.get().$promise.then(() => {
            this.$window.location.reload();
        });
    }
}
export default ErrorController;
