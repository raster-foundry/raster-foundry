/* global BUILDCONFIG, L */
let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

export default class ShareController {
    constructor( // eslint-disable-line max-params
        $rootScope, $log, $state, authService, projectService, mapService, mapUtilsService
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);
    }

    $onInit() {
        this.assetLogo = assetLogo;
        this.projectId = this.$state.params.projectid;
        this.testNoAuth = false;
        this.sceneList = [];

        if (this.projectId) {
            this.loadProject();
        }
    }

    loadProject() {
        this.loadingProject = true;
        this.projectError = false;
        this.projectService.query({id: this.projectId}).then(
            p => {
                this.project = p;
                this.loadingProject = false;
                this.projectError = false;
                this.fitProjectExtent();
                this.addProjectLayer();
            },
            (e) => {
                this.loadingProject = false;
                this.projectError = e;
            }
        );
    }

    getMap() {
        return this.mapService.getMap('share-map');
    }

    addProjectLayer() {
        // Only set query parameters if token is present to prevent serializing a null
        // token as a string in query parameters
        let token = this.authService.token();
        let queryParameters = token ? {token: token} : null;
        let url = this.projectService.getProjectTileURL(this.project, queryParameters);
        let layer = L.tileLayer(url, {maxZoom: 30});

        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitProjectExtent() {
        this.getMap().then(mapWrapper => {
            mapWrapper.map.invalidateSize();
            this.mapUtilsService.fitMapToProject(mapWrapper, this.project);
        });
    }
}
