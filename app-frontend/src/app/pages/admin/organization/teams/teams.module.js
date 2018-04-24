import angular from 'angular';

class OrganizationTeamsController {
    constructor(
      $log,
      modalService, teamService
    ) {
        this.$log = $log;

        this.modalService = modalService;
        this.teamService = teamService;

        this.teams = [
            {
                id: '1',
                name: 'Team one',
                users: [1, 2, 3, 4, 5]
            },
            {
                id: '2',
                name: 'Team two',
                users: [1, 2, 3, 4, 5]
            },
            {
                id: '3',
                name: 'Team three',
                users: [1, 2, 3, 4, 5]
            },
            {
                id: '4',
                name: 'Team four',
                users: [1, 2, 3, 4, 5]
            }
        ];

        this.teams.forEach((team) => Object.assign(team, {
            options: {
                items: this.itemsForTeam(team)
            }
        }));
    }

    $onInit() {
        this.teamService.createTeam('test team 1').then((resp) => {
            this.$log.log('create team', resp);
            this.listTeams();
        }, (err) => {
            this.$log.log(err);
        });
    }

    listTeams() {
        this.teamService.query().then((resp) => {
            this.$log.log('list teams', resp);
            this.getTeamById(resp.results[0].id);
        }, (err) => {
            this.$log.log(err);
        });
    }

    getTeamById(id) {
        this.teamService.get(id).then((res) => {
            this.$log.log('get a team by id', res);
            this.updateTeam(res);
        }, (err) => {
            this.$log.log(err);
        });
    }

    updateTeam(res) {
        this.teamService.updateTeam(Object.assign({}, res, {
            name: 'updated team name',
            settings: {
                test: 'temp'
            }
        })).then((resp) => {
            this.$log.log('updated team', resp);
            this.deleteTeam(resp);
        }, (error) => {
            this.$log.log(error);
        });
    }

    deleteTeam(res) {
        this.teamService.deleteTeam(res).then((resp) => {
            this.$log.log('delete team', resp);
        }, (error) => {
            this.$log.log(error);
        });
    }

    itemsForTeam(team) {
        /* eslint-disable */
        return [
            {
                label: 'Edit',
                callback: () => {
                    console.log('edit callback for team:', team);
                },
                classes: []
            },
            {
                label: 'Add to team...',
                callback: () => {
                    console.log('Add to team... callback for team:', team);
                },
                classes: []
            },
            {
                label: 'Delete',
                callback: () => {
                    console.log('delete callback for team:', team);
                },
                classes: ['color-danger']
            }
        ];
        /* eslint-enable */
    }

    newTeamModal() {
        this.modalService.open({
            component: 'rfTeamModal',
            resolve: { },
            size: 'sm'
        }).result.then((result) => {
            // eslint-disable-next-line
            console.log('team modal closed with value:', result);
        });
    }
}

const OrganizationTeamsModule = angular.module('pages.organization.teams', []);
OrganizationTeamsModule.controller('OrganizationTeamsController', OrganizationTeamsController);

export default OrganizationTeamsModule;
