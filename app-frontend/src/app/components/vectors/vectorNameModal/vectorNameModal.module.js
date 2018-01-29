/* global */
import angular from 'angular';
import vectorNameModalTpl from './vectorNameModal.html';

const VectorNameModalComponent = {
    templateUrl: vectorNameModalTpl,
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: 'VectorNameModalController'
};

class VectorNameModalController {
    constructor(shapesService, authService) {
        this.shapesService = shapesService;
        this.authService = authService;
    }

    handleCancel() {
        this.dismiss();
    }

    handleSave() {
        let shape = this.resolve.shape;

        const userRequest = this.authService.getCurrentUser();

        return userRequest.then(
            (user) => {
                let organizationId = user.organizationId;
                shape.features = shape.features.map(f => {
                    f.properties.organizationId = organizationId;
                    f.properties.name = this.shapeName;
                    return f;
                });
                return this.shapesService.createShape(shape)
                    .then((shapes) => {
                        this.close({$value: shapes.map(s => s.toJSON())});
                    }, () => {
                        this.error = true;
                    });
            },
            () => {
                this.error = true;
            }
        );
    }
}

const VectorNameModalModule = angular.module('components.vectors.vectorNameModal', []);

VectorNameModalModule.controller('VectorNameModalController', VectorNameModalController);
VectorNameModalModule.component('rfVectorNameModal', VectorNameModalComponent);

export default VectorNameModalModule;
