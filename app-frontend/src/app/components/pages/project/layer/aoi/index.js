import tpl from './index.html';
import _ from 'lodash';

const mapName = 'project';
const mapLayerName = 'Project Layer';
const geomDrawType = 'Polygon';
const defaultColor = '#738FFC';

class ProjectLayerAoiController {
    constructor(
        $rootScope,
        $scope,
        $state,
        $log,
        $window,
        uuid4,
        moment,
        projectService,
        paginationService,
        mapService,
        modalService,
        shapesService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.selected = new Map([]);
        this.mapId = mapName;
        this.geomDrawType = geomDrawType;
        this.filterList = [
            {
                name: 'Project AOIs',
                id: this.uuid4.generate(),
                type: 'AOI'
            },
            {
                name: 'Vector Data',
                id: this.uuid4.generate(),
                type: 'VECTOR'
            }
        ];
        this.selectedGeom = null;
        this.setMapLayers();
        this.projectService
            .getProjectLayer(this.project.id, this.layer.id)
            .then(layer => {
                this.layer = layer;
                this.layerGeom = this.layer.geometry;
            })
            .catch(err => {
                this.$log.error(err);
            });
        this.dataType = this.filterList[0].type;
        this.fetchPage();
        this.displayAoiSource = this.filterList[0].name;
    }

    $onDestroy() {
        this.removeMapLayers();
    }

    getMap() {
        return this.mapService.getMap(mapName);
    }

    setMapLayers() {
        let mapLayer = this.projectService.mapLayerFromLayer(this.project, this.layer);
        return this.getMap().then(map => {
            map.setLayer(mapLayerName, mapLayer, true);
        });
    }

    removeMapLayers() {
        this.getMap().then(map => {
            map.deleteLayers(mapLayerName);
        });
    }

    fetchLayerAoiList(page) {
        this.itemList = [];
        let fetchedList = [];
        const currentQuery = this.projectService
            .getProjectLayers(this.project.id, { pageSize: 30, page: page - 1 })
            .then(paginatedResponse => {
                fetchedList = paginatedResponse.results;
                this.itemList = fetchedList.filter(fl => fl.geometry && fl.id !== this.layer.id);
                if (this.itemList.length) {
                    this.onSelect(this.layer.id);
                }
                this.pagination = this.paginationService.buildPagination(paginatedResponse);
                this.paginationService.updatePageParam(page);
                if (this.currentQuery === currentQuery) {
                    delete this.fetchError;
                }
            })
            .catch(e => {
                if (this.currentQuery === currentQuery) {
                    this.fetchError = e;
                }
            })
            .finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    fetchVectorDataList(page) {
        this.itemList = [];
        let fetchedList = [];

        const currentQuery = this.shapesService
            .query({ pageSize: 30, page: page - 1 })
            .then(paginatedResponse => {
                fetchedList = paginatedResponse.features;
                this.itemList = fetchedList.map(fl => {
                    return {
                        id: fl.id,
                        name: fl.properties.name,
                        date: fl.properties.createdAt,
                        colorGroupHex: defaultColor,
                        geometry: fl.geometry
                    };
                });
                this.pagination = this.paginationService.buildPagination(paginatedResponse);
                this.paginationService.updatePageParam(page);
                if (this.currentQuery === currentQuery) {
                    delete this.fetchError;
                }
            })
            .catch(e => {
                if (this.currentQuery === currentQuery) {
                    this.fetchError = e;
                }
            })
            .finally(() => {
                if (this.currentQuery === currentQuery) {
                    delete this.currentQuery;
                }
            });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    fetchPage(page = 1) {
        if (this.dataType.toUpperCase() === 'AOI') {
            this.fetchLayerAoiList(page);
        }

        if (this.dataType.toUpperCase() === 'VECTOR') {
            this.fetchVectorDataList(page);
        }
    }

    onConfirmAoi(aoiGeojson, isSaveShape) {
        const geom = this.getMultipolygonGeom(aoiGeojson);
        if (!geom) {
            this.$window.alert('The supplied AOI geometry is incorrect. Please try again.');
            return;
        }
        const layerGeomToUpdate = this.shapesService.generateFeature(geom);
        const updatedLayer = Object.assign({}, this.layer, { geometry: layerGeomToUpdate });
        this.projectService
            .updateProjectLayer(updatedLayer)
            .then(newLayer => {
                this.layer = newLayer;
                this.layerGeom = this.layer.geometry;
                if (isSaveShape) {
                    this.openShapeModal(this.layer);
                } else {
                    this.$state.go('project.layer');
                }
            })
            .catch(err => {
                this.$log.error(err);
                this.$window.alert(
                    "There was an error updating this layer's AOI." + ' Please try again later'
                );
            });
    }

    getMultipolygonGeom(aoiGeojson) {
        const geomType = _.get(aoiGeojson, 'geometry.type');
        if (geomType && geomType.toUpperCase() === 'POLYGON') {
            return {
                type: 'MultiPolygon',
                coordinates: [aoiGeojson.geometry.coordinates]
            };
        } else if (geomType && geomType.toUpperCase() === 'MULTIPOLYGON') {
            return aoiGeojson.geometry;
        }
        return 0;
    }

    formatDateDisplay(date) {
        return date.length ? this.moment.utc(date).format('LLL') + ' (UTC)' : 'MM/DD/YYYY';
    }

    openShapeModal(layer) {
        const modal = this.modalService.open({
            component: 'rfEnterTokenModal',
            resolve: {
                title: () => 'Enter a name for the vector data',
                token: () =>
                    `${this.project.name} - ${layer.name} AOI ` +
                    `- ${this.formatDateDisplay(layer.createdAt)}`
            }
        });

        modal.result
            .then(name => {
                const geom = this.getMultipolygonGeom(layer.geometry);

                if (!geom) {
                    this.$window('The supplied geometry is incorrect, please try again.');
                    return;
                }

                this.shapesService
                    .createShape(this.shapesService.generateFeatureCollection([geom], name))
                    .then(() => {
                        this.$state.go('project.layer');
                    })
                    .catch(err => {
                        this.$window.alert(
                            "There was an error adding this layer's AOI as vector data." +
                                ' Please try again later'
                        );
                        this.$log.error(err);
                    });
            })
            .catch(() => {});
    }

    onCancelDrawAoi() {
        this.$state.go('project.layer');
    }

    onChangeFilterList(filterId) {
        this.dataType = this.filterList.find(fl => fl.id === filterId).type;
        this.fetchPage();
        this.displayAoiSource = this.filterList.find(fl => fl.id === filterId).name;
    }

    onSelect(id) {
        if (!this.isSelected(id)) {
            const item = this.itemList.find(itm => itm.id === id);
            if (item) {
                this.selected.clear();
                this.selected.set(id, item);
                this.layerGeom = item.geometry;
                this.$scope.$evalAsync();
            }
        } else {
            this.layerGeom = null;
            this.selected.clear();
        }
    }

    isSelected(id) {
        return this.selected.has(id);
    }

    onShapeOp(isInProgress) {
        this.disableCheckbox = isInProgress;
        if (!isInProgress) {
            this.selected.clear();
        }
    }
}

const component = {
    bindings: {
        project: '<',
        layer: '<'
    },
    templateUrl: tpl,
    controller: ProjectLayerAoiController.name
};

export default angular
    .module('components.pages.project.layer.aoi', [])
    .controller(ProjectLayerAoiController.name, ProjectLayerAoiController)
    .component('rfProjectLayerAoiPage', component).name;
