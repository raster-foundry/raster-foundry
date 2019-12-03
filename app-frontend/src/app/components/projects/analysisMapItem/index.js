import { get } from 'lodash';

import tpl from './index.html';
import projectPlaceholder from '../../../../assets/images/transparent.svg';

const analysisLayerName = 'Analysis Viz';
const offset = -2;

class AnalysisMapItemController {
    constructor($rootScope, $state, $scope, $log, $timeout, analysisService, mapService) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.analysisLayerName = analysisLayerName;
        this.mapOptions = { attributionControl: false };
        this.mapId = `analysis-viz-${this.analysisId}`;
        this.offset = offset;
        this.setAnalysisAndTiles();
    }

    getMap() {
        return this.mapService.getMap(this.mapId);
    }

    setAnalysisAndTiles() {
        this.getMap().then(map => {
            this.analysisTile.then(analysisTile => {
                const { analysis, mapTile } = analysisTile;
                this.analysis = analysis;
                if (get(this, 'analysis.executionParameters.mask.type')) {
                    this.updateMapView(this.analysis.executionParameters.mask, map);
                }
                map.setLayer(this.analysisLayerName, mapTile);
            });
        });
    }

    transformWmCoords(coord) {
        return this.analysisService.transformWmPointToLatLngArray(L.point(coord[0], coord[1]));
    }

    updateMapView(multipolygon, mapWrapper) {
        // we know that the backend returns only multipolygon
        // and the coordinates are web mercator
        mapWrapper.map.invalidateSize();
        const updatedMultipolygon = Object.assign({}, multipolygon, {
            coordinates: [[multipolygon.coordinates[0][0].map(this.transformWmCoords.bind(this))]]
        });
        mapWrapper.map.fitBounds(L.geoJson(updatedMultipolygon).getBounds(), {
            padding: [this.offset, this.offset],
            animate: false
        });
    }
}

const component = {
    bindings: {
        analysisId: '<',
        analysisTile: '<'
    },
    transclude: {
        selector: '?itemSelector'
    },
    templateUrl: tpl,
    controller: AnalysisMapItemController.name
};

export default angular
    .module('components.projects.analysisMapItem', [])
    .controller(AnalysisMapItemController.name, AnalysisMapItemController)
    .component('rfAnalysisMapItem', component).name;
