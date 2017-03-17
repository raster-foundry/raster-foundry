class ColorCorrectionController {
    constructor($scope, $state) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
        this.$state = $state;
    }

    updateBuffer(adj, val) {
        this.$parent.colorCorrectionBuffer[adj] = +val;
    }

    save() {
        this.$parent.saveColorCorrection();
    }

    cancel() {
        this.$state.go('imports.datasources.detail');
        this.$parent.cancel();
    }
}

export default ColorCorrectionController;
