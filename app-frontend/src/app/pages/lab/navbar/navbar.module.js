import * as WorkspaceActions from '_redux/actions/workspace-actions';

class LabNavbarController {
    constructor($state, $ngRedux, $scope) {
        'ngInject';

        this.$state = $state;
        this.$ngRedux = $ngRedux;

        let unsubscribe = $ngRedux.connect(this.mapStateToThis, WorkspaceActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
    }

    mapStateToThis(state) {
        return {
            workspace: state.lab.workspace,
            updating: state.lab.updating
        };
    }

    toggleEdit() {
        this.nameBuffer = this.workspace.name;
        this.editing = !this.editing;
    }

    finishEdit() {
        this.updateWorkspaceName(this.nameBuffer);
        this.toggleEdit();
    }
}

const LabNavbarModule = angular.module('pages.lab.navbar', []);

LabNavbarModule.controller('LabNavbarController', LabNavbarController);

export default LabNavbarModule;
