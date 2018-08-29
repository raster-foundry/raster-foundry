/* eslint-disable */
export default angular.module('index.components', [
    //admin components
    require('./components/admin/editableLogo/editableLogo.js').name,
    require('./components/admin/sidebarUserList/sidebarUserList.js').name,
    require('./components/admin/sidebarTeamList/sidebarTeamList.js').name,
    require('./components/admin/sidebarOrganizationList/sidebarOrganizationList.js').name,

    // permission components
    require('./components/permissions/permissionModal/permissionModal.js').name,
    require('./components/permissions/permissionItem/permissionItem.js').name,

    // scene components
    require('./components/scenes/importList/importList.module.js').name,
    require('./components/scenes/sceneItem/sceneItem.module.js').name,
    require('./components/scenes/sceneDetail/sceneDetail.module.js').name,
    require('./components/scenes/sceneDetailModal/sceneDetailModal.module.js').name,
    require('./components/scenes/sceneImportModal/sceneImportModal.module.js').name,
    require('./components/scenes/sceneDownloadModal/sceneDownloadModal.module.js').name,
    require('./components/scenes/sceneFilterPane/sceneFilterPane.module.js').name,
    require('./components/scenes/planetSceneDetailModal/planetSceneDetailModal.module.js').name,

    // vector components
    require('./components/vectors/vectorImportModal/vectorImportModal.module.js').name,
    require('./components/vectors/shapeItem/shapeItem.module.js').name,
    require('./components/vectors/vectorNameModal/vectorNameModal.module.js').name,

    // project components
    require('./components/projects/projectItem/projectItem.module.js').name,
    require('./components/projects/projectPublishModal/projectPublishModal.module.js').name,
    require('./components/projects/projectSelectModal/projectSelectModal.module.js').name,
    require('./components/projects/projectCreateModal/projectCreateModal.module.js').name,
    require('./components/projects/projectExportModal/projectExportModal.module.js').name,
    require('./components/projects/annotateSidebarItem/annotateSidebarItem.module.js').name,

    // datasource components
    require('./components/datasources/datasourceItem/datasourceItem.module.js').name,
    require('./components/datasources/datasourceCreateModal/datasourceCreateModal.module.js').name,
    require('./components/datasources/datasourceDeleteModal/datasourceDeleteModal.module.js').name,

    // lab components
    require('./components/lab/templateItem/templateItem.module.js').name,
    require('./components/lab/templateCreateModal/templateCreateModal.module.js').name,
    require('./components/lab/reclassifyModal/reclassifyModal.module.js').name,
    require('./components/lab/reclassifyTable/reclassifyTable.module.js').name,
    require('./components/lab/reclassifyEntry/reclassifyEntry.module.js').name,
    require('./components/lab/diagramContainer/diagramContainer.module.js').name,
    require('./components/lab/diagramNodeHeader/diagramNodeHeader.module.js').name,
    require('./components/lab/inputNode/inputNode.module.js').name,
    require('./components/lab/operationNode/operationNode.module.js').name,
    require('./components/lab/constantNode/constantNode.module.js').name,
    require('./components/lab/classifyNode/classifyNode.module.js').name,
    require('./components/lab/nodeStatistics/nodeStatistics.module.js').name,
    require('./components/lab/labNode/labNode.module.js').name,
    require('./components/lab/colormapModal/colormapModal.js').name,
    require('./components/map/nodeSelector/nodeSelector.module.js').name,

    // map components
    require('./components/map/mapContainer/mapContainer.module.js').name,
    require('./components/map/staticMap/staticMap.module.js').name,
    require('./components/map/drawToolbar/drawToolbar.module.js').name,
    require('./components/map/labMap/labMap.module.js').name,
    require('./components/map/mapSearchModal/mapSearchModal.module.js').name,
    require('./components/map/annotateToolbar/annotateToolbar.module.js').name,
    require('./components/map/measurementPopup/measurementPopup.module.js').name,

    // settings components
    require('./components/settings/refreshTokenModal/refreshTokenModal.module.js').name,
    require('./components/settings/enterTokenModal/enterTokenModal.module.js').name,
    require('./components/settings/featureFlagOverrides/featureFlagOverrides.module.js').name,
    require('./components/settings/tokenItem/tokenItem.module.js').name,
    require('./components/settings/userModal/userModal.module.js').name,
    require('./components/settings/teamModal/teamModal.module.js').name,
    require('./components/settings/organizationModal/organizationModal.module.js').name,
    require('./components/settings/addUserModal/addUserModal.module.js').name,
    require('./components/settings/addPhotoModal/addPhotoModal.module.js').name,

    // export components
    require('./components/exports/exportItem/exportItem.module.js').name,
    require('./components/exports/exportDownloadModal/exportDownloadModal.module.js').name,
    require('./components/exports/exportAnalysisDownloadModal/exportAnalysisDownloadModal.module.js').name,

    // filter components
    require('./components/filters/daterangeFilter/daterangeFilter.module.js').name,
    require('./components/filters/searchSelectFilter/searchSelectFilter.module.js').name,
    require('./components/filters/shapeFilter/shapeFilter.module.js').name,
    require('./components/filters/sliderFilter/sliderFilter.module.js').name,
    require('./components/filters/tagFilter/tagFilter.module.js').name,


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
    require('./components/common/search/search.js').name,
    require('./components/common/sortingHeader/sortingHeader.js').name,
    require('./components/common/dropdown/dropdown.module.js').name,
    require('./components/common/paginationCount/paginationCount.js').name,
    require('./components/common/paginationControls/paginationControls.js').name,
    require('./components/common/navbarSearch/navbarSearch.js').name,


    // Single components for new domains
    require('./components/aoiFilterPane/aoiFilterPane.module.js').name,

    // project color composite components
    require('./components/colorComposites/colorSchemeBuilder/colorSchemeBuilder.module.js').name,
    require('./components/colorComposites/colorSchemeDropdown/colorSchemeDropdown.module.js').name,
    require('./components/colorComposites/bandSelect/bandSelect.js').name,

    // histogram components
    require('./components/histogram/channelHistogram/channelHistogram.module.js').name,
    require('./components/histogram/nodeHistogram/nodeHistogram.module.js').name,
    require('./components/histogram/histogramBreakpoint/histogramBreakpoint.module.js').name,
    require('./components/histogram/reclassifyHistogram/reclassifyHistogram.module.js').name
]);
