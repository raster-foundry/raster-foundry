import { OrderedMap } from 'immutable';
import _ from 'lodash';
import L from 'leaflet';
import tpl from './index.html';

class AnalysisEditModalController {
    constructor(
        $rootScope, $timeout, $log, $q,
        analysisService, projectService, mapService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.mapOptions = {attributionControl: false};
        this.editableNodes = new OrderedMap();
        this.analyses = this.resolve.analyses;
        if (!this.analyses.length) {
            this.error = 'No analyses have been selected';
            this.$log.error(this.error);
        }
        try {
            this.editableNodes = this.editableNodesFromAnalyses(this.analyses);
            const geojson = _.get(this.editableNodes.find(n => n.type === 'mask'), 'node.mask');
            if (geojson) {
                const reprojected = this.geometryFromMaskGeometry(geojson);
                this.getMap().then((mapContainer) => {
                    mapContainer.setGeojson('aoi', reprojected);
                    this.$timeout(() => {
                        mapContainer.map.fitBounds(L.geoJSON(reprojected).getBounds(), {
                            padding: [15, 15],
                            animate: false
                        });
                    }, 250);
                });
            }
        } catch (e) {
            this.error = e;
            this.$log.error(this.error);
        }
    }

    geometryFromMaskGeometry(geom) {
        if (geom.type.toLowerCase() !== 'multipolygon') {
            throw new Error('Tried to reproject a shape that isn\'t a multipolygon');
        }

        const polygons = geom.coordinates[0];
        const reprojected = Object.assign({}, geom, {
            coordinates: [
                polygons.map(
                    polygon =>
                        polygon
                            .map(([lng, lat]) => L.CRS.EPSG3857.unproject({x: lng, y: lat}))
                            .map(({lng, lat}) => [lng, lat])
                )
            ]
        });
        return reprojected;
    }

    getMap() {
        return this.mapService.getMap('modal');
    }

    editableNodesFromAnalyses(analyses) {
        if (_.uniq(analyses.map(a => a.templateId)).length > 1) {
            throw new Error('Analyses were not all created from the same template');
        }
        const analysis = analyses[0];
        const root = analysis.executionParameters;
        let editableNodes = new OrderedMap();
        let nodes = [root];
        const addEditableNode = node =>
            ({
                mask: () => editableNodes.set(node.id, {
                    node, type: 'mask', value: node.mask
                }),
                layerSrc: () => {
                    const editableNode = {
                        node, type: 'layerSrc', value: '' + node.band,
                        options: [{name: node.band, number: node.band}]
                    };
                    this.projectService.getProjectLayerDatasources(
                        analysis.projectId, analysis.projectLayerId
                    ).then((datasources) => {
                        if (datasources.length === 0) {
                            this.$log.error('No datasources in layer, disabling changes');
                        } else {
                            if (datasources.length !== 1) {
                                this.$log.error(
                                    'layer has more than one datasource',
                                    'falling back to the first one');
                            }
                            const datasource = _.first(datasources);
                            editableNode.options = datasource.bands;
                            if (!datasource.bands.map(d => d.number).includes(editableNode.value)) {
                                editableNode.value = null;
                            }
                        }
                    });
                    return editableNodes.set(node.id, editableNode);
                },
                const: () => editableNodes.set(node.id, {
                    node, type: 'const', value: +node.constant
                })
            }[node.type || node.apply] || (() => editableNodes))();
        while (nodes.length) {
            const node = nodes.pop();
            editableNodes = addEditableNode(node);
            if (node.args) {
                nodes.push(...node.args);
            }
        }
        return editableNodes;
    }

    saveChanges() {
        this.$q.all(
            this.analyses
                .map((analysis) => this.replaceNodesInAnalysis(this.editableNodes, analysis))
                .map((analysis) => this.analysisService.updateAnalysis(analysis))
        ).then((updatedAnalyses) => {
            this.close({$value: updatedAnalyses});
        }).catch(e => {
            this.error = e;
            this.$log.error(this.error);
        });
    }

    replaceNodesInAnalysis(nodeMap, analysis) {
        let root = analysis.executionParameters;
        let nodes = root.args ? [...root.args] : [];
        while (nodes.length) {
            const node = nodes.pop();
            if (node.args) {
                nodes.push(...node.args);
            }
            if (nodeMap.has(node.id)) {
                (n => ({
                    mask: () => {
                        node.mask = nodeMap.get(node.id).value;
                    },
                    layerSrc: () => {
                        node.band = +nodeMap.get(node.id).value;
                    },
                    const: () => {
                        node.constant = '' + nodeMap.get(node.id).value;
                    }
                }[n.type || n.apply] || (() => {})))(node)();
            }
        }
        return analysis;
    }
}

const component = {
    bindings: {
        close: '&',
        dismiss: '&',
        modalInstance: '<',
        resolve: '<'
    },
    controller: AnalysisEditModalController.name,
    templateUrl: tpl
};

export default angular
    .module('components.projects.analysisEditModal', [])
    .controller(AnalysisEditModalController.name, AnalysisEditModalController)
    .component('rfAnalysisEditModal', component)
    .name;
