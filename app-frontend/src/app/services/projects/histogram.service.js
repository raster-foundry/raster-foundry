export default (app) => {
    class ProjectHistogramService {
        constructor($http, authService, APP_CONFIG) {
            'ngInject';
            this.authService = authService;
            this.$http = $http;
            this.tileServer = `${APP_CONFIG.tileServerLocation}`;
        }

        getHistogram(project, scenes) {
            return this.$http.post(
                `${this.tileServer}/${project}/histogram/?token=${this.authService.token()}`,
                scenes
            );
        }


    }

    app.service('projectHistogramService', ProjectHistogramService);
};
