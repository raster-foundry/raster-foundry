/* global L */

export default class LabEditController {
    constructor($scope, $timeout, $element, $uibModal, mapService, projectService, layerService) {
        'ngInject';
        this.$scope = $scope;
        this.$timeout = $timeout;
        this.$element = $element;
        this.$uibModal = $uibModal;
        this.getMap = () => mapService.getMap('lab-run-preview');
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

    showPreview(data) {
        this.isShowingPreview = true;
        this.isComparing = false;
        this.exitText = 'Close Preview';
        this.previewData = data;

        if (data.constructor === Array) {
            // An array was passed, we assume a comparison
            this.isComparing = true;
            this.comparison = data;
            this.exitTest = 'Close Comparison';
            // @TODO: when endpoints are functioning, this will be handled accordingly
            // for now, we are just passing in empty layers to display the control
            this.sideBySideControl = L.control.sideBySide(L.featureGroup(), L.featureGroup());
        }

        this.getMap().then(m => {
            if (this.isComparing && !this.sideBySideAdded) {
                this.sideBySideControl.addTo(m.map);
                this.sideBySideAdded = true;
            } else if (!this.isComparing && this.sideBySideAdded){
                this.sideBySideControl.remove();
                this.sideBySideAdded = false;
            }
            this.$timeout(() => m.map.invalidateSize());
        });
    }

    closePreview() {
        this.isShowingPreview = false;
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
