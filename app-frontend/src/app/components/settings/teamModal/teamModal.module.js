import angular from 'angular';
import $ from 'jquery';
import _ from 'lodash';
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
    constructor($rootScope, $element, $timeout, authService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $postLink() {
        this.claimFocus();

        this.initOrganizationSelect();
    }

    initOrganizationSelect() {
        if (this.resolve.chooseOrg) {
            this.authService.fetchUserRoles().then(res => {
                const groupedUgrs = _.groupBy(res, 'groupType');
                this.platform = groupedUgrs.PLATFORM[0];
                this.organizations = groupedUgrs.ORGANIZATION;
                this.selectedOrganization = this.organizations[0];
            });
        }
    }

    claimFocus(interval = 0) {
        this.$timeout(() => {
            const el = this.$element.find('input').get(0);
            el.focus();
        }, interval);
    }

    disableCreate() {
        return !_.get(this, 'form.name.$modelValue.length');
    }

    onAdd() {
        let result = {
            name: this.form.name.$modelValue
        };

        if (this.resolve.chooseOrg) {
            result = Object.assign(result, {
                platform: this.platform,
                organization: this.selectedOrganization
            });
        }

        this.close({$value: result});
    }
}

const TeamModalModule = angular.module('components.settings.teamModal', []);

TeamModalModule.component('rfTeamModal', TeamModalComponent);
TeamModalModule.controller('TeamModalController', TeamModalController);

export default TeamModalModule;
