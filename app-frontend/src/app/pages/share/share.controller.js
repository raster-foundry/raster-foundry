import logoAsset from '../../../assets/images/logo-raster-foundry.png';
/* global L */

export default class ShareController {
    constructor(
        $log, $state, authService, projectService, mapService
    ) {
        'ngInject';
        this.$log = $log;
        this.$state = $state;
        this.logoAsset = logoAsset;
        this.authService = authService;
        this.projectService = projectService;
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
                    this.loadingProject = false;
                    this.addProjectLayer();
                },
                () => {
                    this.loadingProject = false;
                    // @TODO: handle displaying an error message
                }
            );
            this.fitSceneList();
        }
    }

    addProjectLayer() {
        let url = this.projectService.getProjectLayerURL(
            this.project,
            this.authService.token()
        );
        let layer = L.tileLayer(url);

        this.getMap().then(m => {
            m.addLayer('share-layer', layer);
        });
    }

    fitSceneList() {
        this.sceneRequestState = {loading: true};
        this.projectService.getAllProjectScenes(
            {projectId: this.projectId}
        ).then(
            (allScenes) => {
                this.sceneList = allScenes;
                this.fitScenes(this.sceneList);
            },
            (error) => {
                this.sceneRequestState.errorMsg = error;
            }
        ).finally(() => {
            this.sceneRequestState.loading = false;
        });
    }

    fitScenes(scenes) {
        this.getMap().then((map) =>{
            let sceneFootprints = scenes.map((scene) => scene.dataFootprint);
            map.map.fitBounds(L.geoJSON(sceneFootprints).getBounds());
        });
    }
}
