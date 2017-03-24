import Map from 'es6-map';
/* eslint no-unused-vars: 0 */
/* eslint spaced-comment: 0 */

class MapWrapper {
    constructor( // eslint-disable-line max-params
        leafletMap, mapId, imageOverlayService, datasourceService, options, thumbnailService
    ) {
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
        this._layerGroup = L.featureGroup().addTo(this.map);
        this.persistedThumbnails = new Map();
        this.disableFootprints = false;

        this._controls = L.control({position: 'topright'});
        this._controls.onAdd = function () {
            let div = L.DomUtil.create('div', 'map-control-panel');

            div.innerHTML =
                '<button class="btn btn-default"><i class="icon-resize-full"></i></button>';
            return div;
        };
        this.changeOptions(this.options);
    }

    getBaseMapLayer(layerName) {
        let url =
            `https://cartodb-basemaps-{s}.global.ssl.fastly.net/${layerName}/{z}/{x}/{y}.png`;
        let properties = {
            attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">' +
                'OpenStreetMap</a> &copy;<a href="http://cartodb.com/attributions">CartoDB</a>',
            maxZoom: 19
        };
        return L.tileLayer(url, properties);
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
            this._controls.remove();
            mapInteractionOptions.map((option) => {
                option.disable();
            });
        } else {
            mapInteractionOptions.map((option) => {
                option.enable();
            });

            // Add zoom control to map's controls
            if (!this.controlsAdded) {
                this.controlsAdded = true;
                let zoomControl = L.control.zoom({position: 'topright'});
                this._controls.addTo(this.map);
                // Add basemap controls to map's controls
                let baseMaps = {
                    Light: this.getBaseMapLayer('light_all'),
                    Dark: this.getBaseMapLayer('dark_all')
                };
                baseMaps.Light.addTo(this.map);
                let baseMapControl = L.control.layers(baseMaps, {});
                baseMapControl.addTo(this.map);
                zoomControl.addTo(this.map);

                // eslint-disable-next-line no-underscore-dangle
                let mapContainer = $(this.map._container);
                let $zoom = mapContainer.find('.leaflet-control-zoom');
                let $lpicker = mapContainer.find('.leaflet-control-layers');
                let $mpc = mapContainer.find('.map-control-panel');
                $mpc.prepend($zoom);
                $mpc.append($lpicker);
            }
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

    onLayerGroupEvent(event, callback, scope = this) {
        let callbackId = this._callbackCounter;
        this._callbackCounter += 1;
        this._callbacks.set(callbackId, [event, callback]);
        this._layerGroup.on(event, callback, scope);
        return callbackId;
    }

    offLayerGroupEvent(callbackId) {
        if (this._callbacks.has(callbackId)) {
            let offPair = this._callbacks.get(callbackId);
            this._layerGroup.off(offPair[0], offPair[1]);
            this._callbacks.delete(callbackId);
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
        this._callbacks.set(callbackId, [event, callbackWrapper]);
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
            this._callbacks.delete(callbackId);
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
        this._geoJsonLayerGroup.addLayer(layer);
        if (this._geoJsonMap.has(id)) {
            let layerList = this._geoJsonMap.get(id);
            layerList.push(layer);
        } else {
            this._geoJsonMap.set(id, [layer]);
        }
        return this;
    }

    /** Update a geojson layer. Overwrites current contents
     *  @param {string} id unique identifier for the geojson layer.
     *  @param {Object} geoJson geojson feature(s) to replace with
     *  @returns {this} map wrapper
     */
    setGeojson(id, geoJson) {
        this.deleteGeojson(id);
        this.addGeojson(id, geoJson);
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
            this._geoJsonMap.delete(id);
        }
        return this;
    }

    /** Add a layer to the set of layers identified by an id
     * @param {string} id layer id
     * @param {L.Layer} layer layer to add
     * @returns {this} this
     */
    addLayer(id, layer) {
        this._layerGroup.addLayer(layer);
        let layerList = this.getLayers(id);
        if (layerList && layerList.length) {
            layerList.push(layer);
        } else {
            this._layerMap.set(id, [layer]);
        }
        return this;
    }

    /** Add a Layer or LayerGroup to the map, identified by an id
     * @param {string} id id to refer to the layer by
     * @param {L.Layer | L.LayerGroup} layer layer to add
     * @returns {this} this
     */
    setLayer(id, layer) {
        return this.deleteLayers(id)
            .addLayer(id, layer);
    }

    /** Get a set of layers by id.
     * @param {string} id layer id
     * @returns {Array} list of layers
     */
    getLayers(id) {
        return this._layerMap.get(id);
    }

    /** Delete a layer set from the map
     * @param {string} id layer id
     * @returns {this} this
     */
    deleteLayers(id) {
        let layerList = this.getLayers(id);
        if (layerList && layerList.length) {
            for (const layer of layerList) {
                this._layerGroup.removeLayer(layer);
            }
            this._layerMap.delete(id);
        }
        return this;
    }

    /** Display a thumbnail of the scene
     * @param {object} scene Scene with footprint to display on the map
     * @param {boolean?} useSmall Use the smallest thumbnail
     * @param {boolean?} persist Whether to persist this thumbnail on the map
     * @returns {this} this
     */
    setThumbnail(scene, useSmall, persist) {
        if (this.persistedThumbnails.has(scene.id)) {
            return this;
        }
        let footprintGeojson = Object.assign({
            properties: {
                options: {
                    fillOpacity: 0,
                    weight: 0,
                    interactive: false
                }
            }
        }, scene.dataFootprint);
        if (scene.tileFootprint && scene.thumbnails && scene.thumbnails.length) {
            // get smallest thumbnail - it's a small map
            let thumbUrl = this.thumbnailService.getBestFitUrl(
                scene.thumbnails, useSmall ? 100 : 1000
            );

            let boundsGeoJson = L.geoJSON();
            boundsGeoJson.addData(scene.tileFootprint);
            let imageBounds = boundsGeoJson.getBounds();
            this.datasourceService.get(scene.datasource).then(d => {
                let overlay = this.imageOverlayService.createNewImageOverlay(
                    thumbUrl,
                    imageBounds, {
                        opacity: 1,
                        dataMask: scene.dataFootprint,
                        thumbnail: thumbUrl,
                        attribution: `©${d.name}` +
                            ' | Previews are not representative of actual scene quality.'
                    }
                );
                if (!persist) {
                    this.setLayer('thumbnail', overlay);
                } else {
                    overlay.addTo(this.map);
                    this.persistedThumbnails.set(scene.id, overlay);
                }
                this.setGeojson(
                    'thumbnail',
                    footprintGeojson
                );
            });
        } else if (scene.dataFootprint) {
            this.deleteLayers('thumbnail');
            this.setGeojson(
                'thumbnail',
                footprintGeojson
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
            this.map.removeLayer(this.persistedThumbnails.get(scene.id));
            this.persistedThumbnails.delete(scene.id);
        }
        return this;
    }

    /** Hold the state of the map in case it's needed later
      * @param {state} state to hold, e.g. specifying latlng center and zoom
      * @returns {this} this
      */
    holdState(state) {
        this.heldState = state;
        return this;
    }
}

export default (app) => {
    class MapService {
        constructor($q, imageOverlayService, datasourceService, thumbnailService) {
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
                    map, id, this.imageOverlayService, this.datasourceService,
                    options, this.thumbnailService);
            this.maps.set(id, mapWrapper);
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
                this.maps.delete(id);
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
                this._mapPromises.set(id, promises);
            } else {
                this._mapPromises.set(id, [deferred]);
            }
            return mapPromise;
        }
    }

    app.service('mapService', MapService);
};
