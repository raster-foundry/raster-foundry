import assetLogo from '../../../assets/images/logo-raster-foundry.png';
import ffcSpace from '../../../assets/images/ffc-space.png';
import videom4v from '../../../assets/video/ffc-space.m4v';
import videoogg from '../../../assets/video/ffc-space.ogg';
import videowebm from '../../../assets/video/ffc-space.webm';

class LoginController {
    constructor($log, $state, store, auth) {
        'ngInject';
        const self = this;
        self.$log = $log;
        self.$state = $state;
        self.store = store;

        self.videom4v = videom4v;
        self.videoogg = videoogg;
        self.videowebm = videowebm;
        self.ffcSpace = ffcSpace;
        self.assetLogo = assetLogo;

        $log.debug('LoginController initialized');

        function onLoginSuccess(profile, idToken, accessToken) {
            store.set('profile', profile);
            store.set('token', idToken);
            store.set('accessToken', accessToken);
            $state.go('library');
        }

        function onLoginFailed(err) {
            $log.log('Error :(', err);
        }

        self.signin = function () {
            auth.signin({
                authParams: {
                    // Specify the scopes you want to retrieve
                    scope: 'openid name email',
                    popup: true
                },
                primaryColor: '#5e509b',
                icon: assetLogo,
                closable: false
            }, onLoginSuccess, onLoginFailed);
        };

        if (auth.isAuthenticated) {
            $state.go('library');
        } else {
            self.signin();
        }
    }
}

export default LoginController;
