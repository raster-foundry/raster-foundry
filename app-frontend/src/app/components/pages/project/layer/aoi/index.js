import tpl from './index.html';
import _ from 'lodash';

const mapName = 'project';
const mapLayerName = 'Project Layer';
const geomDrawType = 'Polygon';
const defaultColor = '#738FFC';

class ProjectLayerAoiController {
    constructor(
        $rootScope, $scope, $state, $log, $window,
        uuid4, moment, projectService, paginationService, mapService,
        modalService, shapesService
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
        this.fetchPage(1, this.filterList[0].id);
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

    fetchPage(page = 1, typeId) {
        this.itemList = [];
        let fetchedList = [];
        this.dataType = this.filterList.find(fl => fl.id === typeId).type;
        const query = this.getQueryByType(page);

        const currentQuery = query.then((paginatedResponse) => {
            if (this.dataType === 'AOI') {
                fetchedList = paginatedResponse.results;
            } else {
                fetchedList = paginatedResponse.features;
            }

            this.itemList = _.compact(fetchedList.map(fl => {
                if (this.dataType === 'AOI' && !fl.geometry) {
                    return 0;
                }
                return this.createItemInfo(fl);
            }));

            if (this.dataType === 'AOI') {
                this.onSelect(this.layer.id);
            } else {
                this.onSelect(this.itemList[0].id);
            }

            this.pagination = this.paginationService.buildPagination(paginatedResponse);
            this.paginationService.updatePageParam(page);
            if (this.currentQuery === currentQuery) {
                delete this.fetchError;
            }
        }, (e) => {
            if (this.currentQuery === currentQuery) {
                this.fetchError = e;
            }
        }).finally(() => {
            if (this.currentQuery === currentQuery) {
                delete this.currentQuery;
            }
        });
        this.currentQuery = currentQuery;
        return currentQuery;
    }

    getQueryByType(page) {
        return this.dataType === 'AOI' ?
            this.projectService.getProjectLayers(
                this.project.id,
                {
                    pageSize: 30,
                    page: page - 1
                }
            ) :
            this.shapesService.query(
                {
                    pageSize: 30,
                    page: page - 1
                }
            );
    }

    onConfirmAoi(aoi, isSaveShape) {
        // save layer aoi here
        const updatedLayer = Object.assign({}, this.layer, {geometry: aoi});
        this.projectService
            .updateProjectLayer(updatedLayer)
            .then(newLayer => {
                this.layer = newLayer;
                this.layerGeom = this.layer.geometry;
                if (!isSaveShape) {
                    this.$state.go('project.layer');
                } else {
                    this.openShapeModal(this.layer);
                }
            })
            .catch(err => {
                this.$log.error(err);
                this.$window.alert('There is an error updating this layer\'s AOI.'
                    + ' Please try again later');
            });
    }

    formatDateDisplay(date) {
        return date.length ? this.moment.utc(date).format('LL') + ' (UTC)' : 'MM/DD/YYYY';
    }

    openShapeModal(layer) {
        const modal = this.modalService.open({
            component: 'rfEnterTokenModal',
            resolve: {
                title: () => 'Enter a name for the vector data',
                token: () => `${layer.name} AOI `
                    + `- ${this.formatDateDisplay(layer.createdAt)}`
            }
        });

        modal.result.then((name) => {
            this.shapesService
                .createShape(this.generateShapeCreate(name, layer.geometry))
                .then(() => {
                    this.$state.go('project.layer');
                })
                .catch(err => {
                    this.$window.alert('There is an error adding this layer\'s AOI as vector data.'
                        + ' Please try again later');
                    this.$log.error(err);
                });
        }).catch(() => {});
    }

    generateShapeCreate(name, geometry) {
        return {
            type: 'FeatureCollection',
            features: [
                {
                    type: 'Feature',
                    properties: { name },
                    geometry,
                    id: this.uuid4.generate()
                }
            ]
        };
    }

    onCancelDrawAoi() {
        this.$state.go('project.layer');
    }

    onChangeFilterList(filterId) {
        this.fetchPage(1, filterId);
        this.displayAoiSource = this.filterList.find(fl => fl.id === filterId).name;
    }

    createItemInfo(fl) {
        if (this.dataType === 'AOI') {
            return {
                id: fl.id,
                name: fl.name,
                subtext: '',
                date: fl.createdAt,
                colorGroupHex: fl.colorGroupHex,
                geometry: fl.geometry
            };
        }

        if (this.dataType === 'VECTOR') {
            return {
                id: fl.id,
                name: fl.properties.name,
                subtext: '',
                date: fl.properties.createdAt,
                colorGroupHex: defaultColor,
                geometry: fl.geometry
            };
        }

        return 0;
    }

    onSelect(id) {
        this.selected.clear();
        if (!this.selected.has(id)) {
            const item = this.itemList.find(itm => itm.id === id);
            this.selected.set(id, item);
            if (this.dataType === 'AOI') {
                this.layerGeom = item.geometry;
            } else {
                this.layerGeom = item.geometry;
            }
        }
    }


    isSelected(id) {
        return this.selected.has(id);
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
    .component('rfProjectLayerAoiPage', component)
    .name;
