# Map component ADR
## Context
The current leaflet map implementation was intentionally made as a very thin wrapper. We've developed enough code around it to quantify what we need leaflet to do in raster foundry, as well as the shortcomings of our current approach.  

The initial approach tries to control the leaflet map exclusively thorugh bindings to callbacks and properties. We've found that this creates a lot of complexity in the components and controllers due to the way that data must flow through components.  

Ideally, components should use one-way data bindings where possible, and call bound functions in order to change data in a parent. The current way we have things set up requires all of the important state to be centralized in a parent controller, and modified through two-way bindings in child components.  

Adding layers which need to be controlled by a child attribute has necessitated creating a separate binding for each type of interaction, and this has resulted in a bloated leaflet map component.

## Current use cases

### Scene Browser
#### Attributes used:
```
footprint="$ctrl.hoveredScene.dataFootprint"
bypass-fit-bounds="true"
proposed-bounds="$ctrl.bounds"
grid="$ctrl.lastGridResult"
on-view-change="$ctrl.onViewChange(newBounds, zoom)"
```

#### Requirements
Display single polygon on map  
Fit map view to bounds  
Display a grid of polygons  
Callback on view change  

### Scene Detail & Project Scene Detail
#### Attributes used:
```
footprint="$ctrl.scene.footprint"
static="true",
```
#### Requirements
Display single polygon on map  
Option to disable map interaction  
Ability to fit map to geojson  

### Project Editor
#### Attributes used:
```
footprint="$ctrl.hoveredScene.dataFootprint"
bypass-fit-bounds="true"
layers="$ctrl.layers"
highlight="$ctrl.highlight"
on-map-click="$ctrl.onMapClick(event)"
allow-drawing="$ctrl.allowDrawing"
drawn-polygons="$ctrl.drawnPolygons"
on-view-change="$ctrl.onViewChange(newBounds, zoom)"
```
#### Requirements
Display single polygon on map  
fine grain control over layers  
Highlight  
map click events  
map view events  
option for drawing  


## Proposed Solution

#### Consolidate toggles to an "options" attribute on the leaflet component
* fitToLayerId : expects a layer id as defined below. . Replaces bypass-fit-bounds
* static

```javascript
this.mapOptions = {
  fitToLayerId: 'footprint',
  static: false
}
this.map.addGeojson(scene.footprint, 'footprint');
```


#### Create a map service for most of the data interaction
Geojson: internal map from geojson id to map layer for more efficient re-rendering on change.
         This can be a uuid (eg: scene uuid), a general identifier (eg: "highlight" / "footprint")
         or a domain specific id (eg: calculated grid cell identifier "1,1,1" / "2,1,3")

When leaflet components are initialized / destroyed, they should register themselves with the service.
Any subviews which need to interact with the map should do it through the service so we don't need
complex data bindings just to interact with the map.

In abstract, we require:
* A way to define geojson layers and modify them without re-rendering all of the layers
* A way to define editable basemap layers and re-order / parameterize them on the fly
* A way to listen for map events and act on them. Callbacks are responsible for using evalAsync() if necessary.
* More efficient way to add/manage a grid.
* Utility functions related to map interaction should be on the MapWrapper class
* One time use functions should be on the relevant controller
* Global map state should be stored in the wrapper (eg - geojson object tracking by user defined id)
Preference should be to use the API where possible, but for initial concepts and places where it
doesn't make sense to create an API, the MapWrapper should expose the map object.
  
### Leaflet service api:
#### MapWrapper API
Property | Type | Description
--- | --- | ---
`map` | `L.map` | The wrapped Leaflet map 
`mapId` | `string` | Id for the wrapped leaflet map
`_callbackCounter` | `int` | Internal counter used for preventing callback id clashes
`_callbacks` | `es6-map` | Internal map which stores callbacks `<integer> callbackId -> [eventDescriptor, wrappedCallback]`

Method | Returns | Description
--- | --- | ---
constructor(`<L.map> leafletMap`, `<string> mapId`) | `this` | Constructor for MapWrapper
on(`<string> eventDescriptor`, `<function> callback`) | `<integer> callbackId` | Register a map event listener
off(`<integer> callbackId`) | `-` | Unregister a map event listener
addGeojson(`<string> id, <object> geoJson`) | `this` | Add a geojson object layer to the map, identified with an Id for updates / deletion.  Geojson object may optionally contain properties which define style and tooltip actions
getGeojson(`<string> id`) | `L.GeoJSON` | Get a geojson layer
updateGeojson(`<string> id, <object> geoJson`) | `this` | Update a geojson object layer
deleteGeojson(`<string> id`) | `this` | Delete a geojson object layer
setLayer(`<L.Layer|L.LayerGroup> layer`, `<string> id`) | `this` | Add a Layer or LayerGroup, identified by an id.  Layers will be wrapped in a layergroup.
getLayer(`<string> id`) | `L.LayerGroup` | Get a map layer group
deleteLayer(`<string> id`) | `this` | Delete a map layer

#### MapService API
Property | Type | Description
--- | --- | ---
`maps` | `es6-map(<string> id -> <MapWrapper>)` | es6 map from mapIds to MapWrappers
`_mapPromises` | `es6-map(<string> id -> Promise(<MapWrapper))` | internal es6 map from mapIds to promises which must be resolved to MapWrappers

Method | Returns | Description
--- | --- | ---
constructor() | `this` | Constructor for the MapService
registerMap(`<L.map> map`, `<string> id`) | `-` | Register a map with the MapService.  This should only be called be the rfLeafletMap component to self-register.
deregisterMap(`<string> id`) | `-` | Deregister a map with the map service.  This should automatically delete any remaining user created listeners
getMap(`<string> id`) | `Promise(<MapWrapper>)` | Get a promise which resolves to a MapWrapper

#### Map Service skeleton with more detailed documentation
```javascript
class MapWrapper {
  constructor(leafletMap, mapId) {
    this.map = leafletMap;
    this.mapId = mapId;
    // counter is not global - each map has its own. So don't combine lists of listeners across maps
    this._callbackCounter = 0;
    this._callbacks = new Map();
  }
  
  /*
   * Events functions:
   *  each listener is identified with a number.
   *  Event listeners are registered in a controller eg:
   *    let listener = map.on('click', ($event, map) => { ... });
   *  and removed in a scope destructor eg:
   *    this.listeners.forEach((listener) => {this.map.off(listener)});
   */

  /*
   * Params:
   *   event String string identifier for event which the map fires
   *   callback function callback with signature ($event, map) => {...}
   *
   * Note: We need some mechanism for preventing multiple listeners on an event from
   *       triggering an event at once.
   */
  on(event, callback) {
    // wrap callback to track listeners internally
    let wrappedCallback = (e) => callback(e, this.map);
    let callbackId = this._callbackCounter;
    this._callbackCounter = this._callbackCounter + 1;
    this._callbacks.set(callbackCounter, [event, wrappedCallback]);
    // create listener for map event
    this.map.on(event, wrappedCallback);
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
  
  /*
   * Geojson layer API
   *   The GeoJSON layer has the worst leaflet api, so these functions should do the heavy lifting
   *   for displaying any geojson data on the map through a simple API.  Any customization such as
   *   per component styling should be stored in the properties.
   * GeoJSON property parameters:
   *   render function Function with signature (properties) => Object.  
   *                   Object maps style keys to values which are to be applied to the svg. This is
   *                   pretty direct analogue to the leaflet style property.
   */
  /*
   * Add a geojson layer
   * 
   * Internally, this should create a map of user assigned ids to leaflet internal layer ids. 
   * This would allow us to update large sets of geojson objects selectively.
   * Params:
   *   id string unique identifier for the geojson. This id must be used in order to edit 
   *             geojson that's on the map
   *   geoJson object geojson feature(s) to associate with the id.  This function should 
   *                  support editing/deleting a set of geoJson features together as a set.  
   *                  For simplicity's sake, any objects which are identified by a single 
   *                  id will be removed then added back, so if you want to optimize updates,
   *                  each component of an update should be tracked separately
   */
  addGeojson(id, geoJson) {
  ...
  }
  
  /*
   * Update a geojson layer
   * Params:
   *   id string unique identifier for the geojson layer.
   *   geoJson object geojson feature(s) to replace with
   */
  updateGeojson(id, geoJson) {
  ...
  }
  
  /*
   * Delete a geojson layer
   * Params:
   *   id string unique identifier for the geojson layer
   */
  deleteGeojson(id) {
  ...
  }
  
  /*
   * Add a Layer or LayerGroup to the map, identified by an id
   * @param {L.Layer | L.LayerGroup} layer layer to add
   * @param {string} id id to refer to the layer by
   */
  setLayer(layer, id) {
  ...
  }
  
  /*
   * Get a layer by id
   * @returns {L.LayerGroup}
   */
  getLayer(id) {
  ...
  }
  
  /*
   * Delete a layer from the map
   */
  deleteLayer(id) {
  ...
  }
}

class MapService {
  constructor() {
    this.maps = new Map();
    this._mapPromises = new Map();
  }

  // Leaflet components should self-register using this method.
  registerMap(map, id) {
    let mapWrapper = new MapWrapper(map, id);
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
    ...
    // delete reference to map
    this.maps.delete(id);
    // if any promises, resolve them with a failure
  }
  
  
  getMap(id) {
    let deferred = $q.defer();
    // If map is registered, get map from this.maps
    if (this.maps.has(id)) {
      deferred.resolve(this.maps.get(id));
    }
    // otherwise, wait return a promise which will get resolved once the map is registered
    else {
      let mapPromise = deferred.promise;
      if (this._mapPromises.has(id)) {
        let promises = this._mapPromises.get(id);
        promises.push(mapPromise);
        this._mapPromises.set(id, promises);
      } else {
        this._mapPromises.set(id, [mapPromise]);
      }
    }
    return mapPromise;
  }
}
```
#### Leaflet component attributes
```javascript
class rfLeafletComponent {
  mapId: "<",
  options: "<",
}
```

#### Example interface for a scene footprint preview component:
```html
<rf-leaflet-map map-id="scene-preview"
                options="$ctrl.previewOptions">
</rf-leaflet-map>
```
```javascript
class scenePreviewComponent {
  constructor(mapService) {
    this.previewOptions = {
      static: true,
      fitToLayerId: 'footprint'
    };
    mapService.getMap('scene-preview').then((map) => {
      this.scenePreviewMap = map;
      this.scenePreviewMap.addGeojson('footprint', this.getSceneFootprint());
    });
  }
}
```

#### Example interface for browse component:
```html
<rf-leaflet-map map-id="browse"
                options="$ctrl.browseOptions">
</rf-leaflet-map>
```
```javascript
class browseComponent {
  constructor(mapService) {
    this.browseOptions = {};
    mapService.getMap('browse').then((map) => {
      this.browseMap = map;
      this.browseMap.map.fitBounds(this.params.bbox);
      this.mapListeners = [this.browseMap.on('moveend', this.onViewChange.bind(this))];
    });
  }

  // events will call back with two arguments: the event as $event, and the map object it is from
  onViewChange($event, map) {
    let newBounds = map.getBounds();
    let zoom = map.getZoom();
    ...
  }

  $onDestroy() {
    this.mapListeners.forEach((listener) => {
      this.browseMap.off(listener);
    });
  }
}
```

## Consequences / Tradeoffs
### Limiting angular attributes
Most of the map interaction will happen through a map service, rather than on angular attributes.  
#### Pros
By limiting map interaction to service and map wrapper calls, we avoid much of the complexity that arose from trying to control the map entirely through bindings.  Leaflet doesn't fit perfectly into the angular paradigm of controlling things exclusively through bindings, so we shouldn't try to force our interactions to do so.
#### Cons
This means that we need to manage how interactions with the map affect data in the angular context manually.  This creates some additional complexity in terms of the angular lifecycle.

### Self registering map component
The leaflet component should register itself with the map service, and be exposed through a map wrapper.  
#### Pros
This creates a central location for map utility functions that are used in multiple places, an easy way to put multiple maps on the page at once and control them, and a place to store map specific state other than the containing controller.
#### Cons
We need to be careful about where we store state - it would be easy to keep adding convenience functions to the map wrapper.  We should make sure that a function involves map state before placing it on the wrapper. Simple utility functions should probably be put on the map service instead, to keep the wrapper from getting bloated.

