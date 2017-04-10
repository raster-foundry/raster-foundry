export default (app) => {
    class MapUtilsService {

        /** Get the extent from a project (if it exists) and fit the map to it
          * @param {MapWrapper} mapWrapper mapWrapper object of map to interact with
          * @param {Project} project to hold, e.g. specifying latlng center and zoom
          * @param {Number} offset arbitrary padding offset (positive for zoom in)
          * @return {MapUtilsService} this service
          */
        fitMapToProject(mapWrapper, project, offset = 0) {
            if (project.extent) {
                mapWrapper.map.fitBounds(L.geoJSON(project.extent).getBounds(), {
                    padding: [offset, offset]
                });
            }
            return this;
        }
    }

    app.service('mapUtilsService', MapUtilsService);
};
