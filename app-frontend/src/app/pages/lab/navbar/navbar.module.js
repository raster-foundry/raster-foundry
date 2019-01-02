import LabActions from '_redux/actions/lab-actions';

class LabNavbarController {
    constructor($rootScope, analysisService, $state, $ngRedux, $scope) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        let unsubscribe = this.$ngRedux.connect(this.mapStateToThis, LabActions)(this);
        this.$scope.$on('$destroy', unsubscribe);
    }

    mapStateToThis(state) {
        return {
            analysis: state.lab.analysis,
            updating: state.lab.updating
        };
    }

    toggleEdit() {
        this.nameBuffer = this.analysis.name;
        this.editing = !this.editing;
    }

    finishEdit() {
        this.updateAnalysisName(this.nameBuffer);
        this.toggleEdit();
    }
}

const LabNavbarModule = angular.module('pages.lab.navbar', []);

LabNavbarModule.controller('LabNavbarController', LabNavbarController);

export default LabNavbarModule;
