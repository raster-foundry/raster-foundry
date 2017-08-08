/* globals window screen document */
import dropboxIcon from '../../../../assets/images/dropbox-icon.svg';
import dropboxWordmark from '../../../../assets/images/dropbox-wordmark.svg';
import planetLogo from '../../../../assets/images/planet-logo-light.png';

class ConnectionsController {
    constructor(
        $log, $state, $interval, $uibModal, $location,
        dropboxService, authService, userService, APP_CONFIG
    ) {
        'ngInject';

        this.$log = $log;
        this.$state = $state;
        this.$interval = $interval;
        this.$uibModal = $uibModal;
        this.$location = $location;
        this.config = APP_CONFIG;

        this.authService = authService;
        this.userService = userService;
        this.dropboxService = dropboxService;

        this.dropboxIcon = dropboxIcon;
        this.dropboxWordmark = dropboxWordmark;
        this.planetLogo = planetLogo;
    }

    $onInit() {
        this.authService.getCurrentUser().then((user) => {
            this.user = user;
            this.dropboxConnected = Boolean(user.dropboxCredential);
        });
    }

    connectToDropbox() {
        function calculateOffsets(w, h) {
            // yanked https://stackoverflow.com/questions/4068373/center-a-popup-window-on-screen
            // Fixes dual-screen position                         Most browsers      Firefox
            let dualScreenLeft = typeof window.screenLeft !== 'undefined' ?
                window.screenLeft : screen.left;
            let dualScreenTop = typeof window.screenTop !== 'undefined' ?
                window.screenTop : screen.top;

            let width = (
                window.innerWidth ? window.innerWidth : document.documentElement.clientWidth
            ) ? document.documentElement.clientWidth : screen.width;
            let height = (
                window.innerHeight ? window.innerHeight : document.documentElement.clientHeight
            ) ? document.documentElement.clientHeight : screen.height;

            let left = width / 2 - w / 2 + dualScreenLeft;
            let top = height / 2 - h / 2 + dualScreenTop;
            return {left: left, top: top};
        }

        let offsets = calculateOffsets(500, 500);
        let origin = `${this.$location.protocol()}://${this.$location.host()}` +
            `${this.$location.port() !== 443 ? ':' + this.$location.port() : ''}`;
        let dropboxOauthUrl = 'https://dropbox.com/oauth2/authorize?response_type=code' +
            `&client_id=${this.config.dropboxClientId}` +
            `&redirect_uri=${origin}/callback`;

        let authWindow = window.open(
            dropboxOauthUrl,
            '_blank',
            'toolbar=0,status=0,width=500,height=500,' +
                `left=${offsets.left},top=${offsets.top}`
        );

        let interval = this.$interval(() => {
            let uri;
            if (authWindow.closed) {
                this.$log.log('OAuth window closed manually');
                this.$interval.cancel(interval);
            }
            try {
                uri = authWindow.location.href;
                if (uri.indexOf('error') > -1) {
                    this.$interval.cancel(interval);
                    authWindow.close();
                    this.onDropboxError(uri);
                }
                if (uri !== 'about:blank') {
                    this.$interval.cancel(interval);
                    authWindow.close();
                    this.onDropboxCallback(uri);
                }
            } catch (err) {
                // ignore errors
            }
        }, 500);
    }

    reconnectToDropbox() {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }
        this.activeModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Reconnect to Dropbox?',
                subtitle: () => '',
                content: () =>
                    '<div class="text-center">' +
                    'This is only neccessary if you have revoked ' +
                    'RasterFoundry\'s access to your Dropbox account.' +
                    '</div>',
                confirmText: () => 'Reconnect',
                cancelText: () => 'Cancel'
            }
        });
        this.activeModal.result.then(
            () => {
                this.connectToDropbox();
            });
    }

    onDropboxError(uri) {
        this.$log.log(uri);
        this.activeModal = this.$uibModal.open({
            component: 'rfConfirmationModal',
            resolve: {
                title: () => 'Dropbox Error',
                subtitle: () => '',
                content: () =>
                    '<div class="text-center color-danger">' +
                    'There was an error while connecting your account to dropbox' +
                    '</div>',
                confirmText: () => 'Try Again',
                cancelText: () => 'Cancel'
            }
        });
        this.activeModal.result.then(() => {
            this.connectToDropbox();
        });
    }

    onDropboxCallback(uri) {
        this.dropboxService.confirmCode(uri).then(() => {
            this.dropboxConnected = true;
        }, () => {
            this.$log.error('Dropbox setup failed.', uri);
            this.activeModal = this.$uibModal.open({
                component: 'rfConfirmationModal',
                resolve: {
                    title: () => 'API Error',
                    subtitle: () => '',
                    content: () =>
                        '<div class="text-center color-danger">' +
                        'There was an error while connecting your account to dropbox' +
                        '</div>',
                    confirmText: () => 'Try Again',
                    cancelText: () => 'Cancel'
                }
            });
            this.activeModal.result.then(() => {
                this.connectToDropbox();
            });
        });
    }

    connectToPlanet() {
        this.activeModal = this.$uibModal.open({
            component: 'rfEnterTokenModal',
            resolve: {
                title: () => 'Enter your Planet API Token'
            }
        });
        this.activeModal.result.then((token) => {
            this.userService.updatePlanetToken(token).then(() => {
                this.user.planetCredential = true;
            }, (err) => {
                this.$log.log('There was an error updating the user with a planet api token', err);
            });
        });
    }
}

export default ConnectionsController;
