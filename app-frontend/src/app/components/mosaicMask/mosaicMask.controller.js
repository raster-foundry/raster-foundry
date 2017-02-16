export default class MosaicMaskController {
    constructor( // eslint-disable-line max-params
        $log, $scope, $q, projectService, layerService, $state, $stateParams,
        mapService, sceneService
    ) {
        'ngInject';
        this.projectService = projectService;
        this.$state = $state;
        this.$q = $q;
        this.$log = $log;
        this.$scope = $scope;
        this.$stateParams = $stateParams;
        this.mapService = mapService;
        this.getMap = () => mapService.getMap('project');
        this.sceneService = sceneService;
    }

    $onInit() {
        this.project = this.$state.params.project;
        this.projectid = this.$state.params.projectid;
        this.maskLayers = [];
        this.listeners = [];

        this.scene = this.$stateParams.scene;
        if (!this.scene) {
            // temporary
            this.$state.go('editor.project.mosaic.scenes');
            // fetch scene info
        }

        this.getMap().then(function (map) {
            map.holdState(
                {
                    center: map.map.getCenter(),
                    zoom: map.map.getZoom()
                }
            );
            let sceneBounds = this.sceneService.getSceneBounds(this.scene);
            map.map.fitBounds(sceneBounds, {
                padding: [75, 75],
                animate: true
            });
        }.bind(this));

        this.opacity = {
            model: 100,
            options: {
                floor: 0,
                ceil: 100,
                step: 1,
                showTicks: 10,
                showTicksValues: true
            }
        };

        this.addDrawControl();
    }

    $onDestroy() {
        this.getMap().then((map) => {
            let priorState = map.heldState;
            let center = priorState.center;
            let zoom = priorState.zoom;
            this.listeners.forEach((listener) => {
                map.off(listener);
            });
            map.map.setView(center, zoom);
        });
        this.removeDrawControl();
    }

    closePanel() {
        this.$state.go(
            'editor.project.mosaic.scenes',
            {
                projectid: this.projectid,
                project: this.project,
                sceneList: this.sceneList
            }
        );
    }

    addDrawControl() {
        this.getMap().then((map) => {
            let drawLayer = L.geoJSON(null, {
                style: () => {
                    return {
                        dashArray: '10, 10',
                        weight: 2,
                        fillOpacity: 0.2
                    };
                }
            });
            map.addLayer('draw', drawLayer);
            let drawCtlOptions = {
                position: 'topleft',
                draw: {
                    polyline: false,
                    marker: false,
                    circle: false,
                    rectangle: {
                        shapeOptions: {
                            dashArray: '10, 10',
                            weight: 2,
                            fillOpacity: 0.2
                        }
                    },
                    polygon: {
                        finish: false,
                        allowIntersection: false,
                        drawError: {
                            color: '#e1e100',
                            message: 'Invalid mask polygon'
                        },
                        shapeOptions: {
                            dashArray: '10, 10',
                            weight: 2,
                            fillOpacity: 0.2
                        }
                    }
                },
                edit: {
                    // featureGroup: this.drawLayer,
                    featureGroup: drawLayer,
                    remove: true
                }
            };
            this.drawControl = new L.Control.Draw(drawCtlOptions);
            map.map.addControl(this.drawControl);
            this.listeners = [
                map.on(L.Draw.Event.CREATED, this.addMask.bind(this)),
                map.on(L.Draw.Event.EDITED, this.editMask.bind(this)),
                map.on(L.Draw.Event.DELETED, this.deleteMask.bind(this))
            ];
        });
    }

    removeDrawControl() {
        this.getMap().then((map) => {
            this.drawControl.remove();
            map.deleteLayers('draw');
        });
    }

    addMask($event) {
        let layer = $event.layer;
        layer.properties = {
            area: this.calculateArea(layer),
            id: new Date()
        };
        this.getMap().then((map) => {
            let drawLayer = map.getLayers('draw')[0];
            drawLayer.addLayer(layer);
            this.maskLayers.push(layer);
            // this.makeHttpUpdateToProjectSceneMasks();
            this.$scope.$evalAsync();
        });
    }

    editMask($event) {
        // this.makeHttpUpdateToProjectSceneMasks();
        Object.values(
            $event.layers._layers  // eslint-disable-line no-underscore-dangle
        ).forEach((layer) => {
            layer.properties.area = this.calculateArea(layer);
        });
        this.$scope.$evalAsync();
    }

    deleteMask($event) {
        // this.makeHttpUpdateToProjectSceneMasks();
        this.maskLayers.splice(this.maskLayers.indexOf($event.layer), 1);
        this.$scope.$evalAsync();
    }

    calculateArea(layer) {
        return L.GeometryUtil.geodesicArea(layer.getLatLngs()[0]) / 1000000;
    }
}
