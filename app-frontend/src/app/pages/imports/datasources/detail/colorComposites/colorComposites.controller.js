class ColorCompositesController {
    constructor($scope, $state) {
        'ngInject';
        this.$parent = $scope.$parent.$ctrl;
        this.$state = $state;
    }

    updateBuffer(preset, key, val) {
        this.$parent.colorCompositesBuffer[preset].value[key] = +val;
    }

    deleteFromBuffer(preset) {
        delete this.$parent.colorCompositesBuffer[preset];
    }

    save() {
        this.$parent.saveColorComposites();
    }

    cancel() {
        this.$state.go('imports.datasources.detail');
        this.$parent.cancel();
    }
}

export default ColorCompositesController;
