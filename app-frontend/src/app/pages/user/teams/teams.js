class UserTeamsController {
    constructor(teamRoles, teams) {
        this.teamRoles = teamRoles;
        this.teams = teams;
    }

    getUserTeamRole(teamId) {
        return this.teamRoles.find(role => role.groupId === teamId).groupRole;
    }
}

const Module = angular.module('user.teams', []);

Module
    .controller('UserTeamsController', UserTeamsController);

export default Module;
