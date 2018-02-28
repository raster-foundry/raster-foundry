import angular from 'angular';

class OrganizationTeamsController {
    constructor(modalService) {
        this.modalService = modalService;

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
