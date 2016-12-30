import Map from 'es6-map';
/* eslint no-underscore-dangle: ["error", {"allowAfterThis": true}] */
/* eslint no-unused-vars: 0 */
/* eslint spaced-comment: 0 */

class MapWrapper {
    constructor(leafletMap, mapId, options) {
        this.map = leafletMap;
        this.mapId = mapId;
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
        this._layerGroup = L.layerGroup().addTo(this.map);
        this.persistedThumbnails = new Map();
        this.disableFootprints = false;

        this._controls = L.control({position: 'topright'});
        this._controls.onAdd = function () {
            let div = L.DomUtil.create('div', 'map-control-panel');

            div.innerHTML =
                '<button class="btn btn-default"><i class="icon-resize-full"></i></button>' +
                '<hr>' +
                '<button class="btn btn-default btn-block"><i class="icon-search">' +
                '</i> Find places</button>';
            return div;
        };
        this.changeOptions(options);
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

            let zoom = L.control.zoom({position: 'topright'});
            this._controls.addTo(this.map);
            zoom.addTo(this.map);
            let mapContainer = $(this.map._container); // eslint-disable-line no-underscore-dangle
            let $zoom = mapContainer.find('.leaflet-control-zoom');
            let $mpc = mapContainer.find('.map-control-panel');
            $mpc.prepend($zoom);
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
        if (!persist && scene.id in this.persistedThumbnails) {
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
            let thumbnail = scene.thumbnails.reduce((lastThumbnail, currentThumbnail) => {
                if (useSmall && lastThumbnail.widthPx < currentThumbnail.widthPx ||
                    !useSmall && lastThumbnail.widthPx > currentThumbnail.widthPx) {
                    return lastThumbnail;
                }
                return currentThumbnail;
            });
            let thumbUrl = thumbnail.url;
            let boundsGeoJson = L.geoJSON();
            boundsGeoJson.addData(scene.tileFootprint);
            let imageBounds = boundsGeoJson.getBounds();
            let overlay = L.imageOverlay(thumbUrl, imageBounds, {
                opacity: 1,
                attribution: `©${scene.datasource}` +
                    ' | Previews are not representative of actual scene quality.'
            });
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
        } else {
            this.map.removeLayer(this.persistedThumbnails.get(scene.id));
            this.persistedThumbnails.delete(scene.id);
        }
        return this;
    }
}

export default (app) => {
    class MapService {
        constructor($q) {
            'ngInject';
            this.maps = new Map();
            this._mapPromises = new Map();
            this.$q = $q;
        }

        // Leaflet components should self-register using this method.
        registerMap(map, id, options) {
            let mapWrapper = new MapWrapper(map, id, options);
            this.maps.set(id, mapWrapper);
            // if any promises , resolve them
            if (this._mapPromises.has(id)) {
                this._mapPromises.get(id).forEach((promise) => {
                    promise.resolve(mapWrapper);
                });
                this._mapPromises.delete(id);
            }
        }

        deregisterMap(id) {
            // unregister all listeners on the map
            this.getMap(id).then((mapWrapper) => {
                if (this._mapPromises.has(id)) {
                    this._mapPromises.get(id).forEach((promise) => {
                        promise.reject('Map has been deleted');
                    });
                }
                mapWrapper._callbacks.forEach( // eslint-disable-line no-underscore-dangle
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
