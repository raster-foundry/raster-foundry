/* eslint-disable */
export default angular.module('index.components', [
    //admin components
    require('./components/admin/editableLogo/editableLogo.js').default.name,
    require('./components/admin/sidebarUserList/sidebarUserList.js').default.name,
    require('./components/admin/sidebarTeamList/sidebarTeamList.js').default.name,
    require('./components/admin/sidebarOrganizationList/sidebarOrganizationList.js').default.name,

    // permission components
    require('./components/permissions/permissionModal/permissionModal.js').default.name,
    require('./components/permissions/permissionItem/permissionItem.js').default.name,

    // scene components
    require('./components/scenes/importList/importList.module.js').default.name,
    require('./components/scenes/sceneItem/sceneItem.module.js').default.name,
    require('./components/scenes/sceneDetail/sceneDetail.module.js').default.name,
    require('./components/scenes/sceneDetailModal/sceneDetailModal.module.js').default.name,
    require('./components/scenes/sceneImportModal/sceneImportModal.module.js').default.name,
    require('./components/scenes/sceneDownloadModal/sceneDownloadModal.module.js').default.name,
    require('./components/scenes/sceneFilterPane/sceneFilterPane.module.js').default.name,
    require('./components/scenes/planetSceneDetailModal/planetSceneDetailModal.module.js').default.name,

    // vector components
    require('./components/vectors/vectorImportModal/vectorImportModal.module.js').default.name,
    require('./components/vectors/shapeItem/shapeItem.module.js').default.name,
    require('./components/vectors/vectorNameModal/vectorNameModal.module.js').default.name,

    // project components
    require('./components/projects/projectItem/projectItem.module.js').default.name,
    require('./components/projects/projectPublishModal/projectPublishModal.module.js').default.name,
    require('./components/projects/projectSelectModal/projectSelectModal.module.js').default.name,
    require('./components/projects/projectCreateModal/projectCreateModal.module.js').default.name,
    require('./components/projects/projectExportModal/projectExportModal.module.js').default.name,
    require('./components/projects/annotateSidebarItem/annotateSidebarItem.module.js').default.name,

    // datasource components
    require('./components/datasources/datasourceItem/datasourceItem.module.js').default.name,
    require('./components/datasources/datasourceCreateModal/datasourceCreateModal.module.js').default.name,
    require('./components/datasources/datasourceDeleteModal/datasourceDeleteModal.module.js').default.name,

    // lab components
    require('./components/lab/templateItem/templateItem.module.js').default.name,
    require('./components/lab/templateCreateModal/templateCreateModal.module.js').default.name,
    require('./components/lab/reclassifyModal/reclassifyModal.module.js').default.name,
    require('./components/lab/reclassifyTable/reclassifyTable.module.js').default.name,
    require('./components/lab/reclassifyEntry/reclassifyEntry.module.js').default.name,
    require('./components/lab/diagramContainer/diagramContainer.module.js').default.name,
    require('./components/lab/diagramNodeHeader/diagramNodeHeader.module.js').default.name,
    require('./components/lab/inputNode/inputNode.module.js').default.name,
    require('./components/lab/operationNode/operationNode.module.js').default.name,
    require('./components/lab/constantNode/constantNode.module.js').default.name,
    require('./components/lab/classifyNode/classifyNode.module.js').default.name,
    require('./components/lab/nodeStatistics/nodeStatistics.module.js').default.name,
    require('./components/lab/labNode/labNode.module.js').default.name,
    require('./components/lab/colormapModal/colormapModal.js').default.name,
    require('./components/map/nodeSelector/nodeSelector.module.js').default.name,

    // map components
    require('./components/map/mapContainer/mapContainer.module.js').default.name,
    require('./components/map/staticMap/staticMap.module.js').default.name,
    require('./components/map/drawToolbar/drawToolbar.module.js').default.name,
    require('./components/map/labMap/labMap.module.js').default.name,
    require('./components/map/mapSearchModal/mapSearchModal.module.js').default.name,
    require('./components/map/annotateToolbar/annotateToolbar.module.js').default.name,
    require('./components/map/measurementPopup/measurementPopup.module.js').default.name,

    // settings components
    require('./components/settings/refreshTokenModal/refreshTokenModal.module.js').default.name,
    require('./components/settings/enterTokenModal/enterTokenModal.module.js').default.name,
    require('./components/settings/featureFlagOverrides/featureFlagOverrides.module.js').default.name,
    require('./components/settings/tokenItem/tokenItem.module.js').default.name,
    require('./components/settings/userModal/userModal.module.js').default.name,
    require('./components/settings/teamModal/teamModal.module.js').default.name,
    require('./components/settings/organizationModal/organizationModal.module.js').default.name,
    require('./components/settings/addUserModal/addUserModal.module.js').default.name,
    require('./components/settings/addPhotoModal/addPhotoModal.module.js').default.name,

    // export components
    require('./components/exports/exportItem/exportItem.module.js').default.name,
    require('./components/exports/exportDownloadModal/exportDownloadModal.module.js').default.name,
    require('./components/exports/exportAnalysisDownloadModal/exportAnalysisDownloadModal.module.js').default.name,

    // filter components
    require('./components/filters/daterangeFilter/daterangeFilter.module.js').default.name,
    require('./components/filters/searchSelectFilter/searchSelectFilter.module.js').default.name,
    require('./components/filters/shapeFilter/shapeFilter.module.js').default.name,
    require('./components/filters/sliderFilter/sliderFilter.module.js').default.name,
    require('./components/filters/tagFilter/tagFilter.module.js').default.name,


    // common components (no domain)
    require('./components/common/navBar/navBar.module.js').default.name,
    require('./components/common/toggle-old/toggle-old.module.js').default.name,
    require('./components/common/toggle/toggle.module.js').default.name,
    require('./components/common/confirmationModal/confirmationModal.module.js').default.name,
    require('./components/common/feedbackModal/feedbackModal.module.js').default.name,
    require('./components/common/boxSelectItem/boxSelectItem.module.js').default.name,
    require('./components/common/callToActionItem/callToActionItem.module.js').default.name,
    require('./components/common/dateRangePickerModal/dateRangePickerModal.module.js').default.name,
    require('./components/common/datePickerModal/datePickerModal.module.js').default.name,
    require('./components/common/statusTag/statusTag.module.js').default.name,
    require('./components/common/search/search.js').default.name,
    require('./components/common/sortingHeader/sortingHeader.js').default.name,
    require('./components/common/dropdown/dropdown.module.js').default.name,
    require('./components/common/paginationCount/paginationCount.js').default.name,
    require('./components/common/paginationControls/paginationControls.js').default.name,
    require('./components/common/navbarSearch/navbarSearch.js').default.name,


    // Single components for new domains
    require('./components/aoiFilterPane/aoiFilterPane.module.js').default.name,

    // project color composite components
    require('./components/colorComposites/colorSchemeBuilder/colorSchemeBuilder.module.js').default.name,
    require('./components/colorComposites/colorSchemeDropdown/colorSchemeDropdown.module.js').default.name,
    require('./components/colorComposites/bandSelect/bandSelect.js').default.name,

    // histogram components
    require('./components/histogram/channelHistogram/channelHistogram.module.js').default.name,
    require('./components/histogram/nodeHistogram/nodeHistogram.module.js').default.name,
    require('./components/histogram/histogramBreakpoint/histogramBreakpoint.module.js').default.name,
    require('./components/histogram/reclassifyHistogram/reclassifyHistogram.module.js').default.name
]);
