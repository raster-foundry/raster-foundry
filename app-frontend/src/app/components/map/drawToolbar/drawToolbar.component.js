import drawToolbarTpl from './drawToolbar.html';

/*
  @param {string} mapId id of map to use
  @param {object?} options optional object with options for the toolbar
                   {
                   areaType: text describing type of polygons being drawn
                   requirePolygons: boolean, whether to allow saving with no polygons
                   }
  @param {object?} geom geometry containing multipolygon
                  {
                    geom: {type: 'MultiPolygon', coords: [...]}
                    srid: '...'
                  }
  @param {function(geometry: Object)} onSave function to call when the save button is clicked.
                                             Object has same format as the geom property
  @param {function()} onCancel function to call when the cancel button is clicked
 */
const drawToolbar = {
    templateUrl: drawToolbarTpl,
    controller: 'DrawToolbarController',
    bindings: {
        mapId: '@',
        options: '<?',
        geom: '<?',
        onSave: '&',
        onCancel: '&'
    }
};

export default drawToolbar;
