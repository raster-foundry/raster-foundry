class ColorCompositesController {
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
    }
}

const ColorCompositesModule =
  angular.module('pages.imports.datasources.details.colorComposites', []);

ColorCompositesModule.controller('ColorCompositesController', ColorCompositesController);

export default ColorCompositesModule;
