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
    constructor($element, $timeout) {
        'ngInject';
        this.$element = $element;
        this.$timeout = $timeout;
    }

    $postLink() {
        this.claimFocus();
    }

    claimFocus(interval = 0) {
        this.$timeout(() => {
            const el = $(this.$element[0]).find('input').get(0);
            el.focus();
        }, interval);
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
