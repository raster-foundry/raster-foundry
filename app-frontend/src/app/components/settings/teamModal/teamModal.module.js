import angular from 'angular';
import $ from 'jquery';
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
    constructor($rootScope, $element, $timeout) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $postLink() {
        this.claimFocus();
    }

    claimFocus(interval = 0) {
        this.$timeout(() => {
            const el = this.$element.find('input').get(0);
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
