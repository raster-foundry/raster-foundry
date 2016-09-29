import assetLogo from '../../../assets/images/logo-raster-foundry.png';

// rfNavBar controller class
export default class NavBarController {
    constructor($log, $state, store, auth, $scope) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.store = store;
        this.auth = auth;
        this.optionsOpen = false;
        this.isLoggedIn = auth.isAuthenticated;
        this.profile = {};

        this.assetLogo = assetLogo;

        $log.debug('Navbar controller initialized');

        $scope.$watch(function () {
            return auth.isAuthenticated;
        }, function (isAuthenticated) {
            if (isAuthenticated) {
                store.set('profile', this.auth.profile);
                this.profile = this.auth.profile;
                store.set('token', this.auth.idToken);
                store.set('accessToken', this.auth.accessToken);
                this.isLoggedIn = true;
            }
        }.bind(this));
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
        }, function () {});
    }

    logout() {
        this.auth.signout();
        this.profile = {};
        this.isLoggedIn = false;
    }
}
