import mapTpl from './staticMap.html';
const staticMap = {
    templateUrl: mapTpl,
    controller: 'StaticMapController',
    bindings: {
        mapId: '@',
        options: '<?',
        initialCenter: '<',
        initialZoom: '<'
    }
};

export default staticMap;
