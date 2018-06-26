class UserTeamsController {
    constructor(teams) {
        'ngInject';

        this.userRoles = teams.roles;
        this.teams = [];
        teams.teams.forEach(teamPromises => {
            teamPromises.then(team => {
                this.teams.push(team);
            });
        });
    }

    getUserTeamRole(teamId) {
        return this.userRoles.find(role => role.groupId === teamId).groupRole;
    }
}

const Module = angular.module('user.teams', []);

Module
    .controller('UserTeamsController', UserTeamsController);

export default Module;
