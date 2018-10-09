import ImportsController from './imports.controller.js';
import autoInject from '_appRoot/autoInject';

const ImportsModule = angular.module('pages.imports', []);

ImportsModule.resolve = {
    user: ($stateParams, authService) => {
        if ($stateParams.userId === 'me') {
            return authService.getCurrentUser();
        }
        return false;
    },
    userRoles: (authService) => {
        return authService.fetchUserRoles();
    },
    platform: (userRoles, platformService) => {
        const platformRole = userRoles.find(r =>
            r.groupType === 'PLATFORM'
        );

        return platformService.getPlatform(platformRole.groupId);
    }
};
autoInject(ImportsModule);

ImportsModule.controller('ImportsController', ImportsController);

export default ImportsModule;
