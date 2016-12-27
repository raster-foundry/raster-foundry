export default class MosaicScenesParametersController {
    constructor( // eslint-disable-line max-params
        $state, $scope, mapService
    ) {
        'ngInject';
        this.$scope = $scope;
        this.$state = $state;
        this.getMap = () => mapService.getMap('project');
    }

    $onInit() {
        this.mosaicType = 'Manual';
        this.mosaicOptions = [
            {name: 'Cloudiness Level'},
            {name: 'Acquisition Date'},
            {name: 'Upload Date'}
        ];

        this.extentBounds = {};
        this.extentType = 'sceneExtent';
    }

    $onDestroy() {
        this.getMap().then((map) => {
            this.listeners.forEach((listener) => {
                map.off(listener);
            });
        });
        this.removeDrawControl();
    }

    /** Gets bounds of all scenes combined
     *
     * @returns {L.LatLngBounds} Bounds for combined scenes
     */
    getSceneBounds() {
        this.getMap().then((map) => {
            let layerBounds = map.getLayers('scenes').map((layer) => layer.options.bounds);
            return layerBounds.reduce((originalBnds, newBnds) => originalBnds.extend(newBnds));
        });
    }

    /** Adds draw control to map
     *
     * @returns {undefined}
     */
    addDrawControl() {
        this.getMap().then((map) => {
            let extentLayer = L.geoJSON(null, {
                style: () => {
                    return {
                        dashArray: '10, 10',
                        weight: 2,
                        fillOpacity: 0.2
                    };
                }
            });

            map.addLayer('extent', extentLayer);

            let drawCtlOptions = {
                position: 'topleft',
                draw: {
                    polyline: false,
                    marker: false,
                    circle: false,
                    polygon: false,
                    rectangle: {
                        shapeOptions: {
                            dashArray: '10, 10',
                            weight: 2,
                            fillOpacity: 0.2
                        }
                    }
                },
                edit: {
                    featureGroup: extentLayer,
                    remove: true
                }
            };
            this.drawControl = new L.Control.Draw(drawCtlOptions);
            map.map.addControl(this.drawControl);
            this.listeners = [
                map.on(L.Draw.Event.CREATED, this.addExtent.bind(this)),
                map.on(L.Draw.Event.EDITED, this.editExtent.bind(this)),
                map.on(L.Draw.Event.DELETED, this.deleteExtent.bind(this))
            ];
        });
    }

    /** Called when scene extent is chosen, reset to scene bounds
     *
     * @returns {undefined}
     */
    removeDrawControl() {
        this.getMap().then((map) => {
            this.drawControl.remove();
            map.deleteLayers('extent');
        });
        this.extentLayer = this.getSceneBounds();
    }

    /** Set extent bounds from drawn layer, add to map
     *
     * @param {object} $event Map draw event
     * @returns {undefined}
     */
    addExtent($event) {
        let layer = $event.layer;
        layer.properties = {
            id: new Date()
        };
        this.getMap().then((map) => {
            let extentLayer = map.getLayers('extent')[0];
            extentLayer.addLayer(layer);
            this.extentBounds = extentLayer.options.bounds;
            this.$scope.$evalAsync();
        });
    }

    /** Reset extent to scene bounds if custom extent is deleted
     *
     * @returns {undefined}
     */
    deleteExtent() {
        this.extentBounds = this.getSceneBounds();
        this.$scope.$evalAsync();
    }

    /** Called when extent is edited, will update definition via API
     *
     * @returns {undefined}
     */
    editExtent() {
        // TODO: hook up to API when created
    }

    /** Sets mosaic type when selected from drop down
     *
     * @param {string} mosaicType Selected mosaic option (auto/manual)
     * @returns {undefined}
     */
    selectMosaicType(mosaicType) {
        this.mosaicType = mosaicType;
    }
}
