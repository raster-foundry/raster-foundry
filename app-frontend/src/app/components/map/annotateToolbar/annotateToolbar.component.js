import annotateToolbarTpl from './annotateToolbar.html';

/*
  @param {string} mapId id of map to use
  @param {object?} geom geometry containing FeatureCollection
  @param {function(geometry: Object)} onSave function to call when the save button is clicked.\
 */
const annotateToolbar = {
    templateUrl: annotateToolbarTpl,
    controller: 'AnnotateToolbarController',
    bindings: {
        mapId: '@',
        geom: '<?',
        onSave: '&'
    }
};

export default annotateToolbar;
