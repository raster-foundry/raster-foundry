/* globals BUILDCONFIG */

/* globals _ document */
export default (app) => {
    class DropboxService {
        constructor($resource, authService) {
            'ngInject';

            this.authService = authService;
            this.DropboxSetup = $resource(
                `${BUILDCONFIG.API_HOST}/api/users/dropbox-setup`, {}, {
                    confirm: {
                        method: 'POST'
                    }
                }
            );
        }

        confirmCode(href) {
            let uri = document.createElement('a');
            uri.href = href;
            let code = _.last(uri.search.split('='));
            return this.DropboxSetup.confirm({
                userid: this.authService.profile().user_id
            }, {
                authorizationCode: code,
                redirectURI: uri.origin + uri.pathname
            }).$promise;
        }
    }

    app.service('dropboxService', DropboxService);
};
