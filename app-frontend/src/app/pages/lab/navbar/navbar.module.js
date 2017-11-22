import * as LabActions from '_redux/actions/lab-actions';

class LabNavbarController {
    constructor(analysisService, $state, $ngRedux, $scope) {
        'ngInject';

        this.analysisService = analysisService;
        this.$state = $state;
        this.$ngRedux = $ngRedux;

        let unsubscribe = $ngRedux.connect(this.mapStateToThis, LabActions)(this);
        $scope.$on('$destroy', unsubscribe);
    }

    $onInit() {
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
