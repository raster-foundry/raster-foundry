import labMapTpl from './labMap.html';
const labMap = {
    templateUrl: labMapTpl,
    controller: 'LabMapController',
    bindings: {
        mapId: '@',
        options: '<?',
        initialCenter: '<?',
        onClose: '&'
    }
};

export default labMap;
