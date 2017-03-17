class ColorCorrectionController {
    constructor($scope, $state) {
        'ngInject';
        this.$scope = $scope;
        this.$parent = $scope.$parent.$ctrl;
        this.$state = $state;
    }

    $onInit() {
        this.initListeners();
    }

    initListeners() {
        this.$scope.$on('$locationChangeStart', () => {
            this.$parent.cancel();
        });
    }

    updateBuffer(adj, val) {
        this.$parent.colorCorrectionBuffer[adj] = +val;
    }

    save() {
        this.$parent.saveColorCorrection();
    }

    cancel() {
        this.$state.go('imports.datasources.detail');
    }
}

export default ColorCorrectionController;
