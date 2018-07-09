import {Map, Set} from 'immutable';

/* eslint no-unused-vars: 0 */
/* eslint spaced-comment: 0 */
/* globals BUILDCONFIG _ console setTimeout*/

class MapWrapper {
    // MapWrapper is a bare es6 class, so does not use angular injections
    constructor( // eslint-disable-next-line
        $q, leafletMap, mapId, imageOverlayService, datasourceService, options, thumbnailService
    ) {
        this.$q = $q;
        this.thumbnailService = thumbnailService;
        this.map = leafletMap;
        this.mapId = mapId;

        this.imageOverlayService = imageOverlayService;
        this.datasourceService = datasourceService;
        this.options = options;
        // counter is not global - each map has its own.
        // So don't combine lists of listeners across maps
        this._callbackCounter = 0;
        this._callbacks = new Map();
        // internal id -> geojson layer map
        // TODO: Make geojson functionality an extension on the normal geojson layer
        // that way we can treat them the same as any other.
        this._geoJsonMap = new Map();
        this._geoJsonLayerGroup = L.geoJSON().addTo(this.map);
        this._layerMap = new Map();
        this._hiddenLayerMap = new Map();
        this._layerGroup = L.featureGroup().addTo(this.map);
        this._toggleableLayers = new Set();
        this.persistedThumbnails = new Map();
        this.disableFootprints = false;

        this.map.createPane('basemap').style.zIndex = 199;
        this.map.createPane('editable').style.zIndex = 500;
        this.baseMaps = {};

        let basemapLayers = BUILDCONFIG.BASEMAPS.layers;
        Object.keys(basemapLayers).forEach((basemapName) => {
            let basemaps = basemapLayers[basemapName];
            let basemapOptions = Object.assign(
                {pane: 'basemap'},
                basemaps.properties
            );
            this.baseMaps[basemapName] = L.tileLayer(
                basemaps.url,
                basemapOptions
            );
        });
        let defaultBasemap = BUILDCONFIG.BASEMAPS.default ?
            BUILDCONFIG.BASEMAPS.default :
            this.baseMaps[Object.keys(this.baseMaps)[0]];
        this.setBasemap(defaultBasemap);

        this.changeOptions(this.options);
    }

    toJSON() {
        return {
            mapId: this.mapId
        };
    }

    changeOptions(options) {
        let mapInteractionOptions = [
            this.map.scrollWheelZoom,
            this.map.doubleClickZoom,
            this.map.dragging,

            this.map.touchZoom,
            this.map.boxZoom,
            this.map.keyboard
        ];

        if (options && options.static) {
            mapInteractionOptions.map((option) => {
                option.disable();
            });
        } else {
            mapInteractionOptions.map((option) => {
                option.enable();
            });
        }

        if (options && options.fitToGeojson) {
            let resetBounds = () => {
                let bounds = this._geoJsonLayerGroup.getBounds();
                if (bounds.isValid()) {
                    this.map.fitBounds(bounds, {
                        padding: [35, 35]
                    });
                }
            };
            resetBounds();
            if (!this._followLayerAddListener) {
                this._followLayerAddListener = this.on('layeradd', resetBounds);
            }
            if (!this._followResizeListener) {
                this._followResizeListener = this.on('resize', resetBounds);
            }
        } else {
            this.off(this._followLayerAddListener);
            this.off(this._followResizeListener);
            delete this._followLayerAddListener;
            delete this._followResizeListener;
        }
    }

    setBasemap(basemapKey) {
        if (this.currentBasemap !== basemapKey) {
            if (this.currentBasemap) {
                this.map.removeLayer(this.baseMaps[this.currentBasemap]);
            }
            this.baseMaps[basemapKey].addTo(this.map);
            this.currentBasemap = basemapKey;
        }
    }

    onLayerGroupEvent(event, callback, scope = this) {
        let callbackId = this._callbackCounter;
        this._callbackCounter += 1;
        this._callbacks = this._callbacks.set(callbackId, [event, callback]);
        this._layerGroup.on(event, callback, scope);
        return callbackId;
    }

    offLayerGroupEvent(callbackId) {
        if (this._callbacks.has(callbackId)) {
            let offPair = this._callbacks.get(callbackId);
            this._layerGroup.off(offPair[0], offPair[1]);
            this._callbacks = this._callbacks.delete(callbackId);
        }
    }

    /*
     * Events functions:
     *  each listener is identified with a number.
     *  Event listeners are registered in a controller eg:
     *    let listener = map.on('click', ($event, map) => { //... });
     *  and removed in a scope destructor eg:
     *    this.listeners.forEach((listener) => {this.map.off(listener)});
     */

    /*
     * Params:
     *   event String string identifier for event which the map fires
     *   callback function callback with signature ($event) => {//...}
     *
     * Note: We need some mechanism for preventing multiple listeners on an event from
     *       triggering an event at once.
     */
    on(event, callback) {
        // wrap callback to track listeners internally
        let callbackWrapper = (e) => callback(e, this);
        let callbackId = this._callbackCounter;
        this._callbackCounter = this._callbackCounter + 1;
        this._callbacks = this._callbacks.set(callbackId, [event, callbackWrapper]);
        // create listener for map event
        this.map.on(event, callbackWrapper);
        return callbackId;
    }

    /*
     * Params:
     *   callbackId int id for the callback to deregister on the map
     */
    off(callbackId) {
        // check for valid parameters
        // delete event listener
        if (this._callbacks.has(callbackId)) {
            let offPair = this._callbacks.get(callbackId);
            this.map.off(offPair[0], offPair[1]);
            this._callbacks = this._callbacks.delete(callbackId);
        } else {
            // throw exception
        }
    }

    /** Add a geojson object to a given layer
     *  @param {string} id layer id
     *  @param {Object} geojson geojson Feature or FeatureCollection to add to the layer
     *  @param {Object} options Any default options to apply to the geojson. Optional parameter
     *  @returns {this} map wrapper object
     */
    addGeojson(id, geojson, options) {
        let features;
        let allOptions = Object.assign({}, this._geoJsonLayerGroup.options, options);
        if (Array.isArray(geojson)) {
            features = geojson;
        } else {
            features = geojson.features;
            if (geojson.properties) {
                Object.assign(allOptions, geojson.properties.options);
            }
        }

        if (features) {
            for (let feature of features) {
                if (feature.geometries || feature.geometry ||
                    feature.features || feature.coordinates) {
                    this.addGeojson(id, feature, allOptions);
                }
            }
            return this;
        }

        let layer = L.GeoJSON.geometryToLayer(geojson, allOptions);
        if (!layer) {
            return this;
        }
        layer.feature = L.GeoJSON.asFeature(geojson);
        layer.defaultOptions = layer.options;
        this._geoJsonLayerGroup.resetStyle(layer);
        if (allOptions.onEachFeature) {
            allOptions.onEachFeature(geojson, layer);
        }

        if (options && options.rectify) {
            // TODO: if rectified, needs to duplicate the polygon on the other side of the map
            this.rectifyLayerCoords(layer);
        }

        this._geoJsonLayerGroup.addLayer(layer);
        if (this._geoJsonMap.has(id)) {
            this._geoJsonMap = this._geoJsonMap.set(
                id, this._geoJsonMap.get(id).concat(layer)
            );
        } else {
            this._geoJsonMap = this._geoJsonMap.set(id, [layer]);
        }
        return this;
    }

    rectifyCoords(latlongs) {
        return latlongs.map((multipolygon) => {
            return multipolygon.map((polygon) => {
                return polygon.map((cord) => {
                    return {lng: cord.lng > 0 ? cord.lng - 360 : cord.lng, lat: cord.lat};
                });
            });
        });
    }

    shouldRectifyBounds(bounds, beforeArea, afterArea) {
        // NOTE: This magic number was tested to work with MODIS imagery, which is the largest
        //       that we've worked with so far. The largest I saw was around 10, so 30
        //       is a conservative guess that should cover everything that matters.
        //       Essentially, this boils down to checking if
        //       the middle of a polygon that may cross the anti-meridian is 30 degrees or
        //       less off center. In order for a scene to not trigger rectification, it
        //       would need to cover 60+ degrees of longitude
        const differenceThreshold = 30;
        return bounds &&
              Math.abs(bounds.getEast() + bounds.getWest()) < differenceThreshold &&
              afterArea < beforeArea;
    }

    rectifyLayerCoords(currentLayer) {
        const bounds = currentLayer.getBounds && currentLayer.getBounds();

        const currentArea = L.GeometryUtil.geodesicArea(currentLayer.getLatLngs()[0][0]);

        const latlongs = currentLayer.getLatLngs();
        const newCoords = this.rectifyCoords(latlongs);
        const rectifiedArea = L.GeometryUtil.geodesicArea(newCoords[0][0]);

        if (this.shouldRectifyBounds(bounds, currentArea, rectifiedArea)) {
            currentLayer.setLatLngs(newCoords);
        }
    }

    /** Update a geojson layer. Overwrites current contents
     *  @param {string} id unique identifier for the geojson layer.
     *  @param {Object} geoJson geojson feature(s) to replace with
     *  @param {Object} options options for the geojson object
     *  @returns {this} map wrapper
     */
    setGeojson(id, geoJson, options) {
        this.deleteGeojson(id);
        this.addGeojson(id, geoJson, options);
    }

    /** Get geojson layers for an id
     * @param {string} id id for the set of geojson objects
     * @return {Array} The set of geojson objects for an id
     */
    getGeojson(id) {
        let layerList = this._geoJsonMap.get(id);
        return layerList;
    }

    /** Delete a geojson layers associated with an id
     *  @param {string} id layer id
     *  @returns {this} this
     */
    deleteGeojson(id) {
        if (this._geoJsonMap.has(id)) {
            let layerList = this._geoJsonMap.get(id);
            for (let layer of layerList) {
                this._geoJsonLayerGroup.removeLayer(layer);
            }
            this._geoJsonMap = this._geoJsonMap.delete(id);
        }
        return this;
    }

    hideGeojson() {
        if (!this.geojsonHidden) {
            this._geoJsonLayerGroup.removeFrom(this.map);
            this.geojsonHidden = true;
        }
    }

    showGeojson() {
        if (this.geojsonHidden) {
            this._geoJsonLayerGroup.addTo(this.map);
            this.geojsonHidden = false;
        }
    }

    getGeojsonVisibility() {
        return this.geojsonHidden ? 'hidden' : 'visible';
    }

    /** Add a layer to the set of layers identified by an id
     * @param {string} id layer id
     * @param {L.Layer} layer layer to add
     * @param {Object} opts various options to configure the layer
     * @returns {this} this
     */
    addLayer(id, layer, opts) {
        const options = Object.assign(
            {
                showToggle: true,
                showLayer: true
            },
            opts
        );
        if (options.showToggle) {
            this._toggleableLayers = this._toggleableLayers.add(id);
        } else {
            this._toggleableLayers = this._toggleableLayers.delete(id);
        }

        let layerList = [
            ...this.getLayers(id),
            ...[layer]
        ];
        if (options.showLayer) {
            this._layerGroup.addLayer(layer);
            this._layerMap = this._layerMap.set(id, layerList);
        } else {
            this._hiddenLayerMap = this._hiddenLayerMap.set(id, layerList);
        }
        return this;
    }

    /** Add a Layer or LayerGroup to the map, identified by an id
     * @param {string} id id to refer to the layer by
     * @param {L.Layer | L.LayerGroup | L.Layer[]} layer layer to add
     * @param {Boolean?} showToggle show a toggle in the layer picker
     * @returns {this} this
     */
    setLayer(id, layer, showToggle) {
        if (showToggle) {
            this._toggleableLayers = this._toggleableLayers.add(id);
        } else if (showToggle === false) {
            this._toggleableLayers = this._toggleableLayers.delete(id);
        }

        let toggle = this._toggleableLayers.has(id);

        this.deleteLayers(id);

        if (toggle) {
            this._toggleableLayers = this._toggleableLayers.add(id);
        }

        if (Array.isArray(layer)) {
            this._layerMap = this._layerMap.set(id, layer);
            layer.map((layerItem) => {
                this._layerGroup.addLayer(layerItem);
            });
            return this;
        }
        return this.addLayer(id, layer);
    }

    /** Hide all layers associated with an id
     * @param {string} id id associated with layers
     * @param {Boolean?} showToggle show a toggle in the layer picker
     * @returns {this} this
     */
    hideLayers(id, showToggle) {
        let layers = this._layerMap.get(id);
        let hiddenLayers = this._hiddenLayerMap.get(id);

        if (layers) {
            if (showToggle) {
                this._toggleableLayers = this._toggleableLayers.add(id);
            } else if (showToggle === false) {
                this._toggleableLayers = this._toggleableLayers.delete(id);
            }

            let allLayers = hiddenLayers ? layers.concat(hiddenLayers) : layers;
            // add layers to this._hiddenLayerMap
            this._hiddenLayerMap = this._hiddenLayerMap.set(id, allLayers);
            // remove layers from this._layerMap
            layers.forEach((layer) => {
                this._layerGroup.removeLayer(layer);
            });
            this._layerMap = this._layerMap.delete(id);
        }

        return this;
    }

    /** Show all layers associated with an id
     * @param {string} id id associated with layers
     * @param {Boolean?} showToggle show a toggle in the layer picker
     * @returns {this} this
     */
    showLayers(id, showToggle) {
        let hiddenLayers = this._hiddenLayerMap.get(id);
        let layers = this._layerMap.get(id);
        let allLayers = hiddenLayers;
        if (hiddenLayers) {
            allLayers = layers ? hiddenLayers.concat(layers) : hiddenLayers;
            // add layers to this._layerMap
            this._layerMap = this._layerMap.set(id, allLayers);
            hiddenLayers.forEach((layer) => {
                this._layerGroup.addLayer(layer);
            });
            // remove layers from this._hiddenLayerMap
            this._hiddenLayerMap = this._hiddenLayerMap.delete(id);
        }

        if (allLayers.length && showToggle) {
            this._toggleableLayers = this._toggleableLayers.add(id);
        } else if (showToggle === false) {
            this._toggleableLayers = this._toggleableLayers.delete(id);
        }

        return this;
    }

    layerHasId(layer, id) {
        let hiddenLayers = this._hiddenLayerMap.get(id);
        let layers = this._layerMap.get(id);
        return hiddenLayers && hiddenLayers.includes(layer) || layers && layers.includes(layer);
    }

    /** Get the visibility of layers matching an id
     * @param {string} id id associated with layers
     * @returns {string} one of 'hidden' 'visible' or 'mixed'
     */
    getLayerVisibility(id) {
        let hiddenLayers = this._hiddenLayerMap.get(id);
        let visibleLayers = this._layerMap.get(id);
        let state = 'none';
        if (hiddenLayers && visibleLayers) {
            state = 'mixed';
        } else if (hiddenLayers) {
            state = 'hidden';
        } else if (visibleLayers) {
            state = 'visible';
        }
        return state;
    }

    /** Get a set of layers by id.
     * @param {string} id layer id
     * @returns {Array} list of layers
     */
    getLayers(id) {
        let visibleLayers = this._layerMap.get(id);
        let hiddenLayers = this._hiddenLayerMap.get(id);
        return []
            .concat(hiddenLayers ? hiddenLayers : [])
            .concat(visibleLayers ? visibleLayers : []);
    }

    /** Delete a layer set from the map
     * @param {string} id layer id
     * @returns {this} this
     */
    deleteLayers(id) {
        this._toggleableLayers = this._toggleableLayers.delete(id);
        let layerList = this.getLayers(id);
        if (layerList && layerList.length) {
            for (const layer of layerList) {
                this._layerGroup.removeLayer(layer);
            }
            this._layerMap = this._layerMap.delete(id);
            this._hiddenLayerMap = this._hiddenLayerMap.delete(id);
        }
        return this;
    }

    /** Display a thumbnail of the scene
     * @param {object} scene Scene with footprint to display on the map
     * @param {object} repository {label, service} repository object
     * @param {object} thumbnailOptions options for displaying the thumbnail
     *                                  valid properties: persist, useSmall, dataRepo, url
     * @returns {this} this
     */
    setThumbnail(scene, repository, thumbnailOptions) {
        if (this.persistedThumbnails.has(scene.id)) {
            return this;
        }

        let options = Object.assign({}, thumbnailOptions);

        let footprintGeojson = Object.assign(
            {},
            scene.dataFootprint,
            {
                properties: {
                    options: {
                        stroke: 1,
                        fillOpacity: 0,
                        weight: 2,
                        interactive: false
                    }
                }
            }
        );

        if (scene.tileFootprint && scene.thumbnails && scene.thumbnails.length) {
            let boundsGeoJson = L.geoJSON();
            boundsGeoJson.addData(scene.tileFootprint);
            let imageBounds = boundsGeoJson.getBounds();

            this.$q.all({
                url: repository.service.getPreview(scene),
                datasource: repository.service.getDatasource(scene)
            }).then(({url, datasource}) => {
                let overlay = this.imageOverlayService.createNewImageOverlay(
                    url,
                    imageBounds,
                    {
                        opacity: 1,
                        dataMask: repository.service.skipThumbnailClipping ?
                            scene.tileFootprint : scene.dataFootprint,
                        thumbnail: url,
                        attribution: `${datasource.name} `
                    }
                );
                if (!options.persist) {
                    this.setLayer('thumbnail', overlay);
                } else {
                    this.persistedThumbnails = this.persistedThumbnails.set(scene.id, overlay);
                    this.setLayer(
                        'Selected Scenes',
                        this.persistedThumbnails.toArray(),
                        true
                    );
                }
                this.setGeojson(
                    'thumbnail',
                    footprintGeojson,
                    {rectify: true}
                );
            });
        } else if (scene.dataFootprint) {
            this.deleteLayers('thumbnail');
            this.setGeojson(
                'thumbnail',
                footprintGeojson,
                {rectify: true}
            );
        }
        return this;
    }

    /** Delete any thumbnails from the map
     * @param {str} scene id of the scene to remove thumbnail for
     * @returns {this} this
     */
    deleteThumbnail(scene) {
        if (!scene) {
            this.deleteLayers('thumbnail');
            this.deleteGeojson('thumbnail');
        } else if (this.persistedThumbnails.has(scene.id)) {
            this.persistedThumbnails = this.persistedThumbnails.delete(scene.id);
            this.setLayer('Selected Scenes', this.persistedThumbnails.toArray());
        }
        return this;
    }

    toggleFullscreen(force) {
        // eslint-disable-next-line
        let parent = this.map._mapPane.parentElement.parentElement;
        const setMaxStyle = () => {
            let style = `
                position: fixed;
                width: 100vw;
                height: calc(100vh - 4.3em);
                z-index: 1002;
                top: 4.3em;
                left: 0;
            `.replace(/\s+/g, ' ');
            parent.style.cssText = style;
        };

        const setNormalStyle = () => {
            parent.style.cssText = 'position: relative;';
        };

        if (typeof force !== 'undefined') {
            if (force) {
                this.maximized = true;
                setMaxStyle();
            } else {
                this.maximized = false;
                setNormalStyle();
            }
        } else if (this.maximized) {
            this.maximized = false;
            setNormalStyle();
        } else {
            this.maximized = true;
            setMaxStyle();
        }

        setTimeout(() => this.map.invalidateSize(), 150);
    }
}

export default (app) => {
    class MapService {
        constructor(
          $q, imageOverlayService, datasourceService, thumbnailService
        ) {
            'ngInject';
            this.maps = new Map();
            this._mapPromises = new Map();
            this.$q = $q;
            this.imageOverlayService = imageOverlayService;
            this.datasourceService = datasourceService;
            this.thumbnailService = thumbnailService;
        }

        // Leaflet components should self-register using this method.
        registerMap(map, id, options) {
            let mapWrapper =
                new MapWrapper(
                    this.$q, map, id, this.imageOverlayService, this.datasourceService,
                    options, this.thumbnailService);
            this.maps = this.maps.set(id, mapWrapper);
            // if any promises , resolve them
            if (this._mapPromises.has(id)) {
                this._mapPromises.get(id).forEach((promise) => {
                    promise.resolve(mapWrapper);
                });
                this._mapPromises.delete(id);
            }
            return mapWrapper;
        }

        deregisterMap(id) {
            // unregister all listeners on the map
            this.getMap(id).then((mapWrapper) => {
                if (this._mapPromises.has(id)) {
                    this._mapPromises.get(id).forEach((promise) => {
                        promise.reject('Map has been deleted');
                    });
                }
                // eslint-disable-next-line no-underscore-dangle
                mapWrapper._callbacks.forEach(
                    (value, key) => {
                        mapWrapper.off(key);
                    }
                );
                this.maps = this.maps.delete(id);
            });
            // delete reference to map
            // if any promises, resolve them with a failure
        }


        getMap(id) {
            let deferred = this.$q.defer();
            let mapPromise = deferred.promise;
            // If map is registered, get map from this.maps
            if (this.maps.has(id)) {
                deferred.resolve(this.maps.get(id));
            } else if (this._mapPromises.has(id)) {
                // otherwise, wait return a promise which will get resolved once the map
                // is registered
                let promises = this._mapPromises.get(id);
                promises.push(deferred);
                this._mapPromises = this._mapPromises.set(id, promises);
            } else {
                this._mapPromises = this._mapPromises.set(id, [deferred]);
            }
            return mapPromise;
        }
    }

    app.service('mapService', MapService);
};
