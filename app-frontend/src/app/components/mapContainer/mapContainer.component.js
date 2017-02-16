// import mapTpl from './mapContainer.html';
const mapContainer = {
    template: '<div></div>',
    controller: 'MapContainerController',
    bindings: {
        mapId: '@',
        options: '<?',
        initialCenter: '<',
        initialZoom: '<'
    }
};

export default mapContainer;
