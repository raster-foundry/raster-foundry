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

        /**
         * From a lng coordinate and a zoom level, determine the x-coordinate of
         * the tile in falls within
         * @param {numeric} lng lng coordinate
         * @param {numeric} zoom integer zoom level
         *
         * @returns {numeric} x-coordinate of tile
         */
        lng2Tile(lng, zoom) {
            return Math.floor((lng + 180) / 360 * Math.pow(2, zoom + 2));
        }

        /**
         * From a lat coordinate and a zoom level, determine the y-coordinate of
         * the tile in falls within
         * @param {numeric} lat lat coordinate
         * @param {numeric} zoom integer zoom level
         *
         * @returns {numeric} y-coordinate of tile
         */
        lat2Tile(lat, zoom) {
            return Math.floor((1 - Math.log(Math.tan(lat * Math.PI / 180) +
                                            1 / Math.cos(lat * Math.PI / 180))
                               / Math.PI) / 2 * Math.pow(2, zoom + 2));
        }

        /**
         * From a tile coordinate and zoom level, determine the lng coordinate of
         * the corresponding tile's NE corner
         * @param {numeric} x x-coordinate of tile
         * @param {numeric} zoom integer zoom level
         *
         * @returns {numeric} lng point of tile NE corner
         */
        tile2Lng(x, zoom) {
            return x / Math.pow(2, zoom + 2) * 360 - 180;
        }

        /**
         * From a tile coordinate and zoom level, determine the lat coordinate of
         * the corresponding tile's NE corner
         * @param {numeric} y y-coordinate of tile
         * @param {numeric} zoom integer zoom level
         *
         * @returns {numeric} lat point of tile NE corner
         */
        tile2Lat(y, zoom) {
            let n = Math.PI - 2 * Math.PI * y / Math.pow(2, zoom + 2);
            return 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
        }
    }

    app.service('mapUtilsService', MapUtilsService);
};
