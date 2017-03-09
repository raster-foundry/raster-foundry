import mapTpl from './mapContainer.html';
const mapContainer = {
    templateUrl: mapTpl,
    controller: 'MapContainerController',
    bindings: {
        mapId: '@',
        options: '<?',
        initialCenter: '<',
        initialZoom: '<'
    }
};

export default mapContainer;
