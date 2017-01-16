export default class LabEditController {
    constructor($scope, $element, $uibModal) {
        'ngInject';
        this.$scope = $scope;
        this.$element = $element;
        this.$uibModal = $uibModal;
    }

    $onInit() {
        this.inputs = [false, false];
        this.inputParameters = [{
            bands: {
                nir: '5',
                red: '4'
            }
        }, {
            bands: {
                nir: '5',
                red: '4'
            }
        }];
    }

    selectProjectModal(src) {
        if (this.activeModal) {
            this.activeModal.dismiss();
        }

        this.activeModal = this.$uibModal.open({
            component: 'rfSelectProjectModal',
            resolve: {
                project: () => this.inputs[src],
                content: () => ({
                    title: 'Select a project'
                })
            }
        });

        this.activeModal.result.then(p => {
            this.inputs[src] = p;
            this.$scope.$evalAsync();
        });
    }
}
