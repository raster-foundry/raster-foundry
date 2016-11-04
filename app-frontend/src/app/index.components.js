export default angular.module('index.components', [
    require('./components/leafletMap/leafletMap.module.js').name,
    require('./components/navBar/navBar.module.js').name,
    require('./components/filterPane/filterPane.module.js').name,
    require('./components/sceneItem/sceneItem.module.js').name,
    require('./components/sceneDetail/sceneDetail.module.js').name,
    require('./components/bucketItem/bucketItem.module.js').name,
    require('./components/selectedScenesModal/selectedScenesModal.module.js').name,
    require('./components/confirmationModal/confirmationModal.module.js').name,
    require('./components/bucketAddModal/bucketAddModal.module.js').name,
    require('./components/refreshTokenModal/refreshTokenModal.module.js').name,
    require('./components/downloadModal/downloadModal.module.js').name
]);
