export default angular.module('index.components', [
    require('./components/mapContainer/mapContainer.module.js').name,
    require('./components/diagramContainer/diagramContainer.module.js').name,
    require('./components/navBar/navBar.module.js').name,
    require('./components/toolSearch/toolSearch.module.js').name,
    require('./components/toolItem/toolItem.module.js').name,
    require('./components/filterPane/filterPane.module.js').name,
    require('./components/boxSelectItem/boxSelectItem.module.js').name,
    require('./components/callToActionItem/callToActionItem.module.js').name,
    require('./components/channelHistogram/channelHistogram.module.js').name,
    require('./components/createProjectModal/createProjectModal.module.js').name,
    require('./components/mosaicScenes/mosaicScenes.module.js').name,
    require('./components/mosaicMask/mosaicMask.module.js').name,
    require('./components/maskItem/maskItem.module.js').name,
    require('./components/mosaicParameters/mosaicScenesParameters.module.js').name,
    require('./components/sceneList/sceneList.module.js').name,
    require('./components/sceneItem/sceneItem.module.js').name,
    require('./components/sceneDetail/sceneDetail.module.js').name,
    require('./components/projectItem/projectItem.module.js').name,
    require('./components/publishModal/publishModal.module.js').name,
    require('./components/selectedScenesModal/selectedScenesModal.module.js').name,
    require('./components/confirmationModal/confirmationModal.module.js').name,
    require('./components/projectAddModal/projectAddModal.module.js').name,
    require('./components/refreshTokenModal/refreshTokenModal.module.js').name,
    require('./components/downloadModal/downloadModal.module.js').name,
    require('./components/featureFlagOverrides/featureFlagOverrides.module.js').name,
    require('./components/toggle/toggle.module.js').name,
    require('./components/tokenItem/tokenItem.module.js').name,
    require('./components/datasourceItem/datasourceItem.module.js').name,
    require('./components/selectProjectModal/selectProjectModal.module.js').name,
    require('./components/importModal/importModal.module.js').name,
    require('./components/staticMap/staticMap.module.js').name
]);
