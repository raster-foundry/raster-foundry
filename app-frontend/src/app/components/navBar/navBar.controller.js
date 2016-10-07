import assetLogo from '../../../assets/images/logo-raster-foundry.png';

// rfNavBar controller class
export default class NavBarController {
    constructor( // eslint-disable-line max-params
        $log, $state, store, auth, $scope, APP_CONFIG
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.store = store;
        this.auth = auth;

        if (APP_CONFIG.error) {
            this.loadError = true;
        }

        this.optionsOpen = false;
        this.isLoggedIn = auth.isAuthenticated;
        this.profile = {};

        this.assetLogo = assetLogo;

        $log.debug('Navbar controller initialized');

        $scope.$watch(function () {
            return auth.isAuthenticated;
        }, (isAuthenticated) => {
            if (isAuthenticated) {
                store.set('profile', this.auth.profile);
                this.profile = this.auth.profile;
                store.set('token', this.auth.idToken);
                store.set('accessToken', this.auth.accessToken);
                this.isLoggedIn = true;
            }
        });
    }

    signin() {
        this.auth.signin({
            authParams: {
                // Specify the scopes you want to retrieve
                scope: 'openid name email',
                popup: true
            },
            primaryColor: '#5e509b',
            icon: assetLogo,
            closable: true
        }, () => {});
    }

    logout() {
        this.auth.signout();
        this.profile = {};
        this.isLoggedIn = false;
    }
}
