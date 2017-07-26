/* eslint-disable */
export default angular.module('index.components', [
    // scene components
    require('./components/scenes/sceneList/sceneList.module.js').name,
    require('./components/scenes/sceneItem/sceneItem.module.js').name,
    require('./components/scenes/sceneDetail/sceneDetail.module.js').name,
    require('./components/scenes/sceneDetailModal/sceneDetailModal.module.js').name,
    require('./components/scenes/sceneImportModal/sceneImportModal.module.js').name,
    require('./components/scenes/sceneDownloadModal/sceneDownloadModal.module.js').name,
    require('./components/scenes/sceneFilterPane/sceneFilterPane.module.js').name,

    // project components
    require('./components/projects/projectItem/projectItem.module.js').name,
    require('./components/projects/projectPublishModal/projectPublishModal.module.js').name,
    require('./components/projects/projectAddScenesModal/projectAddScenesModal.module.js').name,
    require('./components/projects/projectSelectModal/projectSelectModal.module.js').name,
    require('./components/projects/projectCreateModal/projectCreateModal.module.js').name,
    require('./components/projects/projectExportModal/projectExportModal.module.js').name,

    // datasource components
    require('./components/datasources/datasourceItem/datasourceItem.module.js').name,
    require('./components/datasources/datasourceCreateModal/datasourceCreateModal.module.js').name,

    // tool components
    require('./components/tools/toolSearch/toolSearch.module.js').name,
    require('./components/tools/toolItem/toolItem.module.js').name,
    require('./components/tools/toolCreateModal/toolCreateModal.module.js').name,
    require('./components/tools/diagramContainer/diagramContainer.module.js').name,
    require('./components/tools/diagramNodeHeader/diagramNodeHeader.module.js').name,
    require('./components/tools/inputNode/inputNode.module.js').name,
    require('./components/tools/operationNode/operationNode.module.js').name,
    require('./components/tools/constantNode/constantNode.module.js').name,
    require('./components/map/nodeSelector/nodeSelector.module.js').name,

    // map components
    require('./components/map/mapContainer/mapContainer.module.js').name,
    require('./components/map/staticMap/staticMap.module.js').name,
    require('./components/map/drawToolbar/drawToolbar.module.js').name,
    require('./components/map/labMap/labMap.module.js').name,
    require('./components/map/mapSearchModal/mapSearchModal.module.js').name,
    require('./components/map/annotateToolbar/annotateToolbar.module.js').name,
    require('./components/map/annotateSidebarItem/annotateSidebarItem.module.js').name,

    // settings components
    require('./components/settings/refreshTokenModal/refreshTokenModal.module.js').name,
    require('./components/settings/enterTokenModal/enterTokenModal.module.js').name,
    require('./components/settings/featureFlagOverrides/featureFlagOverrides.module.js').name,
    require('./components/settings/tokenItem/tokenItem.module.js').name,

    // export components
    require('./components/exports/exportItem/exportItem.module.js').name,
    require('./components/exports/exportDownloadModal/exportDownloadModal.module.js').name,

    // common components (no domain)
    require('./components/common/navBar/navBar.module.js').name,
    require('./components/common/toggle-old/toggle-old.module.js').name,
    require('./components/common/toggle/toggle.module.js').name,
    require('./components/common/confirmationModal/confirmationModal.module.js').name,
    require('./components/common/boxSelectItem/boxSelectItem.module.js').name,
    require('./components/common/callToActionItem/callToActionItem.module.js').name,
    require('./components/common/dateRangePickerModal/dateRangePickerModal.module.js').name,
    require('./components/common/datePickerModal/datePickerModal.module.js').name,
    require('./components/common/statusTag/statusTag.module.js').name,

    // Single components for new domains
    require('./components/aoiFilterPane/aoiFilterPane.module.js').name,
    require('./components/channelHistogram/channelHistogram.module.js').name,
]);
