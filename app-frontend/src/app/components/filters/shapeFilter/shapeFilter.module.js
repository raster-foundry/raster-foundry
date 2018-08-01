/* globals document */
import angular from 'angular';
import _ from 'lodash';
import shapeFilterTpl from './shapeFilter.html';
import ShapeActions from '_redux/actions/shape-actions';

const ShapeFilterComponent = {
    templateUrl: shapeFilterTpl,
    controller: 'ShapeFilterController',
    bindings: {
        filter: '<',
        onFilterChange: '&',
        shape: '<?'
    }
};

class ShapeFilterController {
    constructor(
        $scope, $location, $q, $ngRedux,
        shapesService, modalService, mapService
    ) {
        this.$scope = $scope;
        this.$location = $location;
        this.$q = $q;
        this.shapesService = shapesService;
        this.modalService = modalService;
        this.getMap = () => mapService.getMap('edit');

        this.selectedShape = null;
        this.shapeSearch = '';
        this.open = false;

        let unsubscribe = $ngRedux.connect(
            this.mapStateToThis.bind(this),
            ShapeActions
        )(this);
        $scope.$on('$destroy', () => {
            this.getMap().then(m => {
                m.deleteGeojson('AOI');
            });
            this.cancelDrawing();
            unsubscribe();
        });
    }

    mapStateToThis(state) {
        return {
            drawState: state.shape
        };
    }

    $onInit() {
        this.shapeRequest = this.getShapes().then((shapes) => {
            this.shapes = shapes;
            this.filteredShapes = shapes;
            this.shapeSearch = '';
            if (this.shape) {
                this.setShapeFromShape(this.shape);
            } else if (this.paramValue) {
                this.setShapeFromParam(this.paramValue);
            }
        });
        this.shapeRequest.catch((err) => {
            this.error = err;
        });
    }

    $onChanges(changes) {
        if (changes.filter && changes.filter.currentValue) {
            this.filter = changes.filter.currentValue;
            if (this.filter.param) {
                this.paramValue = this.$location.search()[this.filter.param];
                this.setShapeFromParam(this.paramValue);
            }
        }
        let shape = _.get(changes, 'shape.currentValue');
        if (shape && shape.id !== _.get(this, 'selectedShape.id')) {
            this.setShapeFromShape(shape);
        }
    }

    setShapeFromParam(param) {
        if (this.shapes && this.shapes.length) {
            const paramShape = _.first(this.shapes.filter((shape) => shape.id === this.paramValue));
            if (paramShape) {
                this.onSelectShape(paramShape);
            }
        }
    }

    setShapeFromShape(bindShape) {
        if (this.shapes && this.shapes.length) {
            const boundShape = _.first(this.shapes.filter((shape) => shape.id === bindShape.id));
            if (boundShape) {
                this.onSelectShape(boundShape);
            }
        }
    }

    onSearchChange() {
        if (this.shapeSearch.length) {
            this.filteredShapes = _.filter(this.shapes, (shape) => {
                return shape.properties.name.toLowerCase()
                    .includes(this.shapeSearch.toLowerCase());
            });
        } else {
            this.filteredShapes = this.shapes;
        }
    }

    getShapes() {
        const pageSize = 30;
        return this.shapesService.fetchShapes({pageSize}).then((response) => {
            let shapes = response.features;
            let promises = [];
            let pages = Math.ceil(response.count / pageSize);
            for (
                let i = 1;
                i < pages;
                i = i + 1
            ) {
                promises.push(
                    this.shapesService.fetchShapes({page: i, pageSize})
                );
            }
            return this.$q.all(promises).then((responses) => {
                shapes = responses.map((geojson) => geojson.features)
                    .reduce((acc, features) => acc.concat(features), shapes);
                shapes = _.sortBy(shapes, [(s) => s.properties.name]);
                return shapes;
            });
        });
    }

    onSelectShape(shape, event) {
        if (event) {
            event.stopPropagation();
        }
        this.selectedShape = shape;

        const filterParams = {};
        filterParams[this.filter.param] = shape;
        this.onFilterChange({filter: this.filter, filterParams});
        if (this.selectedShape) {
            this.getMap().then((m) => {
                m.setGeojson('AOI', this.selectedShape);
            });
        }
        if (this.open) {
            this.toggleDropdown();
        }
    }

    onClearShapeClick(event) {
        event.stopPropagation();
        this.clearShape();
    }

    clearShape() {
        this.selectedShape = null;
        this.getMap().then(m => {
            m.deleteGeojson('AOI');
        });

        const filterParams = {};
        filterParams[this.filter.param] = null;
        this.onFilterChange({filter: this.filter, filterParams});
    }

    onClearSearch(event) {
        event.stopPropagation();
        this.shapeSearch = '';
        this.onSearchChange();
    }

    toggleDropdown(event) {
        if (event) {
            event.stopPropagation();
        }
        this.open = !this.open;
        if (this.open) {
            this.clickListener = () => {
                this.toggleDropdown();
                this.$scope.$evalAsync();
            };
            $(document).click(this.clickListener);
        } else {
            $(document).unbind('click', this.clickListener);
        }
    }

    shapeImportModal() {
        let modal = this.modalService.open({
            component: 'rfVectorImportModal',
            resolve: {}
        });
        modal.result.then(() => {
            this.getShapes();
        });
    }

    startCreateShape() {
        this.$q((resolve, reject) => {
            this.getMap().then(m => {
                m.deleteGeojson('AOI');
            });
            this.startDrawing(resolve, reject, 'edit');
        }).then((geojson) => {
            let modal = this.modalService.open({
                component: 'rfVectorNameModal',
                resolve: {
                    shape: geojson
                }
            });
            return modal.result.then((shapes) => {
                this.shapes = this.shapes.concat(shapes);
                this.onSearchChange();
                let shape = _.first(shapes);
                this.onSelectShape(shape);
            });
        }, () => {
            this.getMap().then(m => {
                m.setGeojson('AOI', this.selectedShape);
            });
        });
    }
}

const ShapeFilterModule = angular.module('components.filters.shapeSearch', []);

ShapeFilterModule.component('rfShapeFilter', ShapeFilterComponent);
ShapeFilterModule.controller('ShapeFilterController', ShapeFilterController);

export default ShapeFilterModule;
