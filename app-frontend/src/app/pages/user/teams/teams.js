class UserTeamsController {
    constructor(
        $scope, $state,
        teamService, modalService,
        platform, teamRoles, teams, user
    ) {
        'ngInject';
        $scope.autoInject(this, arguments);
    }

    getUserTeamRole(teamId) {
        return this.teamRoles.find(role => role.groupId === teamId).groupRole;
    }

    membershipPending(team) {
        return !!this.teamRoles.find(r =>
                r.groupType === 'TEAM' &&
                r.groupId === team.id &&
                r.membershipStatus === 'INVITED'
            );
    }

    updateUserMembershipStatus(team, isApproved) {
        if (isApproved) {
            const role = this.teamRoles.find(r =>
                r.groupType === 'TEAM' &&
                r.groupId === team.id
            ).groupRole;

            if (role) {
                this.teamService.addUserWithRole(
                    this.platform.id,
                    team.organizationId,
                    team.id,
                    role,
                    this.user.id
                ).then(resp => {
                    this.$state.reload();
                });
            }
        } else {
            this.teamService.removeUser(
                this.platform.id,
                team.organizationId,
                team.id,
                this.user.id
            ).then(resp => {
                this.$state.reload();
            });
        }
    }

    newTeamModal() {
        this.modalService.open({
            component: 'rfTeamModal',
            resolve: {
                chooseOrg: true
            }
        }).result.then((result) => {
            this.teamService
                .createTeam(result.platform.groupId, result.organization.groupId, result.name)
                .then(() => {
                    this.$state.reload();
                }, err => {
                    this.$log.error(err);
                });
        }).catch(() => {});
    }
}

const Module = angular.module('user.teams', []);

Module
    .controller('UserTeamsController', UserTeamsController);

export default Module;
