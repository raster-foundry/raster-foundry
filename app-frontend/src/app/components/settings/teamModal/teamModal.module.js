import angular from 'angular';
import teamModalTpl from './teamModal.html';

const TeamModalComponent = {
    templateUrl: teamModalTpl,
    controller: 'TeamModalController',
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    }
};

class TeamModalController {
    constructor() {
    }

    onAdd() {
        this.close({$value: {
            name: this.form.name.$modelValue
        }});
    }
}

const TeamModalModule = angular.module('components.settings.teamModal', []);

TeamModalModule.component('rfTeamModal', TeamModalComponent);
TeamModalModule.controller('TeamModalController', TeamModalController);

export default TeamModalModule;
