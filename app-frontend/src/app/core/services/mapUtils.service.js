export default (app) => {
    class MapUtilsService {

        /** Get the extent from a project (if it exists) and fit the map to it
          * @param {MapWrapper} mapWrapper mapWrapper object of map to interact with
          * @param {Project} project to hold, e.g. specifying latlng center and zoom
          * @return {MapUtilsService} this service
          */
        fitMapToProject(mapWrapper, project) {
            if (project.extent) {
                mapWrapper.map.fitBounds(L.geoJSON(project.extent).getBounds());
            }
            return this;
        }
    }

    app.service('mapUtilsService', MapUtilsService);
};
