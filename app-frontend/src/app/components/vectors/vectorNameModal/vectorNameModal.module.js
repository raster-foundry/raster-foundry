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
        const userRequest = this.authService.getCurrentUser();

        return userRequest.then(
            (user) => {
                let info = {
                    props: {
                        name: this.shapeName,
                        organizationId: user.organizationId
                    },
                    id: this.resolve.shape.features[0].id
                };
                let shape = this.toMultiPolygon(this.resolve.shape, info);
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

    toMultiPolygon(shape, info) {
        return {
            type: 'FeatureCollection',
            features: [
                {
                    type: 'Feature',
                    properties: info.props,
                    geometry: {
                        type: 'MultiPolygon',
                        coordinates: shape.features.map(f => f.geometry.coordinates)
                    },
                    id: info.id
                }
            ]
        };
    }
}

const VectorNameModalModule = angular.module('components.vectors.vectorNameModal', []);

VectorNameModalModule.controller('VectorNameModalController', VectorNameModalController);
VectorNameModalModule.component('rfVectorNameModal', VectorNameModalComponent);

export default VectorNameModalModule;
