/* global BUILDCONFIG, L */
let assetLogo = BUILDCONFIG.LOGOFILE ?
    require(`../../../assets/images/${BUILDCONFIG.LOGOFILE}`) :
    require('../../../assets/images/raster-foundry-logo.svg');

assetLogo = BUILDCONFIG.LOGOURL || assetLogo;

export default class ShareController {
    constructor( // eslint-disable-line max-params
        $log, $state, authService, projectService, mapService, mapUtilsService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.logoAsset = assetLogo;
        this.authService = authService;
        this.projectService = projectService;
        this.mapUtilsService = mapUtilsService;
        this.getMap = () => mapService.getMap('share-map');
    }

    $onInit() {
        this.projectId = this.$state.params.projectid;
        this.testNoAuth = false;
        this.sceneList = [];

        if (this.projectId) {
            this.loadingProject = true;
            this.projectService.query({id: this.projectId}).then(
                p => {
                    this.project = p;
                    this.fitProjectExtent();
                    this.loadingProject = false;
                    this.addProjectLayer();
                },
                () => {
                    this.loadingProject = false;
                    // @TODO: handle displaying an error message
                }
            );
        }
    }

    addProjectLayer() {
        let url = this.projectService.getProjectLayerURL(
            this.project,
            {token: this.authService.token()}
        );
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
