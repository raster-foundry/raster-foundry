class UserTeamsController {
    constructor(teams) {
        this.teams = teams;
    }
}

const Module = angular.module('user.teams', []);

Module
    .controller('UserTeamsController', UserTeamsController);

export default Module;
